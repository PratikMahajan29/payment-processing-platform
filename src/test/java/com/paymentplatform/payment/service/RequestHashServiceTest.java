package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestHashServiceTest {

    private final RequestHashService requestHashService =
            new RequestHashService(new MoneyService());

    @Test
    void shouldProduceSameHashForEquivalentMoneyRepresentations() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        CreatePaymentRequest first =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR"
                );

        CreatePaymentRequest second =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.500"),
                        "INR"
                );

        assertEquals(
                requestHashService.hash(first),
                requestHashService.hash(second)
        );
    }

    @Test
    void shouldProduceDifferentHashForDifferentAmount() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        CreatePaymentRequest first =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR"
                );

        CreatePaymentRequest second =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.51"),
                        "INR"
                );

        assertNotEquals(
                requestHashService.hash(first),
                requestHashService.hash(second)
        );
    }

    @Test
    void shouldNormalizeCurrencyBeforeHashing() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        CreatePaymentRequest first =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR"
                );

        CreatePaymentRequest second =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        " inr "
                );

        assertEquals(
                requestHashService.hash(first),
                requestHashService.hash(second)
        );
    }
}