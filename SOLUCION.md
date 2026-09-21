# Productos similares — notas de implementación

Spring Boot 4.1.1 sobre Java 25, WebFlux y `WebClient`, exponiendo
`GET /product/{productId}/similar` en el puerto 5000.

## Cómo ejecutarlo

```bash
docker compose up -d simulado influxdb grafana
mvn test
mvn -DskipTests package && java -jar target/similar-products-0.0.1-SNAPSHOT.jar
```

Y en otra terminal:

```bash
docker compose run --rm k6 run scripts/test.js
```

Reinicia la aplicación entre corridas si quieres medir con la caché en frío: sobrevive de una a otra, y la
diferencia es grande (ver más abajo).

## Diseño

```
SimilarProductsController
    └─ SimilarProductsService              presupuesto, deduplicación, fan-out paralelo, degradación
         └─ ProductCatalog  (puerto)
              ├─ CachingProductCatalog      @Primary — coalescing y stale-while-revalidate
              │    └─ StaleWhileRevalidate  política de caché
              └─ ExistingApisProductCatalog @Upstream — sólo HTTP
```

Cuatro paquetes: `domain` (el puerto y los dos records), `application` (el caso de uso),
`infrastructure.in.web` e `infrastructure.out.{http,cache}`. Sin Lombok, sin capa de DTOs y sin mapper: el
adaptador deserializa directamente al record de dominio.

La caché es un **decorador del puerto** y no código dentro del adaptador HTTP: hablar HTTP y cachear son
dos motivos de cambio distintos, y separarlos es lo que permite probar cada uno por su cuenta.

| Requisito | Mecanismo | Dónde |
|---|---|---|
| Similares en orden de similitud | `flatMapSequential`, paralelo y conservando el orden | `SimilarProductsService` |
| Una llamada por id distinto | `distinct()` | `SimilarProductsService` |
| Tiempo de respuesta acotado | `.timeout(budget)` por llamada | `SimilarProductsService` |
| Un similar que falla no tumba la respuesta | `onErrorResume`, se omite y se cuenta | `SimilarProductsService` |
| 404 sólo si falta el producto pedido | excepción de dominio y `@RestControllerAdvice` | `infrastructure.in.web` |
| N peticiones concurrentes, una llamada | `Mono.cache()` dentro de `Cache.get(key, ...)` | `StaleWhileRevalidate` |
| Sin estampida al caducar una entrada | `refreshAfterWrite` de Caffeine con `asyncReload` | `StaleWhileRevalidate` |
| No martillear un upstream que responde 404 | TTL de error corto (caché negativa) | `StaleWhileRevalidate` |
| Nada se queda colgado para siempre | timeouts de conexión y lectura del transporte | `application.properties` |

## Resultados del test de carga

Dos corridas de k6 seguidas sin reiniciar la aplicación, para separar el arranque en frío del régimen
permanente.

| | En frío | En caliente |
|---|---|---|
| `http_req_duration` mediana | 5,6 ms | **3,53 ms** |
| p90 | 152 ms | **20,6 ms** |
| p95 | 2,02 s | **33,2 ms** |
| máximo | 2,05 s | **90,7 ms** |
| throughput | 250 req/s | **334 req/s** |
| llamadas al upstream | 31 | **21** |
| fallos de caché | 14 | **0** |
| respuestas de error | 0 | **0** |

En la corrida en caliente, 20.058 peticiones produjeron **21 llamadas al upstream — una proporción de
955 : 1** — y las 21 son inevitables: unas diez son el 404 real de `/product/5`, unas diez el 500 real de
`/product/6`, y **una** es el fetch de 50 segundos de `/product/10000` terminando.

**Por qué el p95 en frío se queda clavado en el presupuesto, y por qué eso no es un defecto.** El
escenario `verySlow` dura diez segundos y `/product/10000` tarda cincuenta en responder. Su primera
petición lanza el fetch y la corrida termina mucho antes de que el valor exista, así que todas las
peticiones de ese escenario agotan su presupuesto completo. No hay caché que arregle eso dentro de una sola
corrida; el mecanismo se paga a partir de la segunda ventana, que es lo que se parece a producción. Por eso
se publican las dos columnas: la caliente sola quedaría bien, y la fría sola llevaría a la conclusión
equivocada.

El throughput sube un 33 % de una corrida a otra sin tocar la aplicación: los VUs de k6 hacen `get` y
después `sleep(0.5)`, así que responder rápido les permite completar más iteraciones. El número de
peticiones, por tanto, no es comparable entre corridas.

## Cuatro decisiones que merecen explicación

**Degradar, no fallar.** Un similar cuyo detalle no se puede recuperar se queda fuera de la respuesta; el
endpoint sigue contestando 200 con el resto. El 404 se devuelve sólo cuando es el producto *pedido* el que
no tiene lista de similares. Cada producto descartado incrementa `similar.products.discarded`, porque una
lista truncada en silencio hace que una caída del upstream sea indistinguible de un producto que
legítimamente no tiene similares.

