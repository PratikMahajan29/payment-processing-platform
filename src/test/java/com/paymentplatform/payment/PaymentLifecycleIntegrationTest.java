package com.paymentplatform.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(
        properties = "payment.gateway.mock.fail-attempts=1"
)
class PaymentLifecycleIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final UUID merchantId =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Test
    void shouldCompletePaymentLifecycleWithFailureAndRetry()
            throws Exception {

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String idempotencyKey =
                "integration-" + UUID.randomUUID();

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

        JsonNode created =
                objectMapper.readTree(
                        createResponse.body()
                );

        UUID paymentId =
                UUID.fromString(
                        created.get("paymentId").asText()
                );

        assertEquals(
                paymentId.toString(),
                created.get("paymentId").asText()
        );

        assertEquals(
                merchantId.toString(),
                created.get("merchantId").asText()
        );

        assertEquals(
                orderId.toString(),
                created.get("orderId").asText()
        );

        assertEquals(
                customerId.toString(),
                created.get("customerId").asText()
        );

        assertAmount(
                created.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                created.get("currency").asText()
        );

        assertEquals(
                "CREATED",
                created.get("status").asText()
        );

        assertEquals(
                idempotencyKey,
                created.get("idempotencyKey").asText()
        );

        // -------------------------------------------------
        // 2. GET CREATED PAYMENT
        // -------------------------------------------------

        HttpResponse<String> getCreatedResponse =
                getPayment(paymentId);

        assertEquals(
                200,
                getCreatedResponse.statusCode()
        );

        JsonNode getCreated =
                objectMapper.readTree(
                        getCreatedResponse.body()
                );

        assertEquals(
                paymentId.toString(),
                getCreated.get("paymentId").asText()
        );

        assertAmount(
                getCreated.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                getCreated.get("currency").asText()
        );

        assertEquals(
                "CREATED",
                getCreated.get("status").asText()
        );

        // -------------------------------------------------
        // 3. PROCESS PAYMENT
        //
        // fail-attempts=1
        // therefore attempt #1 must fail
        // -------------------------------------------------

        HttpResponse<String> processResponse =
                postWithoutBody(
                        "/api/v1/payments/"
                                + paymentId
                                + "/process"
                );

        assertEquals(
                200,
                processResponse.statusCode()
        );

        JsonNode processed =
                objectMapper.readTree(
                        processResponse.body()
                );

        assertEquals(
                paymentId.toString(),
                processed.get("paymentId").asText()
        );

        assertAmount(
                processed.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                processed.get("currency").asText()
        );

        assertEquals(
                "FAILED",
                processed.get("status").asText()
        );

        // -------------------------------------------------
        // 4. CHECK ATTEMPTS AFTER FAILURE
        // -------------------------------------------------

        HttpResponse<String> firstAttemptsResponse =
                getAttempts(paymentId);

        assertEquals(
                200,
                firstAttemptsResponse.statusCode()
        );

        JsonNode firstAttempts =
                objectMapper.readTree(
                        firstAttemptsResponse.body()
                );

        assertTrue(
                firstAttempts.isArray()
        );

        assertEquals(
                1,
                firstAttempts.size()
        );

        JsonNode firstAttempt =
                firstAttempts.get(0);

        assertEquals(
                1,
                firstAttempt
                        .get("attemptNumber")
                        .asInt()
        );

        assertEquals(
                paymentId.toString(),
                firstAttempt
                        .get("paymentId")
                        .asText()
        );

        assertEquals(
                "FAILED",
                firstAttempt
                        .get("status")
                        .asText()
        );

        assertEquals(
                "MOCK",
                firstAttempt
                        .get("gateway")
                        .asText()
        );

        assertEquals(
                "MOCK_FAILURE",
                firstAttempt
                        .get("failureCode")
                        .asText()
        );

        assertNotNull(
                firstAttempt
                        .get("failureMessage")
                        .asText()
        );

        assertTrue(
                firstAttempt
                        .get("gatewayTransactionId")
                        .isNull()
        );

        // -------------------------------------------------
        // 5. RETRY PAYMENT
        //
        // attempt #2 must succeed
        // -------------------------------------------------

        HttpResponse<String> retryResponse =
                postWithoutBody(
                        "/api/v1/payments/"
                                + paymentId
                                + "/retry"
                );

        assertEquals(
                200,
                retryResponse.statusCode()
        );

        JsonNode retried =
                objectMapper.readTree(
                        retryResponse.body()
                );

        assertEquals(
                paymentId.toString(),
                retried.get("paymentId").asText()
        );

        assertAmount(
                retried.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                retried.get("currency").asText()
        );

        assertEquals(
                "SUCCEEDED",
                retried.get("status").asText()
        );

        // -------------------------------------------------
        // 6. GET FINAL PAYMENT
        // -------------------------------------------------

        HttpResponse<String> getFinalResponse =
                getPayment(paymentId);

        assertEquals(
                200,
                getFinalResponse.statusCode()
        );

        JsonNode finalPayment =
                objectMapper.readTree(
                        getFinalResponse.body()
                );

        assertEquals(
                paymentId.toString(),
                finalPayment.get("paymentId").asText()
        );

        assertAmount(
                finalPayment.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                finalPayment.get("currency").asText()
        );

        assertEquals(
                "SUCCEEDED",
                finalPayment.get("status").asText()
        );

        // -------------------------------------------------
        // 7. VERIFY COMPLETE ATTEMPT HISTORY
        // -------------------------------------------------

        HttpResponse<String> finalAttemptsResponse =
                getAttempts(paymentId);

        assertEquals(
                200,
                finalAttemptsResponse.statusCode()
        );

        JsonNode finalAttempts =
                objectMapper.readTree(
                        finalAttemptsResponse.body()
                );

        assertTrue(
                finalAttempts.isArray()
        );

        assertEquals(
                2,
                finalAttempts.size()
        );

        // Attempt #1

        JsonNode attemptOne =
                finalAttempts.get(0);

        assertEquals(
                1,
                attemptOne
                        .get("attemptNumber")
                        .asInt()
        );

        assertEquals(
                "FAILED",
                attemptOne
                        .get("status")
                        .asText()
        );

        assertEquals(
                "MOCK_FAILURE",
                attemptOne
                        .get("failureCode")
                        .asText()
        );

        assertTrue(
                attemptOne
                        .get("gatewayTransactionId")
                        .isNull()
        );

        // Attempt #2

        JsonNode attemptTwo =
                finalAttempts.get(1);

        assertEquals(
                2,
                attemptTwo
                        .get("attemptNumber")
                        .asInt()
        );

        assertEquals(
                "SUCCEEDED",
                attemptTwo
                        .get("status")
                        .asText()
        );

        assertEquals(
                "MOCK",
                attemptTwo
                        .get("gateway")
                        .asText()
        );

        assertTrue(
                attemptTwo
                        .get("gatewayTransactionId")
                        .asText()
                        .startsWith("MOCK-")
        );

        assertTrue(
                attemptTwo
                        .get("failureCode")
                        .isNull()
        );

        assertTrue(
                attemptTwo
                        .get("failureMessage")
                        .isNull()
        );

        // -------------------------------------------------
        // 8. IDEMPOTENCY
        //
        // Same key + same payload
        // must return the same payment
        // -------------------------------------------------

        HttpResponse<String> duplicateCreateResponse =
                postPayment(
                        createBody,
                        idempotencyKey
                );

        assertEquals(
                201,
                duplicateCreateResponse.statusCode()
        );

        JsonNode duplicate =
                objectMapper.readTree(
                        duplicateCreateResponse.body()
                );

        assertEquals(
                paymentId.toString(),
                duplicate.get("paymentId").asText()
        );

        assertAmount(
                duplicate.get("amount"),
                "499.50"
        );

        assertEquals(
                "INR",
                duplicate.get("currency").asText()
        );

        assertEquals(
                "SUCCEEDED",
                duplicate.get("status").asText()
        );

        // -------------------------------------------------
        // 9. IDEMPOTENCY CONFLICT
        //
        // Same merchant + same key
        // but different payload
        // -------------------------------------------------

        String conflictingBody = """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "amount": 500.00,
                  "currency": "INR"
                }
                """.formatted(
                orderId,
                customerId
        );

        HttpResponse<String> conflictResponse =
                postPayment(
                        conflictingBody,
                        idempotencyKey
                );

        assertEquals(
                409,
                conflictResponse.statusCode()
        );

        JsonNode conflict =
                objectMapper.readTree(
                        conflictResponse.body()
                );

        assertEquals(
                "IDEMPOTENCY_KEY_CONFLICT",
                conflict.get("code").asText()
        );

        assertTrue(
                conflict.has("message")
        );

        assertTrue(
                conflict.has("timestamp")
        );
    }

    private void assertAmount(
            JsonNode amountNode,
            String expected
    ) {
        assertNotNull(amountNode);

        BigDecimal actual =
                new BigDecimal(
                        amountNode.asText()
                );

        BigDecimal expectedAmount =
                new BigDecimal(expected);

        assertEquals(
                0,
                actual.compareTo(expectedAmount),
                "Unexpected payment amount"
        );
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
            UUID paymentId
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
            UUID paymentId
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
                                HttpRequest.BodyPublishers
                                        .noBody()
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
}