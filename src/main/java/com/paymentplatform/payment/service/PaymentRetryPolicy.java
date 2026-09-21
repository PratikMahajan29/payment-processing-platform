package com.paymentplatform.payment.service;

import com.paymentplatform.payment.exception.RetryLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentRetryPolicy {

    private final int maxAttempts;

    public PaymentRetryPolicy(
            @Value("${payment.retry.max-attempts:3}")
            int maxAttempts
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "max-attempts must be at least 1"
            );
        }

        this.maxAttempts = maxAttempts;
    }

    public void validateCanRetry(int currentAttemptNumber) {
        if (currentAttemptNumber >= maxAttempts) {
            throw new RetryLimitExceededException(
                    "Maximum payment attempts reached: "
                            + maxAttempts
            );
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}