package com.paymentplatform.payment.gateway;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentGatewayTest {

    @Test
    void shouldSucceedImmediatelyWhenFailAttemptsIsZero() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(0);

        PaymentAttempt attempt =
                attempt(1);

        PaymentGatewayResult result =
                gateway.process(attempt);

        assertTrue(result.successful());

        assertNotNull(
                result.transactionId()
        );

        assertTrue(
                result.transactionId()
                        .startsWith("MOCK-")
        );

        assertNull(
                result.failureCode()
        );

        assertNull(
                result.failureMessage()
        );
    }

    @Test
    void shouldFailFirstAttemptAndSucceedOnSecondWhenFailAttemptsIsOne() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(1);

        PaymentGatewayResult firstResult =
                gateway.process(
                        attempt(1)
                );

        PaymentGatewayResult secondResult =
                gateway.process(
                        attempt(2)
                );

        assertFalse(
                firstResult.successful()
        );

        assertEquals(
                "MOCK_FAILURE",
                firstResult.failureCode()
        );

        assertEquals(
                "Mock gateway configured to fail this attempt",
                firstResult.failureMessage()
        );

        assertNull(
                firstResult.transactionId()
        );

        assertTrue(
                secondResult.successful()
        );

        assertNotNull(
                secondResult.transactionId()
        );

        assertTrue(
                secondResult.transactionId()
                        .startsWith("MOCK-")
        );

        assertNull(
                secondResult.failureCode()
        );

        assertNull(
                secondResult.failureMessage()
        );
    }

    @Test
    void shouldFailFirstTwoAttemptsAndSucceedOnThirdWhenFailAttemptsIsTwo() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(2);

        PaymentGatewayResult firstResult =
                gateway.process(
                        attempt(1)
                );

        PaymentGatewayResult secondResult =
                gateway.process(
                        attempt(2)
                );

        PaymentGatewayResult thirdResult =
                gateway.process(
                        attempt(3)
                );

        assertFalse(
                firstResult.successful()
        );

        assertFalse(
                secondResult.successful()
        );

        assertEquals(
                "MOCK_FAILURE",
                firstResult.failureCode()
        );

        assertEquals(
                "MOCK_FAILURE",
                secondResult.failureCode()
        );

        assertTrue(
                thirdResult.successful()
        );

        assertNotNull(
                thirdResult.transactionId()
        );

        assertTrue(
                thirdResult.transactionId()
                        .startsWith("MOCK-")
        );
    }

    @Test
    void shouldReturnSameSuccessfulResultForRepeatedIdempotencyKey() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(0);

        PaymentAttempt firstAttempt =
                attempt(1);

        PaymentAttempt replayAttempt =
                attempt(1);

        // Both attempts represent the same logical
        // gateway operation because they use the
        // same gateway idempotency key.

        assertEquals(
                firstAttempt.getGatewayIdempotencyKey(),
                replayAttempt.getGatewayIdempotencyKey()
        );

        PaymentGatewayResult firstResult =
                gateway.process(
                        firstAttempt
                );

        PaymentGatewayResult replayResult =
                gateway.process(
                        replayAttempt
                );

        assertTrue(
                firstResult.successful()
        );

        assertTrue(
                replayResult.successful()
        );

        assertNotNull(
                firstResult.transactionId()
        );

        assertEquals(
                firstResult.transactionId(),
                replayResult.transactionId()
        );
    }

    @Test
    void shouldReturnSameFailureForRepeatedIdempotencyKey() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(1);

        PaymentAttempt firstAttempt =
                attempt(1);

        PaymentAttempt replayAttempt =
                attempt(1);

        assertEquals(
                firstAttempt.getGatewayIdempotencyKey(),
                replayAttempt.getGatewayIdempotencyKey()
        );

        PaymentGatewayResult firstResult =
                gateway.process(
                        firstAttempt
                );

        PaymentGatewayResult replayResult =
                gateway.process(
                        replayAttempt
                );

        assertFalse(
                firstResult.successful()
        );

        assertFalse(
                replayResult.successful()
        );

        assertEquals(
                firstResult.failureCode(),
                replayResult.failureCode()
        );

        assertEquals(
                firstResult.failureMessage(),
                replayResult.failureMessage()
        );

        assertNull(
                firstResult.transactionId()
        );

        assertNull(
                replayResult.transactionId()
        );
    }

    @Test
    void shouldUseDifferentGatewayResultsForDifferentIdempotencyKeys() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(1);

        PaymentAttempt firstAttempt =
                attempt(1);

        PaymentAttempt secondAttempt =
                attempt(2);

        assertNotEquals(
                firstAttempt.getGatewayIdempotencyKey(),
                secondAttempt.getGatewayIdempotencyKey()
        );

        PaymentGatewayResult firstResult =
                gateway.process(
                        firstAttempt
                );

        PaymentGatewayResult secondResult =
                gateway.process(
                        secondAttempt
                );

        assertFalse(
                firstResult.successful()
        );

        assertTrue(
                secondResult.successful()
        );

        assertNotNull(
                secondResult.transactionId()
        );

        assertTrue(
                secondResult.transactionId()
                        .startsWith("MOCK-")
        );
    }

    @Test
    void shouldRejectMissingGatewayIdempotencyKey() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(0);

        PaymentAttempt attempt =
                attempt(1);

        attempt.setGatewayIdempotencyKey(
                null
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> gateway.process(attempt)
                );

        assertEquals(
                "Gateway idempotency key is required",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullAttempt() {

        MockPaymentGateway gateway =
                new MockPaymentGateway(0);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> gateway.process(null)
                );

        assertEquals(
                "Payment attempt cannot be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNegativeFailAttempts() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new MockPaymentGateway(-1)
                );

        assertEquals(
                "fail-attempts cannot be negative",
                exception.getMessage()
        );
    }

    private PaymentAttempt attempt(
            int attemptNumber
    ) {

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setAttemptId(
                java.util.UUID.randomUUID()
        );

        attempt.setPaymentId(
                java.util.UUID.randomUUID()
        );

        attempt.setAttemptNumber(
                attemptNumber
        );

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        /*
         * For tests, use the same deterministic key
         * whenever we want to simulate replay of the
         * same gateway operation.
         *
         * attempt(1) -> same key every time
         * attempt(2) -> different key
         */
        attempt.setGatewayIdempotencyKey(
                "PAYMENT-TEST-ATTEMPT-"
                        + attemptNumber
        );

        return attempt;
    }
}