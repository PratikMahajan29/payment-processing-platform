package com.paymentplatform.payment.controller.dto;

import com.paymentplatform.payment.domain.enums.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        Long amount,
        String currency,
        PaymentStatus status,
        String idempotencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}