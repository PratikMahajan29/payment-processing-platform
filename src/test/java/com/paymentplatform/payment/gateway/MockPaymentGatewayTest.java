package com.paymentplatform.payment.gateway;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentGatewayTest {

    @Test
    void shouldSucceedImmediatelyWhenFailAttemptsIsZero() {
        MockPaymentGateway gateway = new MockPaymentGateway(0);

        PaymentAttempt attempt = attempt(1);

        PaymentGatewayResult result = gateway.process(attempt);

        assertTrue(result.successful());
        assertNotNull(result.transactionId());
        assertTrue(result.transactionId().startsWith("MOCK-"));
        assertNull(result.failureCode());
        assertNull(result.failureMessage());
    }

    @Test
    void shouldFailFirstAttemptAndSucceedOnSecondWhenFailAttemptsIsOne() {
        MockPaymentGateway gateway = new MockPaymentGateway(1);

        PaymentGatewayResult firstResult =
                gateway.process(attempt(1));

        PaymentGatewayResult secondResult =
                gateway.process(attempt(2));

        assertFalse(firstResult.successful());
        assertEquals("MOCK_FAILURE", firstResult.failureCode());
        assertEquals(
                "Mock gateway configured to fail this attempt",
                firstResult.failureMessage()
        );
        assertNull(firstResult.transactionId());

        assertTrue(secondResult.successful());
        assertNotNull(secondResult.transactionId());
        assertTrue(secondResult.transactionId().startsWith("MOCK-"));
        assertNull(secondResult.failureCode());
        assertNull(secondResult.failureMessage());
    }

    @Test
    void shouldFailFirstTwoAttemptsAndSucceedOnThirdWhenFailAttemptsIsTwo() {
        MockPaymentGateway gateway = new MockPaymentGateway(2);

        PaymentGatewayResult firstResult =
                gateway.process(attempt(1));

        PaymentGatewayResult secondResult =
                gateway.process(attempt(2));

        PaymentGatewayResult thirdResult =
                gateway.process(attempt(3));

        assertFalse(firstResult.successful());
        assertFalse(secondResult.successful());

        assertEquals("MOCK_FAILURE", firstResult.failureCode());
        assertEquals("MOCK_FAILURE", secondResult.failureCode());

        assertTrue(thirdResult.successful());
        assertNotNull(thirdResult.transactionId());
        assertTrue(thirdResult.transactionId().startsWith("MOCK-"));
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

    private PaymentAttempt attempt(int attemptNumber) {
        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setAttemptId(java.util.UUID.randomUUID());
        attempt.setPaymentId(java.util.UUID.randomUUID());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(PaymentAttemptStatus.PENDING);

        return attempt;
    }
}