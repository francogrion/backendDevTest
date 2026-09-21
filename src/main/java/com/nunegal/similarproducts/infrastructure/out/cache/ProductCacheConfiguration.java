package com.nunegal.similarproducts.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nunegal.similarproducts.domain.ProductDetail;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableConfigurationProperties(ProductCacheProperties.class)
class ProductCacheConfiguration {

    @Bean
    Cache<String, Mono<List<String>>> similarIdsCache(ProductCacheProperties properties,
                                                      MeterRegistry meterRegistry) {
        return monitored("similar-ids", properties, meterRegistry);
    }

    @Bean
    Cache<String, Mono<ProductDetail>> productDetailCache(ProductCacheProperties properties,
                                                          MeterRegistry meterRegistry) {
        return monitored("product-details", properties, meterRegistry);
    }

    private <V> Cache<String, V> monitored(String name,
                                           ProductCacheProperties properties,
                                           MeterRegistry meterRegistry) {
        Cache<String, V> cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .recordStats()
                .build();
        return CaffeineCacheMetrics.monitor(meterRegistry, cache, name);
    }
}
