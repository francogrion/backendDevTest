package com.nunegal.similarproducts.application;

import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService {

    private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);

    private final ProductCatalog catalog;
    private final Counter discardedSimilars;

    SimilarProductsService(ProductCatalog catalog, MeterRegistry meterRegistry) {
        this.catalog = catalog;
        this.discardedSimilars = Counter.builder("similar.products.discarded")
                .description("Similar products left out because their detail could not be retrieved")
                .register(meterRegistry);
    }

    public Flux<ProductDetail> similarTo(String productId) {
        return catalog.similarIds(productId)
                .distinct()
                .flatMapSequential(this::detailOrSkip);
    }

    private Mono<ProductDetail> detailOrSkip(String similarId) {
        return catalog.detail(similarId)
                .onErrorResume(error -> {
                    discardedSimilars.increment();
                    log.debug("Discarding similar product {}: {}", similarId, error.toString());
                    return Mono.empty();
                });
    }
}
