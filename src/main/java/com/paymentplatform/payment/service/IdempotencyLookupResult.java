package com.paymentplatform.payment.service;

import java.util.UUID;

public record IdempotencyLookupResult(
        IdempotencyLookupState state,
        UUID paymentId
) {

    public static IdempotencyLookupResult notFound() {
        return new IdempotencyLookupResult(
                IdempotencyLookupState.NOT_FOUND,
                null
        );
    }

    public static IdempotencyLookupResult processing() {
        return new IdempotencyLookupResult(
                IdempotencyLookupState.PROCESSING,
                null
        );
    }

    public static IdempotencyLookupResult completed(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "Completed idempotency lookup must contain a payment ID"
            );
        }

        return new IdempotencyLookupResult(
                IdempotencyLookupState.COMPLETED,
                paymentId
        );
    }
}