package com.paymentplatform.payment.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        @Size(min = 3, max = 3)
        String currency

) {
}