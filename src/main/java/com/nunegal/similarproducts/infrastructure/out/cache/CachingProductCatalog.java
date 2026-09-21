package com.nunegal.similarproducts.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Primary
class CachingProductCatalog implements ProductCatalog {

    private final LoadingCache<String, Mono<List<String>>> similarIdsCache;
    private final LoadingCache<String, Mono<ProductDetail>> productDetailCache;

    CachingProductCatalog(LoadingCache<String, Mono<List<String>>> similarIdsCache,
                          LoadingCache<String, Mono<ProductDetail>> productDetailCache) {
        this.similarIdsCache = similarIdsCache;
        this.productDetailCache = productDetailCache;
    }

    @Override
    public Mono<List<String>> similarIds(String productId) {
        return similarIdsCache.get(productId);
    }

    @Override
    public Mono<ProductDetail> detail(String productId) {
        return productDetailCache.get(productId);
    }
}