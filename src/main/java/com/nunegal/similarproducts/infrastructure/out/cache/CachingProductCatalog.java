package com.nunegal.similarproducts.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.infrastructure.out.Upstream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Primary
class CachingProductCatalog implements ProductCatalog {

    private final ProductCatalog upstream;
    private final Cache<String, Mono<List<String>>> similarIdsCache;
    private final Cache<String, Mono<ProductDetail>> productDetailCache;
    private final ProductCacheProperties properties;

    CachingProductCatalog(@Upstream ProductCatalog upstream,
                          Cache<String, Mono<List<String>>> similarIdsCache,
                          Cache<String, Mono<ProductDetail>> productDetailCache,
                          ProductCacheProperties properties) {
        this.upstream = upstream;
        this.similarIdsCache = similarIdsCache;
        this.productDetailCache = productDetailCache;
        this.properties = properties;
    }

    @Override
    public Mono<List<String>> similarIds(String productId) {
        return similarIdsCache.get(productId, id -> coalesced(upstream.similarIds(id)));
    }

    @Override
    public Mono<ProductDetail> detail(String productId) {
        return productDetailCache.get(productId, id -> coalesced(upstream.detail(id)));
    }

    private <T> Mono<T> coalesced(Mono<T> source) {
        return source.cache(value -> properties.ttl(),
                error -> properties.errorTtl(),
                properties::errorTtl);
    }
}
