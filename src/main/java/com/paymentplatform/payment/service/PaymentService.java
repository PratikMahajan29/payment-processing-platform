package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.exception.InvalidPaymentStateException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentGateway paymentGateway;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptService paymentAttemptService,
            PaymentStateMachine paymentStateMachine,
            PaymentGateway paymentGateway,
            PaymentAttemptRepository paymentAttemptRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentStateMachine = paymentStateMachine;
        this.paymentGateway = paymentGateway;
        this.paymentAttemptRepository = paymentAttemptRepository;
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

        Payment savedPayment = paymentRepository.save(payment);

        paymentAttemptService.createAttempt(savedPayment.getPaymentId());

        return savedPayment;
    }

    @Transactional
    public Payment processPayment(UUID paymentId) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStateException(
                    "Payment has already succeeded: " + paymentId
            );
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new InvalidPaymentStateException(
                    "Payment has been cancelled: " + paymentId
            );
        }

        paymentStateMachine.moveTo(payment, PaymentStatus.PENDING);

        PaymentAttempt attempt;

        if (payment.getStatus() == PaymentStatus.FAILED) {
            attempt = paymentAttemptService.createAttempt(paymentId);
        } else {
            attempt = paymentAttemptRepository
                    .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "No payment attempt found for payment: " + paymentId
                            )
                    );
        }

        PaymentGatewayResult result = paymentGateway.process(attempt);

        if (result.successful()) {
            attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
            attempt.setGateway("MOCK");
            attempt.setGatewayTransactionId(result.transactionId());
            attempt.setCompletedAt(OffsetDateTime.now());

            paymentStateMachine.moveTo(
                    payment,
                    PaymentStatus.SUCCEEDED
            );
        } else {
            attempt.setStatus(PaymentAttemptStatus.FAILED);
            attempt.setGateway("MOCK");
            attempt.setFailureCode(result.failureCode());
            attempt.setFailureMessage(result.failureMessage());
            attempt.setCompletedAt(OffsetDateTime.now());

            paymentStateMachine.moveTo(
                    payment,
                    PaymentStatus.FAILED
            );
        }

        payment.setUpdatedAt(OffsetDateTime.now());

        return paymentRepository.save(payment);
    }
}