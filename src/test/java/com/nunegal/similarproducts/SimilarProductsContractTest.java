package com.nunegal.similarproducts;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("GET /product/{productId}/similar")
class SimilarProductsContractTest {

    private static final WireMockServer existingApis = new WireMockServer(options().dynamicPort());

    @BeforeAll
    static void startExistingApis() {
        existingApis.start();
    }

    @AfterAll
    static void stopExistingApis() {
        existingApis.stop();
    }

    @BeforeEach
    void resetStubs() {
        existingApis.resetAll();
    }

    @DynamicPropertySource
    static void existingApisBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", existingApis::baseUrl);
    }

    @Autowired
    WebTestClient webTestClient;

    @Test
    @DisplayName("un producto sin similares devuelve un array vacío")
    void empty_array_when_the_product_has_no_similars() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[]")));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");
    }

    @Test
    @DisplayName("devuelve el detalle de cada producto similar")
    void returns_the_detail_of_each_similar_product() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        existingApis.stubFor(get("/product/2").willReturn(okJson("""
                {"id":"2","name":"Dress","price":19.99,"availability":true}
                """)));

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        [{"id":"2","name":"Dress","price":19.99,"availability":true}]
                        """);
    }
}
