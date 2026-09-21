package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.IdempotencyRequestInProgressException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final IdempotencyService idempotencyService;
    private final MoneyService moneyService;
    private final PaymentGateway paymentGateway;
    private final PaymentProcessingTransactionService
            paymentProcessingTransactionService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptService paymentAttemptService,
            IdempotencyService idempotencyService,
            MoneyService moneyService,
            PaymentGateway paymentGateway,
            PaymentProcessingTransactionService
                    paymentProcessingTransactionService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptService = paymentAttemptService;
        this.idempotencyService = idempotencyService;
        this.moneyService = moneyService;
        this.paymentGateway = paymentGateway;
        this.paymentProcessingTransactionService =
                paymentProcessingTransactionService;
    }

    @Transactional
    public Payment createPayment(
            UUID merchantId,
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
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

        if (existing.state() ==
                IdempotencyLookupState.COMPLETED) {

            return paymentRepository.findById(
                            existing.paymentId()
                    )
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Idempotency record points to missing payment: "
                                            + existing.paymentId()
                            )
                    );
        }

        if (existing.state() ==
                IdempotencyLookupState.PROCESSING) {

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

            if (afterClaim.state() ==
                    IdempotencyLookupState.COMPLETED) {

                return paymentRepository.findById(
                                afterClaim.paymentId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Idempotency record points to missing payment: "
                                                + afterClaim.paymentId()
                                )
                        );
            }

            if (afterClaim.state() ==
                    IdempotencyLookupState.PROCESSING) {

                throw new IdempotencyRequestInProgressException(
                        "A request with this idempotency key is already being processed"
                );
            }

            throw new IllegalStateException(
                    "Unable to claim idempotency key"
            );
        }

        Payment savedPayment =
                createNewPayment(
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

    public Payment getPayment(UUID paymentId) {

        return paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );
    }

    public Payment processPayment(UUID paymentId) {

        PaymentAttempt attempt =
                paymentProcessingTransactionService
                        .prepareInitialProcessing(paymentId);

        PaymentGatewayResult result =
                paymentGateway.process(attempt);

        return paymentProcessingTransactionService
                .completeGatewayAttempt(
                        attempt.getAttemptId(),
                        result
                );
    }

    public Payment retryPayment(UUID paymentId) {

        PaymentAttempt retryAttempt =
                paymentProcessingTransactionService
                        .prepareRetryProcessing(paymentId);

        PaymentGatewayResult result =
                paymentGateway.process(retryAttempt);

        return paymentProcessingTransactionService
                .completeGatewayAttempt(
                        retryAttempt.getAttemptId(),
                        result
                );
    }

    private Payment createNewPayment(
            UUID merchantId,
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {

        long minorUnits =
                moneyService.toMinorUnits(
                        amount,
                        currency
                );

        String normalizedCurrency =
                moneyService.normalizeCurrency(currency);

        Payment payment = new Payment();

        payment.setPaymentId(
                UUID.randomUUID()
        );

        payment.setMerchantId(
                merchantId
        );

        payment.setOrderId(
                orderId
        );

        payment.setCustomerId(
                customerId
        );

        payment.setAmount(
                minorUnits
        );

        payment.setCurrency(
                normalizedCurrency
        );

        payment.setStatus(
                PaymentStatus.CREATED
        );

        payment.setIdempotencyKey(
                idempotencyKey
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        Payment savedPayment =
                paymentRepository.save(payment);

        paymentAttemptService.createAttempt(
                savedPayment.getPaymentId()
        );

        return savedPayment;
    }
}