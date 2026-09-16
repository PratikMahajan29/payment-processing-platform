package com.paymentplatform.payment.service;

public enum IdempotencyLookupState {

    NOT_FOUND,
    PROCESSING,
    COMPLETED
}