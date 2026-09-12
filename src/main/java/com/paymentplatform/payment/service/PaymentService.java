package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createPayment(
            UUID orderId,
            UUID customerId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> createNewPayment(
                        orderId,
                        customerId,
                        amount,
                        currency,
                        idempotencyKey
                ));
    }

    private Payment createNewPayment(
            UUID orderId,
            UUID customerId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        Payment payment = new Payment();

        payment.setPaymentId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(idempotencyKey);

        OffsetDateTime now = OffsetDateTime.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        return paymentRepository.save(payment);
    }
}