package com.nunegal.similarproducts.infrastructure.out.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "product-cache")
record ProductCacheProperties(@DefaultValue("1m") Duration ttl,
                              @DefaultValue("1s") Duration errorTtl,
                              @DefaultValue("10000") long maximumSize) {
}
