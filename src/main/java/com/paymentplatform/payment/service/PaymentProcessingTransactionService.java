package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.InvalidPaymentStateException;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentProcessingTransactionService {

    private static final String MOCK_GATEWAY = "MOCK";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentRetryPolicy paymentRetryPolicy;

    public PaymentProcessingTransactionService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentAttemptService paymentAttemptService,
            PaymentStateMachine paymentStateMachine,
            PaymentRetryPolicy paymentRetryPolicy
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentStateMachine = paymentStateMachine;
        this.paymentRetryPolicy = paymentRetryPolicy;
    }

    @Transactional
    public PaymentAttempt prepareInitialProcessing(UUID paymentId) {

        Payment payment =
                paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment not found: " + paymentId
                                )
                        );

        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new InvalidPaymentStateException(
                    "Payment can only be initially processed from CREATED state: "
                            + paymentId
            );
        }

        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No payment attempt found for payment: "
                                                + paymentId
                                )
                        );

        if (attempt.getStatus() != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment attempt is not pending: "
                            + attempt.getAttemptId()
            );
        }

        paymentStateMachine.moveTo(
                payment,
                PaymentStatus.PENDING
        );

        payment.setUpdatedAt(
                OffsetDateTime.now()
        );

        paymentRepository.save(payment);

        return attempt;
    }

    @Transactional
    public PaymentAttempt prepareRetryProcessing(UUID paymentId) {

        Payment payment =
                paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment not found: " + paymentId
                                )
                        );

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new InvalidPaymentStateException(
                    "Payment can only be retried from FAILED state: "
                            + paymentId
            );
        }

        PaymentAttempt latestAttempt =
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No payment attempt found for payment: "
                                                + paymentId
                                )
                        );

        paymentRetryPolicy.validateCanRetry(
                latestAttempt.getAttemptNumber()
        );

        paymentStateMachine.moveTo(
                payment,
                PaymentStatus.PENDING
        );

        payment.setUpdatedAt(
                OffsetDateTime.now()
        );

        PaymentAttempt retryAttempt =
                paymentAttemptService.createAttempt(paymentId);

        paymentRepository.save(payment);

        return retryAttempt;
    }

    @Transactional
    public Payment completeGatewayAttempt(
            UUID attemptId,
            PaymentGatewayResult result
    ) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Gateway result cannot be null"
            );
        }

        PaymentAttempt attempt =
                paymentAttemptRepository.findById(attemptId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Payment attempt not found: "
                                                + attemptId
                                )
                        );

        Payment payment =
                paymentRepository.findByIdForUpdate(
                                attempt.getPaymentId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Payment not found: "
                                                + attempt.getPaymentId()
                                )
                        );

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Payment must be PENDING when completing gateway attempt: "
                            + payment.getPaymentId()
            );
        }

        if (attempt.getStatus() != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment attempt must be PENDING when completing gateway attempt: "
                            + attemptId
            );
        }

        if (result.successful()) {

            attempt.setStatus(
                    PaymentAttemptStatus.SUCCEEDED
            );

            attempt.setGateway(
                    MOCK_GATEWAY
            );

            attempt.setGatewayTransactionId(
                    result.transactionId()
            );

            attempt.setFailureCode(null);
            attempt.setFailureMessage(null);

            attempt.setCompletedAt(
                    OffsetDateTime.now()
            );

            paymentStateMachine.moveTo(
                    payment,
                    PaymentStatus.SUCCEEDED
            );

        } else {

            attempt.setStatus(
                    PaymentAttemptStatus.FAILED
            );

            attempt.setGateway(
                    MOCK_GATEWAY
            );

            attempt.setGatewayTransactionId(null);

            attempt.setFailureCode(
                    result.failureCode()
            );

            attempt.setFailureMessage(
                    result.failureMessage()
            );

            attempt.setCompletedAt(
                    OffsetDateTime.now()
            );

            paymentStateMachine.moveTo(
                    payment,
                    PaymentStatus.FAILED
            );
        }

        payment.setUpdatedAt(
                OffsetDateTime.now()
        );

        paymentAttemptRepository.save(attempt);

        return paymentRepository.save(payment);
    }
}