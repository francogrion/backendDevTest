package com.nunegal.similarproducts.infrastructure.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "existing-apis")
record ExistingApisProperties(String baseUrl, @DefaultValue("2s") Duration requestTimeout) {
}
