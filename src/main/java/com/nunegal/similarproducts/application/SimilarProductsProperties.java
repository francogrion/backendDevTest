package com.nunegal.similarproducts.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "similar-products")
record SimilarProductsProperties(@DefaultValue("2s") Duration budget) {
}
