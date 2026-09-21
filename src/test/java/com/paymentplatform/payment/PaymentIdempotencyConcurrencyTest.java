package com.paymentplatform.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PaymentIdempotencyConcurrencyTest {

    private static final String BASE_URL =
            "http://localhost:8080";

    private static final String MERCHANT_ID =
            "11111111-1111-1111-1111-111111111111";

    private static final int REQUEST_COUNT = 10;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final JsonMapper objectMapper =
            JsonMapper.builder().build();

    @Test
    void shouldCreateOnlyOnePaymentForConcurrentRequests()
            throws Exception {

        String idempotencyKey =
                "concurrency-" + UUID.randomUUID();

        String requestBody = """
                {
                    "orderId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "customerId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    "amount": 49950,
                    "currency": "INR"
                }
                """;

        ExecutorService executor =
                Executors.newFixedThreadPool(REQUEST_COUNT);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<HttpResponse<String>>> futures =
                new ArrayList<>();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {

                futures.add(
                        executor.submit(() -> {

                            startLatch.await();

                            HttpRequest request =
                                    HttpRequest.newBuilder()
                                            .uri(URI.create(
                                                    BASE_URL
                                                            + "/api/v1/payments"
                                            ))
                                            .header(
                                                    "Content-Type",
                                                    "application/json"
                                            )
                                            .header(
                                                    "X-Merchant-Id",
                                                    MERCHANT_ID
                                            )
                                            .header(
                                                    "Idempotency-Key",
                                                    idempotencyKey
                                            )
                                            .POST(
                                                    HttpRequest.BodyPublishers
                                                            .ofString(
                                                                    requestBody
                                                            )
                                            )
                                            .build();

                            return httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers.ofString()
                            );
                        })
                );
            }

            // Release all workers together.
            startLatch.countDown();

            List<HttpResponse<String>> responses =
                    new ArrayList<>();

            for (Future<HttpResponse<String>> future : futures) {

                responses.add(
                        future.get(
                                15,
                                TimeUnit.SECONDS
                        )
                );
            }

            assertEquals(
                    REQUEST_COUNT,
                    responses.size()
            );

            Set<String> paymentIds =
                    new HashSet<>();

            for (HttpResponse<String> response : responses) {

                assertTrue(
                        response.statusCode() == 200
                                || response.statusCode() == 201,
                        "Unexpected response: "
                                + response.statusCode()
                                + " "
                                + response.body()
                );

                JsonNode json =
                        objectMapper.readTree(
                                response.body()
                        );

                assertNotNull(
                        json.get("paymentId"),
                        "Response did not contain paymentId"
                );

                paymentIds.add(
                        json.get("paymentId").asText()
                );
            }

            assertEquals(
                    1,
                    paymentIds.size(),
                    "All concurrent requests must return the same paymentId"
            );

        } finally {
            executor.shutdownNow();
        }
    }
}