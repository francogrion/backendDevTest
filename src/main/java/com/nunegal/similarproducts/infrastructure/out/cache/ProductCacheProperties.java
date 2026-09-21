package com.nunegal.similarproducts.infrastructure.out.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "product-cache")
record ProductCacheProperties(@DefaultValue("30m") Duration expireAfter,
                              @DefaultValue("5m") Duration refreshAfter,
                              @DefaultValue("1s") Duration errorTtl,
                              @DefaultValue("10000") long maximumSize) {
}
