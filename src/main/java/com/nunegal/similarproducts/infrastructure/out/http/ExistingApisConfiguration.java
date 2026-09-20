package com.nunegal.similarproducts.infrastructure.out.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ExistingApisProperties.class)
class ExistingApisConfiguration {

    @Bean
    WebClient existingApisWebClient(WebClient.Builder builder, ExistingApisProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
