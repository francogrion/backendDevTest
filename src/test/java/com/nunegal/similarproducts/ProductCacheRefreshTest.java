package com.nunegal.similarproducts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "product-cache.refresh-after=300ms")
@DisplayName("Refresco de la caché")
class ProductCacheRefreshTest extends ExistingApisTestBase {

    @Test
    @DisplayName("sirve lo último conocido al instante mientras refresca por detrás")
    void serves_stale_while_revalidating() {
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        stubDetail("2", "Dress", "19.99", 0);

        webTestClient.get()
                .uri("/product/{productId}/similar", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Dress");

        existingApis.resetAll();
        existingApis.stubFor(get("/product/1/similarids").willReturn(okJson("[2]")));
        stubDetail("2", "Gown", "29.99", 1200);

        await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            long startedAt = System.nanoTime();
            webTestClient.get()
                    .uri("/product/{productId}/similar", "1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].name").isEqualTo("Dress");

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .as("el refresco no lo paga quien pregunta")
                    .isLessThan(Duration.ofMillis(400));
        });

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                webTestClient.get()
                        .uri("/product/{productId}/similar", "1")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$[0].name").isEqualTo("Gown"));
    }
}