**El presupuesto de la petición vive fuera de la caché.** Esto se descubrió midiendo, no leyendo. El
timeout estaba aplicado en el adaptador HTTP, que es *dentro* de lo que la caché memoriza, así que el
publisher cacheado era «pide el detalle, pero ríndete a los 2 s» y ningún fetch lento llegaba a
completarse. El test de carga lo delató: ninguna llamada al upstream pasó de 2,002 s y ninguna exitosa de
1,005 s, aunque dos mocks respondan en 5 s y en 50 s. Quince tests en verde no lo vieron, porque ninguno
afirmaba que un producto lento acabe apareciendo.

El presupuesto está ahora en el servicio, que es donde pertenece un timeout: cuánto estoy dispuesto a
esperar es una propiedad del llamante. Cancelar un suscriptor no cancela la fuente de un `Mono.cache()`,
así que el fetch sobrevive a la petición que se rindió y aterriza en la caché — la corrida en caliente
registra una llamada al upstream exitosa de **49,8 segundos** cuyo peticionario se había marchado 48
segundos antes.

Es por llamada y no un plazo para la respuesta entera porque `flatMapSequential` retiene los resultados
que llegan desordenados para emitirlos en orden: si el lento es el primer elemento, un plazo sobre el
`Flux` cortaría sin nada en la mano, que es lo contrario de degradar. El endpoint queda por tanto acotado
por dos presupuestos, no por uno.

**Stale-while-revalidate, no un TTL más largo.** Cuando un TTL normal vence, el siguiente en llegar no
paga solo: pagan todos los que lleguen durante la re-obtención, así que la fracción de tiempo degradado es
`F/(T+F)` — un 45 % para un fetch de 50 segundos tras un TTL de un minuto, y todavía un 7,7 % con diez
minutos. Subir el TTL diluye el problema y encima empeora la obsolescencia. Lo que hay que cambiar es
*quién espera*: el `refreshAfterWrite` de Caffeine con un `asyncReload` sirve el valor anterior al instante
y refresca por detrás del llamante. `refresh-after` está en 5 minutos porque tiene que superar al fetch más
caro, no sólo al requisito de frescura: Caffeine fecha la entrada cuando el loader devuelve, que aquí es
antes de que el valor exista, así que con una ventana más corta las entradas nacen caducadas.

Un refresco fallido conserva el valor anterior, de modo que a un upstream caído se le responde con el
último dato bueno conocido hasta el `expire-after` duro.

**No hay circuit breaker, y es una conclusión medida.** A lo largo de las corridas: cero respuestas de
error, cero excepciones en el log, 21 llamadas al upstream y ninguna petición abortada por falta de
conexión — el pool de Reactor Netty, `max(cores, 8) * 2` conexiones, no fue nunca el techo. Un breaker no
tendría nada que abrir y un bulkhead nada que aislar, porque el coalescing ya acota las llamadas
simultáneas al upstream al número de productos distintos. Y lo único que un breaker habría aportado
—seguir contestando mientras el upstream está caído— ya lo da la caché, con un resultado mejor que el
error o el fallback vacío de un breaker abierto.

## Observabilidad

Están expuestos `/actuator/health` y `/actuator/metrics` (en producción irían en un puerto de management
separado).

| Métrica | Qué responde |
|---|---|
| `similar.products.discarded` | cuántos productos decidimos dejar fuera |
| `cache.gets{result,cache}` | ratio de aciertos por caché |
| `http.client.requests{outcome,status,uri}` | llamadas al upstream, latencia y fallos; gratis por usar el `WebClient` autoconfigurado |
| `http.server.requests` | qué servimos |

El `outcome=UNKNOWN` de la métrica de cliente es el que hay que vigilar: cuenta los intercambios que
terminaron sin respuesta, y es así como se hizo visible el defecto del timeout descrito arriba.

## Configuración

| Propiedad | Valor | Por qué |
|---|---|---|
| `similar-products.budget` | `2s` | cuánto espera una petición por una llamada al upstream |
| `spring.http.clients.connect-timeout` | `1s` | conectar a un host conocido es instantáneo o está roto |
| `spring.http.clients.read-timeout` | `60s` | red de seguridad, deliberadamente por encima del upstream legítimo más lento (50 s) para que un refresco en segundo plano pueda completarse |
| `product-cache.refresh-after` | `5m` | cuándo arranca un refresco en segundo plano; tiene que superar al fetch más caro |
| `product-cache.expire-after` | `30m` | cota dura de obsolescencia y de claves sin tocar |
| `product-cache.error-ttl` | `1s` | caché negativa: suficiente para cortar el martilleo, corto para que un fallo transitorio no se quede pegado |
| `product-cache.maximum-size` | `10000` | cota de memoria; los ids son opacos, así que un mapa sin límite es una fuga |

Nota: Spring Boot 4 unificó las propiedades del cliente HTTP bajo `spring.http.clients.*`.
`spring.http.client.*` y `spring.http.reactiveclient.*` están deprecadas y no tienen efecto, en silencio.
