package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    @Test
    void shouldMoveCreatedToPending() {
        Payment payment = paymentWithStatus(PaymentStatus.CREATED);

        stateMachine.moveTo(payment, PaymentStatus.PENDING);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void shouldMovePendingToSucceeded() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);

        stateMachine.moveTo(payment, PaymentStatus.SUCCEEDED);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void shouldMovePendingToFailed() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);

        stateMachine.moveTo(payment, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void shouldAllowRetryFromFailedToPending() {
        Payment payment = paymentWithStatus(PaymentStatus.FAILED);

        stateMachine.moveTo(payment, PaymentStatus.PENDING);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void shouldRejectSucceededToFailed() {
        Payment payment = paymentWithStatus(PaymentStatus.SUCCEEDED);

        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.moveTo(payment, PaymentStatus.FAILED)
        );
    }

    @Test
    void shouldRejectCancelledToSucceeded() {
        Payment payment = paymentWithStatus(PaymentStatus.CANCELLED);

        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.moveTo(payment, PaymentStatus.SUCCEEDED)
        );
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setPaymentId(java.util.UUID.randomUUID());
        payment.setStatus(status);
        return payment;
    }
}