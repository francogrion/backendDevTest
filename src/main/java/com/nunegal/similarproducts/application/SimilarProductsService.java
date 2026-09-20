package com.nunegal.similarproducts.application;

import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SimilarProductsService {

    private final ProductCatalog catalog;

    SimilarProductsService(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    public Flux<ProductDetail> similarTo(String productId) {
        return catalog.similarIds(productId)
                .distinct()
                .flatMapSequential(catalog::detail);
    }
}
