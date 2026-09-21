package com.nunegal.similarproducts.infrastructure.out.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "existing-apis")
record ExistingApisProperties(String baseUrl) {
}
