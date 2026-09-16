package com.paymentplatform.payment.exception;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException(String message) {
        super(message);
    }
}