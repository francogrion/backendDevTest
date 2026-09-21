package com.nunegal.similarproducts.infrastructure.out.http;

import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.domain.ProductNotFoundException;
import com.nunegal.similarproducts.infrastructure.out.Upstream;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Upstream
class ExistingApisProductCatalog implements ProductCatalog {

    private static final ParameterizedTypeReference<List<String>> SIMILAR_IDS =
            new ParameterizedTypeReference<>() {
            };
    private final WebClient webClient;

    ExistingApisProductCatalog(WebClient existingApisWebClient) {
        this.webClient = existingApisWebClient;
    }

    @Override
    public Mono<List<String>> similarIds(String productId) {
        // bodyToMono(ParameterizedTypeReference<List<String>>) y no bodyToFlux(String.class):
        // con elemento String gana el StringDecoder y devuelve el cuerpo crudo ("[2]") en vez
        // de los elementos del array JSON.
        return webClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals,
                        response -> Mono.error(new ProductNotFoundException(productId)))
                .bodyToMono(SIMILAR_IDS);
    }

    @Override
    public Mono<ProductDetail> detail(String productId) {
        return webClient.get()
                .uri("/product/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductDetail.class);
    }
}
