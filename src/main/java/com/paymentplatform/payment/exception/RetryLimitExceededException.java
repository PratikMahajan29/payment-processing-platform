package com.paymentplatform.payment.exception;

public class RetryLimitExceededException extends RuntimeException {

    public RetryLimitExceededException(String message) {
        super(message);
    }
}