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

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("GET /product/{productId}/similar")
class SimilarProductsContractTest {

    private static final WireMockServer existingApis = new WireMockServer(options().dynamicPort());
    @Autowired
    WebTestClient webTestClient;

    @BeforeAll
    static void startExistingApis() {
        existingApis.start();
    }

    @AfterAll
    static void stopExistingApis() {
        existingApis.stop();
    }

    @DynamicPropertySource
    static void existingApisBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", existingApis::baseUrl);
    }

    private static void stubDetail(String id, String name, String price, int delayMillis) {
        existingApis.stubFor(get("/product/" + id).willReturn(okJson("""
                {"id":"%s","name":"%s","price":%s,"availability":true}
                """.formatted(id, name, price)).withFixedDelay(delayMillis)));
    }

    @BeforeEach
    void resetStubs() {
        existingApis.resetAll();
    }

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

    @Test
    @DisplayName("respeta el orden de similitud aunque los detalles lleguen desordenados")
    void keeps_the_similarity_order() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));
        stubDetail("2", "Dress", "19.99", 300);
        stubDetail("3", "Blazer", "29.99", 150);
        stubDetail("4", "Boots", "39.99", 0);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[2].id").isEqualTo("4");
    }

    @Test
    @DisplayName("consulta los detalles en paralelo, no en serie")
    void fetches_details_in_parallel() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,3,4]")));
        stubDetail("2", "Dress", "19.99", 400);
        stubDetail("3", "Blazer", "29.99", 400);
        stubDetail("4", "Boots", "39.99", 400);

        long startedAt = System.nanoTime();
        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed)
                .as("tres detalles de 400 ms en paralelo deben tardar ~400 ms, no ~1200 ms")
                .isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("no repite productos ni llamadas cuando un id viene duplicado")
    void deduplicates_repeated_ids() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2,2,3]")));
        stubDetail("2", "Dress", "19.99", 0);
        stubDetail("3", "Blazer", "29.99", 0);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        existingApis.verify(exactly(1), getRequestedFor(urlEqualTo("/product/2")));
    }
}
