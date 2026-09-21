package com.nunegal.similarproducts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public abstract class ExistingApisTestBase {

    static final WireMockServer existingApis = new WireMockServer(options().dynamicPort());

    static {
        existingApis.start();
    }

    @Autowired
    WebTestClient webTestClient;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    List<Cache<?, ?>> caches;

    @DynamicPropertySource
    static void existingApisBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", existingApis::baseUrl);
        registry.add("similar-products.budget", () -> "1s");
    }

    static void stubDetail(String id, String name, String price, int delayMillis) {
        existingApis.stubFor(get("/product/" + id).willReturn(okJson("""
                {"id":"%s","name":"%s","price":%s,"availability":true}
                """.formatted(id, name, price)).withFixedDelay(delayMillis)));
    }

    @BeforeEach
    void resetStubsAndCaches() {
        existingApis.resetAll();
        caches.forEach(Cache::invalidateAll);
    }
}
