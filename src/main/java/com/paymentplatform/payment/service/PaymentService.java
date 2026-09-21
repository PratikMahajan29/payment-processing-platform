package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.IdempotencyRequestInProgressException;
import com.paymentplatform.payment.exception.InvalidPaymentStateException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentGateway paymentGateway;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentRetryPolicy paymentRetryPolicy;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptService paymentAttemptService,
            PaymentStateMachine paymentStateMachine,
            PaymentGateway paymentGateway,
            PaymentAttemptRepository paymentAttemptRepository,
            IdempotencyService idempotencyService,
            PaymentRetryPolicy paymentRetryPolicy
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptService = paymentAttemptService;
        this.paymentStateMachine = paymentStateMachine;
        this.paymentGateway = paymentGateway;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.idempotencyService = idempotencyService;
        this.paymentRetryPolicy = paymentRetryPolicy;
    }

    @Transactional
    public Payment createPayment(
            UUID merchantId,
            UUID orderId,
            UUID customerId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        orderId,
                        customerId,
                        amount,
                        currency
                );

        String requestHash =
                idempotencyService.validateAndHash(
                        merchantId,
                        idempotencyKey,
                        request
                );

        IdempotencyLookupResult existing =
                idempotencyService.lookup(
                        merchantId,
                        idempotencyKey,
                        requestHash
                );

        if (existing.state() == IdempotencyLookupState.COMPLETED) {

            return paymentRepository.findById(existing.paymentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record points to missing payment: "
                                    + existing.paymentId()
                    ));
        }

        if (existing.state() == IdempotencyLookupState.PROCESSING) {

            throw new IdempotencyRequestInProgressException(
                    "A request with this idempotency key is already being processed"
            );
        }

        boolean claimed =
                idempotencyService.tryClaim(
                        merchantId,
                        idempotencyKey,
                        requestHash
                );

        if (!claimed) {

            IdempotencyLookupResult afterClaim =
                    idempotencyService.lookup(
                            merchantId,
                            idempotencyKey,
                            requestHash
                    );

            if (afterClaim.state()
                    == IdempotencyLookupState.COMPLETED) {

                return paymentRepository.findById(afterClaim.paymentId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record points to missing payment: "
                                        + afterClaim.paymentId()
                        ));
            }

            if (afterClaim.state()
                    == IdempotencyLookupState.PROCESSING) {

                throw new IdempotencyRequestInProgressException(
                        "A request with this idempotency key is already being processed"
                );
            }

            throw new IllegalStateException(
                    "Unable to claim idempotency key"
            );
        }

        Payment savedPayment = createNewPayment(
                merchantId,
                orderId,
                customerId,
                amount,
                currency,
                idempotencyKey
        );

        idempotencyService.complete(
                merchantId,
                idempotencyKey,
                savedPayment.getPaymentId()
        );

        return savedPayment;
    }

    private Payment createNewPayment(
            UUID merchantId,
            UUID orderId,
            UUID customerId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        Payment payment = new Payment();

        payment.setPaymentId(UUID.randomUUID());
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(currency.trim().toUpperCase());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(idempotencyKey);

        OffsetDateTime now = OffsetDateTime.now();

        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        Payment savedPayment =
                paymentRepository.save(payment);

        paymentAttemptService.createAttempt(
                savedPayment.getPaymentId()
        );

        return savedPayment;
    }

    @Transactional
    public Payment processPayment(UUID paymentId) {

        Payment payment =
                paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment not found: "
                                                + paymentId
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

        paymentStateMachine.moveTo(
                payment,
                PaymentStatus.PENDING
        );

        return processGatewayAttempt(
                payment,
                attempt
        );
    }

    @Transactional
    public Payment retryPayment(UUID paymentId) {

        Payment payment =
                paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment not found: "
                                                + paymentId
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

        PaymentAttempt retryAttempt =
                paymentAttemptService.createAttempt(paymentId);

        return processGatewayAttempt(
                payment,
                retryAttempt
        );
    }

    private Payment processGatewayAttempt(
            Payment payment,
            PaymentAttempt attempt
    ) {
        PaymentGatewayResult result =
                paymentGateway.process(attempt);

        if (result.successful()) {

            attempt.setStatus(
                    PaymentAttemptStatus.SUCCEEDED
            );

            attempt.setGateway("MOCK");

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

            attempt.setGateway("MOCK");

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

        return paymentRepository.save(payment);
    }
}