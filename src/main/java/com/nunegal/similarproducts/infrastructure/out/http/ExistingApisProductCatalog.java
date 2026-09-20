package com.nunegal.similarproducts.infrastructure.out.http;

import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
class ExistingApisProductCatalog implements ProductCatalog {

    private static final ParameterizedTypeReference<List<String>> SIMILAR_IDS =
            new ParameterizedTypeReference<>() {
            };
    private final WebClient webClient;

    ExistingApisProductCatalog(WebClient existingApisWebClient) {
        this.webClient = existingApisWebClient;
    }

    @Override
    public Flux<String> similarIds(String productId) {
        // bodyToFlux(String.class) NO sirve aquí: con elemento String gana el StringDecoder
        // y devuelve el cuerpo crudo ("[2]") en vez de los elementos del array JSON.
        return webClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                        response -> Mono.error(new ProductNotFoundException(productId)))
                .bodyToMono(SIMILAR_IDS)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<ProductDetail> detail(String productId) {
        return webClient.get()
                .uri("/product/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductDetail.class);
    }
}
