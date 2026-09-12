package com.paymentplatform.payment.controller.dto;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID attemptId,
        UUID paymentId,
        Integer attemptNumber,
        PaymentAttemptStatus status,
        String gateway,
        String gatewayTransactionId,
        String failureCode,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}