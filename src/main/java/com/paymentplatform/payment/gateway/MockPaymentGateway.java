package com.paymentplatform.payment.gateway;

import com.paymentplatform.payment.domain.model.PaymentAttempt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

@Component
public class MockPaymentGateway
        implements PaymentGateway {

    private final int failAttemptsBeforeSuccess;

    private final ConcurrentMap<
            String,
            PaymentGatewayResult
            > processedResults =
            new ConcurrentHashMap<>();

    public MockPaymentGateway(
            @Value(
                    "${payment.gateway.mock.fail-attempts:0}"
            )
            int failAttemptsBeforeSuccess
    ) {

        if (failAttemptsBeforeSuccess < 0) {
            throw new IllegalArgumentException(
                    "fail-attempts cannot be negative"
            );
        }

        this.failAttemptsBeforeSuccess =
                failAttemptsBeforeSuccess;
    }

    @Override
    public PaymentGatewayResult process(
            PaymentAttempt attempt
    ) {

        if (attempt == null) {
            throw new IllegalArgumentException(
                    "Payment attempt cannot be null"
            );
        }

        String idempotencyKey =
                attempt.getGatewayIdempotencyKey();

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalStateException(
                    "Gateway idempotency key is required"
            );
        }

        return processedResults.computeIfAbsent(
                idempotencyKey,
                ignored ->
                        processNewAttempt(attempt)
        );
    }

    private PaymentGatewayResult processNewAttempt(
            PaymentAttempt attempt
    ) {

        if (attempt.getAttemptNumber()
                <= failAttemptsBeforeSuccess) {

            return PaymentGatewayResult.failure(
                    "MOCK_FAILURE",
                    "Mock gateway configured to fail this attempt"
            );
        }

        return PaymentGatewayResult.success(
                "MOCK-" + UUID.randomUUID()
        );
    }
}