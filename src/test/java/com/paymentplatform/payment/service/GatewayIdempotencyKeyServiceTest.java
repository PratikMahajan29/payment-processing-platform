package com.paymentplatform.payment.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GatewayIdempotencyKeyServiceTest {

    private final GatewayIdempotencyKeyService service =
            new GatewayIdempotencyKeyService();

    @Test
    void shouldGenerateDeterministicKey() {

        UUID paymentId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        String first =
                service.generate(
                        paymentId,
                        1
                );

        String second =
                service.generate(
                        paymentId,
                        1
                );

        assertEquals(
                first,
                second
        );

        assertEquals(
                "PAYMENT-11111111-1111-1111-1111-111111111111-ATTEMPT-1",
                first
        );
    }

    @Test
    void shouldGenerateDifferentKeyForDifferentAttempts() {

        UUID paymentId =
                UUID.randomUUID();

        String attemptOne =
                service.generate(
                        paymentId,
                        1
                );

        String attemptTwo =
                service.generate(
                        paymentId,
                        2
                );

        assertNotEquals(
                attemptOne,
                attemptTwo
        );
    }

    @Test
    void shouldGenerateDifferentKeyForDifferentPayments() {

        String first =
                service.generate(
                        UUID.randomUUID(),
                        1
                );

        String second =
                service.generate(
                        UUID.randomUUID(),
                        1
                );

        assertNotEquals(
                first,
                second
        );
    }

    @Test
    void shouldRejectNullPaymentId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(
                        null,
                        1
                )
        );
    }

    @Test
    void shouldRejectNonPositiveAttemptNumber() {

        UUID paymentId =
                UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(
                        paymentId,
                        0
                )
        );
    }
}