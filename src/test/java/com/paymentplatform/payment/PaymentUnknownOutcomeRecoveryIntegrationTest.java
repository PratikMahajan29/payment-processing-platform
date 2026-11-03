package com.paymentplatform.payment;

import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(PaymentUnknownOutcomeRecoveryIntegrationTest.TestGatewayConfiguration.class)
class PaymentUnknownOutcomeRecoveryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestGatewayConfiguration.TestUnknownOutcomeGateway testGateway;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final UUID merchantId =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Test
    void shouldRecoverUnknownGatewayOutcomeWithoutCreatingNewAttempt()
            throws Exception {

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String idempotencyKey =
                "unknown-outcome-" + UUID.randomUUID();

        String createBody = """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "amount": 499.50,
                  "currency": "INR"
                }
                """.formatted(
                orderId,
                customerId
        );

        // -------------------------------------------------
        // 1. CREATE PAYMENT
        // -------------------------------------------------

        HttpResponse<String> createResponse =
                postPayment(
                        createBody,
                        idempotencyKey
                );

        assertEquals(
                201,
                createResponse.statusCode()
        );

        String paymentId =
                extractPaymentId(
                        createResponse.body()
                );

        // -------------------------------------------------
        // 2. PROCESS PAYMENT
        //
        // Test gateway:
        // - generates a SUCCESS result
        // - stores that result by gateway idempotency key
        // - throws AFTER the gateway has "processed" it
        //
        // This simulates response loss.
        // -------------------------------------------------

        HttpResponse<String> processResponse =
                postWithoutBody(
                        "/api/v1/payments/"
                                + paymentId
                                + "/process"
                );

        assertTrue(
                processResponse.statusCode() >= 400,
                "Expected gateway failure response, but received: "
                        + processResponse.statusCode()
        );

        // -------------------------------------------------
        // 3. VERIFY PAYMENT IS STILL PENDING
        // -------------------------------------------------

        HttpResponse<String> pendingPaymentResponse =
                getPayment(paymentId);

        assertEquals(
                200,
                pendingPaymentResponse.statusCode()
        );

        assertTrue(
                pendingPaymentResponse.body()
                        .contains("\"status\":\"PENDING\"")
        );

        // -------------------------------------------------
        // 4. VERIFY ONLY ONE ATTEMPT EXISTS
        // -------------------------------------------------

        HttpResponse<String> pendingAttemptsResponse =
                getAttempts(paymentId);

        assertEquals(
                200,
                pendingAttemptsResponse.statusCode()
        );

        String pendingAttempts =
                pendingAttemptsResponse.body();

        assertEquals(
                1,
                countOccurrences(
                        pendingAttempts,
                        "\"attemptNumber\":1"
                )
        );

        assertTrue(
                pendingAttempts.contains(
                        "\"status\":\"PENDING\""
                )
        );

        assertFalse(
                pendingAttempts.contains(
                        "\"attemptNumber\":2"
                )
        );

        // -------------------------------------------------
        // 5. RECOVER
        //
        // Recovery must use the SAME attempt.
        // -------------------------------------------------

        HttpResponse<String> recoveryResponse =
                postWithoutBody(
                        "/api/v1/payments/"
                                + paymentId
                                + "/recover"
                );

        assertEquals(
                200,
                recoveryResponse.statusCode()
        );

        assertTrue(
                recoveryResponse.body()
                        .contains("\"status\":\"SUCCEEDED\"")
        );

        // -------------------------------------------------
        // 6. VERIFY FINAL PAYMENT
        // -------------------------------------------------

        HttpResponse<String> finalPaymentResponse =
                getPayment(paymentId);

        assertEquals(
                200,
                finalPaymentResponse.statusCode()
        );

        assertTrue(
                finalPaymentResponse.body()
                        .contains("\"status\":\"SUCCEEDED\"")
        );

        // -------------------------------------------------
        // 7. VERIFY THERE IS STILL ONLY ONE ATTEMPT
        // -------------------------------------------------

        HttpResponse<String> finalAttemptsResponse =
                getAttempts(paymentId);

        assertEquals(
                200,
                finalAttemptsResponse.statusCode()
        );

        String finalAttempts =
                finalAttemptsResponse.body();

        assertEquals(
                1,
                countOccurrences(
                        finalAttempts,
                        "\"attemptNumber\":1"
                )
        );

        assertFalse(
                finalAttempts.contains(
                        "\"attemptNumber\":2"
                )
        );

        assertTrue(
                finalAttempts.contains(
                        "\"status\":\"SUCCEEDED\""
                )
        );

        // -------------------------------------------------
        // 8. VERIFY GATEWAY WAS CALLED TWICE
        //
        // First call = actual processing
        // Second call = recovery
        //
        // But both calls must use the same idempotency key.
        // -------------------------------------------------

        assertEquals(
                2,
                testGateway.getInvocationCount()
        );

        assertEquals(
                1,
                testGateway.getUniqueGatewayKeyCount()
        );
    }

    private String extractPaymentId(
            String response
    ) {

        String marker =
                "\"paymentId\":\"";

        int start =
                response.indexOf(marker);

        assertTrue(start >= 0);

        start += marker.length();

        int end =
                response.indexOf(
                        "\"",
                        start
                );

        assertTrue(end > start);

        return response.substring(
                start,
                end
        );
    }

    private int countOccurrences(
            String text,
            String value
    ) {

        int count = 0;
        int index = 0;

        while ((index = text.indexOf(
                value,
                index
        )) >= 0) {

            count++;
            index += value.length();
        }

        return count;
    }

    private HttpResponse<String> postPayment(
            String body,
            String idempotencyKey
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl()
                                                + "/api/v1/payments"
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "X-Merchant-Id",
                                merchantId.toString()
                        )
                        .header(
                                "Idempotency-Key",
                                idempotencyKey
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> getPayment(
            String paymentId
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl()
                                                + "/api/v1/payments/"
                                                + paymentId
                                )
                        )
                        .GET()
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> getAttempts(
            String paymentId
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl()
                                                + "/api/v1/payments/"
                                                + paymentId
                                                + "/attempts"
                                )
                        )
                        .GET()
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> postWithoutBody(
            String path
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl() + path
                                )
                        )
                        .POST(
                                HttpRequest.BodyPublishers.noBody()
                        )
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @TestConfiguration
    static class TestGatewayConfiguration {

        @Bean
        @Primary
        TestUnknownOutcomeGateway testUnknownOutcomeGateway() {
            return new TestUnknownOutcomeGateway();
        }

        static class TestUnknownOutcomeGateway
                implements PaymentGateway {

            private final Map<String, PaymentGatewayResult>
                    processedResults =
                    new ConcurrentHashMap<>();

            private final AtomicBoolean
                    throwAfterFirstProcessing =
                    new AtomicBoolean(true);

            private int invocationCount;

            @Override
            public synchronized PaymentGatewayResult process(
                    PaymentAttempt attempt
            ) {

                invocationCount++;

                String key =
                        attempt.getGatewayIdempotencyKey();

                assertNotNull(key);
                assertFalse(key.isBlank());

                PaymentGatewayResult existing =
                        processedResults.get(key);

                if (existing != null) {
                    return existing;
                }

                PaymentGatewayResult result =
                        PaymentGatewayResult.success(
                                "MOCK-UNKNOWN-"
                                        + UUID.randomUUID()
                        );

                processedResults.put(
                        key,
                        result
                );

                if (throwAfterFirstProcessing.compareAndSet(
                        true,
                        false
                )) {

                    throw new RuntimeException(
                            "Simulated gateway response loss"
                    );
                }

                return result;
            }

            int getInvocationCount() {
                return invocationCount;
            }

            int getUniqueGatewayKeyCount() {
                return processedResults.size();
            }
        }
    }
}