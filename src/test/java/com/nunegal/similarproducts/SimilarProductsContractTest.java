package com.nunegal.similarproducts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("GET /product/{productId}/similar")
class SimilarProductsContractTest {

    private static final WireMockServer existingApis = new WireMockServer(options().dynamicPort());
    @Autowired
    WebTestClient webTestClient;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    List<Cache<?, ?>> caches;

    @BeforeAll
    static void startExistingApis() {
        existingApis.start();
    }

    @AfterAll
    static void stopExistingApis() {
        existingApis.stop();
    }

    @DynamicPropertySource
    static void existingApisBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", existingApis::baseUrl);
        registry.add("similar-products.budget", () -> "1s");
    }

    private static void stubDetail(String id, String name, String price, int delayMillis) {
        existingApis.stubFor(get("/product/" + id).willReturn(okJson("""
                {"id":"%s","name":"%s","price":%s,"availability":true}
                """.formatted(id, name, price)).withFixedDelay(delayMillis)));
    }

    @BeforeEach
    void resetStubsAndCaches() {
        existingApis.resetAll();
        caches.forEach(Cache::invalidateAll);
    }

    @Test
    @DisplayName("un producto sin similares devuelve un array vacío")
    void empty_array_when_the_product_has_no_similars() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[]")));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");
    }

    @Test
    @DisplayName("devuelve el detalle de cada producto similar")
    void returns_the_detail_of_each_similar_product() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        existingApis.stubFor(get("/product/2").willReturn(okJson("""
                {"id":"2","name":"Dress","price":19.99,"availability":true}
                """)));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        [{"id":"2","name":"Dress","price":19.99,"availability":true}]
                        """);
    }

    @Test
    @DisplayName("respeta el orden de similitud aunque los detalles lleguen desordenados")
    void keeps_the_similarity_order() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));
        stubDetail("2", "Dress", "19.99", 300);
        stubDetail("3", "Blazer", "29.99", 150);
        stubDetail("4", "Boots", "39.99", 0);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[2].id").isEqualTo("4");
    }

    @Test
    @DisplayName("consulta los detalles en paralelo, no en serie")
    void fetches_details_in_parallel() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));
        stubDetail("2", "Dress", "19.99", 400);
        stubDetail("3", "Blazer", "29.99", 400);
        stubDetail("4", "Boots", "39.99", 400);

        long startedAt = System.nanoTime();
        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed)
                .as("tres detalles de 400 ms en paralelo deben tardar ~400 ms, no ~1200 ms")
                .isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("no repite productos ni llamadas cuando un id viene duplicado")
    void deduplicates_repeated_ids() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,2,3]")));
        stubDetail("2", "Dress", "19.99", 0);
        stubDetail("3", "Blazer", "29.99", 0);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/2")));
    }

    @Test
    @DisplayName("responde 404 cuando el producto raíz no existe")
    void not_found_when_the_product_does_not_exist() {
        existingApis.stubFor(get("/product/999/similarids").willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "text/plain; charset=utf-8")
                .withBody("Not Found")));

        webTestClient.get()
                .uri("/product/{productId}/similar", "999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }

    @Test
    @DisplayName("omite el similar cuyo detalle no existe (escenario notFound de k6)")
    void skips_similars_whose_detail_is_missing() {
        existingApis.stubFor(get("/product/4/similarids").willReturn(okJson("[1,2,5]")));
        stubDetail("1", "Shirt", "9.99", 0);
        stubDetail("2", "Dress", "19.99", 0);
        existingApis.stubFor(get("/product/5").willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Product not found\"}")));

        webTestClient.get()
                .uri("/product/{productId}/similar", "4")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("1")
                .jsonPath("$[1].id").isEqualTo("2");
    }

    @Test
    @DisplayName("omite el similar cuyo detalle falla con 500 (escenario error de k6)")
    void skips_similars_whose_detail_fails() {
        existingApis.stubFor(get("/product/5/similarids").willReturn(okJson("[1,2,6]")));
        stubDetail("1", "Shirt", "9.99", 0);
        stubDetail("2", "Dress", "19.99", 0);
        existingApis.stubFor(get("/product/6").willReturn(aResponse().withStatus(500)));

        webTestClient.get()
                .uri("/product/{productId}/similar", "5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("contabiliza los similares descartados")
    void counts_discarded_similars() {
        existingApis.stubFor(get("/product/5/similarids").willReturn(okJson("[1,2,6]")));
        stubDetail("1", "Shirt", "9.99", 0);
        stubDetail("2", "Dress", "19.99", 0);
        existingApis.stubFor(get("/product/6").willReturn(aResponse().withStatus(500)));

        double before = discardedSimilars();

        webTestClient.get()
                .uri("/product/{productId}/similar", "5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        assertThat(discardedSimilars())
                .as("el similar 6 responde 500, debe quedar contabilizado")
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("descarta el similar cuyo detalle no llega a tiempo (escenario verySlow de k6)")
    void skips_similars_whose_detail_times_out() {
        existingApis.stubFor(get("/product/3/similarids").willReturn(okJson("[100,1000]")));
        stubDetail("100", "Trousers", "49.99", 0);
        stubDetail("1000", "Coat", "89.99", 1500);

        long startedAt = System.nanoTime();
        webTestClient.get()
                .uri("/product/{productId}/similar", "3")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("100");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed)
                .as("la respuesta se acota al presupuesto, no al retardo del upstream")
                .isLessThan(Duration.ofMillis(1400));
    }

    @Test
    @DisplayName("responde 500, no 404 ni 200 vacío, si la lista de similares no llega a tiempo")
    void server_error_when_similar_ids_time_out() {
        existingApis.stubFor(get("/product/1/similarids")
                .willReturn(okJson("[2]").withFixedDelay(1500)));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("no vuelve a pedir al upstream lo que ya tiene en caché")
    void serves_repeated_requests_from_the_cache() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        stubDetail("2", "Dress", "19.99", 0);

        for (int attempt = 0; attempt < 3; attempt++) {
            webTestClient.get()
                    .uri("/product/{productId}/similar", "1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].id").isEqualTo("2");
        }

        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/1/similarids")));
        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/2")));
    }

    @Test
    @DisplayName("una ráfaga concurrente en frío produce una sola llamada por recurso")
    void coalesces_a_concurrent_burst() throws Exception {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]").withFixedDelay(200)));
        stubDetail("2", "Dress", "19.99", 200);

        List<Future<?>> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                responses.add(pool.submit(() -> webTestClient.get()
                        .uri("/product/{productId}/similar", "1")
                        .exchange()
                        .expectStatus().isOk()));
            }
        }
        for (Future<?> response : responses) {
            response.get();
        }

        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/1/similarids")));
        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/2")));
    }

    @Test
    @DisplayName("un fallo del upstream no se queda pegado en la caché")
    void does_not_cache_failures_for_long() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        existingApis.stubFor(get("/product/2").willReturn(aResponse().withStatus(500)));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        stubDetail("2", "Dress", "19.99", 0);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                webTestClient.get()
                        .uri("/product/{productId}/similar", "1")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.length()").isEqualTo(1));
    }

    @Test
    @DisplayName("un detalle que llega tarde acaba en la caché y aparece en las peticiones siguientes")
    void a_late_detail_eventually_lands_in_the_cache() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3]")));
        stubDetail("2", "Dress", "19.99", 0);
        stubDetail("3", "Blazer", "29.99", 1500);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("2");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                webTestClient.get()
                        .uri("/product/{productId}/similar", "1")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.length()").isEqualTo(2)
                        .jsonPath("$[1].id").isEqualTo("3"));

        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/3")));
    }

    private double discardedSimilars() {
        return meterRegistry.get("similar.products.discarded").counter().count();
    }
}
