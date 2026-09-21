package com.paymentplatform.payment.service;

import com.paymentplatform.payment.exception.RetryLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRetryPolicyTest {

    @Test
    void shouldAllowRetryWhenCurrentAttemptIsBelowMaximum() {

        PaymentRetryPolicy policy =
                new PaymentRetryPolicy(3);

        assertDoesNotThrow(
                () -> policy.validateCanRetry(1)
        );

        assertDoesNotThrow(
                () -> policy.validateCanRetry(2)
        );
    }

    @Test
    void shouldRejectRetryWhenCurrentAttemptReachedMaximum() {

        PaymentRetryPolicy policy =
                new PaymentRetryPolicy(3);

        RetryLimitExceededException exception =
                assertThrows(
                        RetryLimitExceededException.class,
                        () -> policy.validateCanRetry(3)
                );

        assertEquals(
                "Maximum payment attempts reached: 3",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidMaximumAttempts() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new PaymentRetryPolicy(0)
                );

        assertEquals(
                "max-attempts must be at least 1",
                exception.getMessage()
        );
    }


}