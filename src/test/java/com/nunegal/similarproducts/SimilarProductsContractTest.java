package com.nunegal.similarproducts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("GET /product/{productId}/similar")
class SimilarProductsContractTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    @DisplayName("expone el endpoint acordado y responde un array JSON")
    void exposes_the_agreed_endpoint() {
        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray();
    }
}
