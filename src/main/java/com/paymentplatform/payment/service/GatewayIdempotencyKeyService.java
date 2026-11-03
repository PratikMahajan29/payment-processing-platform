package com.paymentplatform.payment.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GatewayIdempotencyKeyService {

    public String generate(
            UUID paymentId,
            int attemptNumber
    ) {

        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "Payment ID cannot be null"
            );
        }

        if (attemptNumber <= 0) {
            throw new IllegalArgumentException(
                    "Attempt number must be greater than zero"
            );
        }

        return "PAYMENT-"
                + paymentId
                + "-ATTEMPT-"
                + attemptNumber;
    }
}