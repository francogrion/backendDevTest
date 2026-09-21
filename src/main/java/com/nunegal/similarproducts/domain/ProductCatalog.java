package com.nunegal.similarproducts.domain;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductCatalog {

    Mono<List<String>> similarIds(String productId);

    Mono<ProductDetail> detail(String productId);
}
