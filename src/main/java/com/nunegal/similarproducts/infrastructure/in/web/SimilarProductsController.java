package com.nunegal.similarproducts.infrastructure.in.web;

import com.nunegal.similarproducts.application.SimilarProductsService;
import com.nunegal.similarproducts.domain.ProductDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
class SimilarProductsController {

    private final SimilarProductsService similarProducts;

    SimilarProductsController(SimilarProductsService similarProducts) {
        this.similarProducts = similarProducts;
    }

    @GetMapping("/product/{productId}/similar")
    Flux<ProductDetail> similar(@PathVariable String productId) {
        return similarProducts.similarTo(productId);
    }
}