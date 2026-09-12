package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentAttemptService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentAttemptService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @Transactional
    public PaymentAttempt createAttempt(UUID paymentId) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Payment not found: " + paymentId)
                );

        int nextAttemptNumber = paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(payment.getPaymentId())
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(1);

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setAttemptId(UUID.randomUUID());
        attempt.setPaymentId(payment.getPaymentId());
        attempt.setAttemptNumber(nextAttemptNumber);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setCreatedAt(OffsetDateTime.now());

        return paymentAttemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> getAttempts(UUID paymentId) {
        return paymentAttemptRepository
                .findByPaymentIdOrderByAttemptNumberAsc(paymentId);
    }
}