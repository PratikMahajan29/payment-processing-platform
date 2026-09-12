package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachine {

    public void moveTo(Payment payment, PaymentStatus targetStatus) {

        PaymentStatus currentStatus = payment.getStatus();

        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                    "Invalid payment state transition: "
                            + currentStatus
                            + " -> "
                            + targetStatus
            );
        }

        payment.setStatus(targetStatus);
    }

    private boolean isValidTransition(
            PaymentStatus currentStatus,
            PaymentStatus targetStatus
    ) {
        return switch (currentStatus) {

            case CREATED ->
                    targetStatus == PaymentStatus.PENDING
                            || targetStatus == PaymentStatus.CANCELLED;

            case PENDING ->
                    targetStatus == PaymentStatus.SUCCEEDED
                            || targetStatus == PaymentStatus.FAILED
                            || targetStatus == PaymentStatus.CANCELLED;

            case FAILED ->
                    targetStatus == PaymentStatus.PENDING;

            case SUCCEEDED, CANCELLED ->
                    false;
        };
    }
}