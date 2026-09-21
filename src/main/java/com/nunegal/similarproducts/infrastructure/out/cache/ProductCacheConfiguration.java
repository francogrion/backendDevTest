package com.nunegal.similarproducts.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nunegal.similarproducts.domain.ProductCatalog;
import com.nunegal.similarproducts.domain.ProductDetail;
import com.nunegal.similarproducts.infrastructure.out.Upstream;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Configuration
class ProductCacheConfiguration {

    @Bean
    LoadingCache<String, Mono<List<String>>> similarIdsCache(@Upstream ProductCatalog upstream,
                                                             ProductCacheProperties properties,
                                                             MeterRegistry meterRegistry) {
        return monitored("similar-ids", properties, meterRegistry, upstream::similarIds);
    }

    @Bean
    LoadingCache<String, Mono<ProductDetail>> productDetailCache(@Upstream ProductCatalog upstream,
                                                                 ProductCacheProperties properties,
                                                                 MeterRegistry meterRegistry) {
        return monitored("product-details", properties, meterRegistry, upstream::detail);
    }

    private <V> LoadingCache<String, Mono<V>> monitored(String name,
                                                        ProductCacheProperties properties,
                                                        MeterRegistry meterRegistry,
                                                        Function<String, Mono<V>> fetch) {
        LoadingCache<String, Mono<V>> cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.expireAfter())
                .refreshAfterWrite(properties.refreshAfter())
                .recordStats()
                .build(new StaleWhileRevalidate<>(fetch, properties.errorTtl()));
        return CaffeineCacheMetrics.monitor(meterRegistry, cache, name);
    }
}
