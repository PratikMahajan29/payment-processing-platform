package com.paymentplatform.payment.gateway;

public record PaymentGatewayResult(
        boolean successful,
        String transactionId,
        String failureCode,
        String failureMessage
) {

    public static PaymentGatewayResult success(String transactionId) {
        return new PaymentGatewayResult(
                true,
                transactionId,
                null,
                null
        );
    }

    public static PaymentGatewayResult failure(
            String failureCode,
            String failureMessage
    ) {
        return new PaymentGatewayResult(
                false,
                null,
                failureCode,
                failureMessage
        );
    }
}