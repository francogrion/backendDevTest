package com.nunegal.similarproducts.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductCatalog {

    Flux<String> similarIds(String productId);

    Mono<ProductDetail> detail(String productId);
}
