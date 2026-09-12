package com.paymentplatform.payment.gateway;

import com.paymentplatform.payment.domain.model.PaymentAttempt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResult process(PaymentAttempt attempt) {

        return PaymentGatewayResult.success(
                "MOCK-" + UUID.randomUUID()
        );
    }
}