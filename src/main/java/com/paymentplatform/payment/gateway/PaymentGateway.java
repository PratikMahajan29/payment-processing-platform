package com.paymentplatform.payment.gateway;

import com.paymentplatform.payment.domain.model.PaymentAttempt;

public interface PaymentGateway {

    PaymentGatewayResult process(PaymentAttempt attempt);
}