package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.IdempotencyKeyConflictException;
import com.paymentplatform.payment.exception.IdempotencyRequestInProgressException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptService paymentAttemptService;

    @Mock
    private IdempotencyService idempotencyService;

    @Spy
    private MoneyService moneyService =
            new MoneyService();

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentProcessingTransactionService
            paymentProcessingTransactionService;

    @InjectMocks
    private PaymentService paymentService;

    private UUID merchantId;
    private UUID orderId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    // ============================================================
    // CREATE PAYMENT / IDEMPOTENCY
    // ============================================================

    @Test
    void shouldCreatePaymentWhenIdempotencyKeyDoesNotExist() {

        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        when(idempotencyService.validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(
                IdempotencyLookupResult.notFound()
        );

        when(idempotencyService.tryClaim(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(true);

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(paymentAttemptService.createAttempt(any(UUID.class)))
                .thenReturn(new PaymentAttempt());

        Payment result =
                paymentService.createPayment(
                        merchantId,
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR",
                        idempotencyKey
                );

        assertNotNull(result);
        assertNotNull(result.getPaymentId());

        assertEquals(
                merchantId,
                result.getMerchantId()
        );

        assertEquals(
                orderId,
                result.getOrderId()
        );

        assertEquals(
                customerId,
                result.getCustomerId()
        );

        assertEquals(
                49950L,
                result.getAmount()
        );

        assertEquals(
                "INR",
                result.getCurrency()
        );

        assertEquals(
                PaymentStatus.CREATED,
                result.getStatus()
        );

        assertEquals(
                idempotencyKey,
                result.getIdempotencyKey()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(merchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(idempotencyService)
                .lookup(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(idempotencyService)
                .tryClaim(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(idempotencyService)
                .complete(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(result.getPaymentId())
                );

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(paymentAttemptService)
                .createAttempt(
                        result.getPaymentId()
                );
    }

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyIsCompleted() {

        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        UUID existingPaymentId =
                UUID.randomUUID();

        Payment existingPayment =
                new Payment();

        existingPayment.setMerchantId(
                merchantId
        );

        existingPayment.setPaymentId(
                existingPaymentId
        );

        existingPayment.setOrderId(
                orderId
        );

        existingPayment.setCustomerId(
                customerId
        );

        existingPayment.setAmount(
                49950L
        );

        existingPayment.setCurrency(
                "INR"
        );

        existingPayment.setStatus(
                PaymentStatus.CREATED
        );

        existingPayment.setIdempotencyKey(
                idempotencyKey
        );

        when(idempotencyService.validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(
                IdempotencyLookupResult.completed(
                        existingPaymentId
                )
        );

        when(paymentRepository.findById(
                existingPaymentId
        )).thenReturn(
                Optional.of(existingPayment)
        );

        Payment result =
                paymentService.createPayment(
                        merchantId,
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR",
                        idempotencyKey
                );

        assertNotNull(result);

        assertEquals(
                existingPaymentId,
                result.getPaymentId()
        );

        assertEquals(
                merchantId,
                result.getMerchantId()
        );

        assertEquals(
                orderId,
                result.getOrderId()
        );

        assertEquals(
                customerId,
                result.getCustomerId()
        );

        assertEquals(
                49950L,
                result.getAmount()
        );

        assertEquals(
                "INR",
                result.getCurrency()
        );

        assertEquals(
                PaymentStatus.CREATED,
                result.getStatus()
        );

        assertEquals(
                idempotencyKey,
                result.getIdempotencyKey()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(merchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(idempotencyService)
                .lookup(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(paymentRepository)
                .findById(existingPaymentId);

        verify(
                idempotencyService,
                never()
        ).tryClaim(
                any(UUID.class),
                anyString(),
                anyString()
        );

        verify(
                idempotencyService,
                never()
        ).complete(
                any(UUID.class),
                anyString(),
                any(UUID.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verify(
                paymentAttemptService,
                never()
        ).createAttempt(
                any(UUID.class)
        );
    }

    @Test
    void shouldRejectRequestWhenIdempotencyKeyIsProcessing() {

        String idempotencyKey =
                "key-processing";

        String requestHash =
                "hash-processing";

        when(idempotencyService.validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(
                IdempotencyLookupResult.processing()
        );

        IdempotencyRequestInProgressException exception =
                assertThrows(
                        IdempotencyRequestInProgressException.class,
                        () ->
                                paymentService.createPayment(
                                        merchantId,
                                        orderId,
                                        customerId,
                                        new BigDecimal("499.50"),
                                        "INR",
                                        idempotencyKey
                                )
                );

        assertEquals(
                "A request with this idempotency key is already being processed",
                exception.getMessage()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(merchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(idempotencyService)
                .lookup(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(
                idempotencyService,
                never()
        ).tryClaim(
                any(UUID.class),
                anyString(),
                anyString()
        );

        verify(
                idempotencyService,
                never()
        ).complete(
                any(UUID.class),
                anyString(),
                any(UUID.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verify(
                paymentAttemptService,
                never()
        ).createAttempt(
                any(UUID.class)
        );
    }

    @Test
    void shouldRejectSameMerchantWhenIdempotencyKeyIsReusedForDifferentPayload() {

        String idempotencyKey =
                "key-123";

        String requestHash =
                "hash-new";

        when(idempotencyService.validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenThrow(
                new IdempotencyKeyConflictException(
                        "Idempotency key has already been used for a different request"
                )
        );

        IdempotencyKeyConflictException exception =
                assertThrows(
                        IdempotencyKeyConflictException.class,
                        () ->
                                paymentService.createPayment(
                                        merchantId,
                                        orderId,
                                        customerId,
                                        new BigDecimal("999.99"),
                                        "INR",
                                        idempotencyKey
                                )
                );

        assertEquals(
                "Idempotency key has already been used for a different request",
                exception.getMessage()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(merchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(idempotencyService)
                .lookup(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(
                idempotencyService,
                never()
        ).tryClaim(
                any(UUID.class),
                anyString(),
                anyString()
        );

        verify(
                idempotencyService,
                never()
        ).complete(
                any(UUID.class),
                anyString(),
                any(UUID.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verify(
                paymentAttemptService,
                never()
        ).createAttempt(
                any(UUID.class)
        );
    }

    @Test
    void shouldReturnCompletedPaymentWhenAnotherRequestClaimsTheKeyFirst() {

        String idempotencyKey =
                "key-concurrent";

        String requestHash =
                "hash-concurrent";

        UUID existingPaymentId =
                UUID.randomUUID();

        Payment existingPayment =
                new Payment();

        existingPayment.setPaymentId(
                existingPaymentId
        );

        existingPayment.setMerchantId(
                merchantId
        );

        existingPayment.setOrderId(
                orderId
        );

        existingPayment.setCustomerId(
                customerId
        );

        existingPayment.setAmount(
                49950L
        );

        existingPayment.setCurrency(
                "INR"
        );

        existingPayment.setStatus(
                PaymentStatus.CREATED
        );

        existingPayment.setIdempotencyKey(
                idempotencyKey
        );

        when(idempotencyService.validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(
                IdempotencyLookupResult.notFound(),
                IdempotencyLookupResult.completed(
                        existingPaymentId
                )
        );

        when(idempotencyService.tryClaim(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(false);

        when(paymentRepository.findById(
                existingPaymentId
        )).thenReturn(
                Optional.of(existingPayment)
        );

        Payment result =
                paymentService.createPayment(
                        merchantId,
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR",
                        idempotencyKey
                );

        assertNotNull(result);

        assertEquals(
                existingPaymentId,
                result.getPaymentId()
        );

        assertEquals(
                merchantId,
                result.getMerchantId()
        );

        assertEquals(
                orderId,
                result.getOrderId()
        );

        assertEquals(
                customerId,
                result.getCustomerId()
        );

        assertEquals(
                49950L,
                result.getAmount()
        );

        assertEquals(
                "INR",
                result.getCurrency()
        );

        assertEquals(
                PaymentStatus.CREATED,
                result.getStatus()
        );

        assertEquals(
                idempotencyKey,
                result.getIdempotencyKey()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(merchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(
                idempotencyService,
                times(2)
        ).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService)
                .tryClaim(
                        eq(merchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(paymentRepository)
                .findById(existingPaymentId);

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verify(
                paymentAttemptService,
                never()
        ).createAttempt(
                any(UUID.class)
        );

        verify(
                idempotencyService,
                never()
        ).complete(
                any(UUID.class),
                anyString(),
                any(UUID.class)
        );
    }

    @Test
    void shouldAllowSameIdempotencyKeyForDifferentMerchant() {

        UUID secondMerchantId =
                UUID.randomUUID();

        String idempotencyKey =
                "same-key";

        String requestHash =
                "same-hash";

        when(idempotencyService.validateAndHash(
                eq(secondMerchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        )).thenReturn(requestHash);

        when(idempotencyService.lookup(
                eq(secondMerchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(
                IdempotencyLookupResult.notFound()
        );

        when(idempotencyService.tryClaim(
                eq(secondMerchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(true);

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(paymentAttemptService.createAttempt(any(UUID.class)))
                .thenReturn(new PaymentAttempt());

        Payment result =
                paymentService.createPayment(
                        secondMerchantId,
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR",
                        idempotencyKey
                );

        assertNotNull(result);
        assertNotNull(result.getPaymentId());

        assertEquals(
                secondMerchantId,
                result.getMerchantId()
        );

        assertEquals(
                orderId,
                result.getOrderId()
        );

        assertEquals(
                customerId,
                result.getCustomerId()
        );

        assertEquals(
                49950L,
                result.getAmount()
        );

        assertEquals(
                "INR",
                result.getCurrency()
        );

        assertEquals(
                PaymentStatus.CREATED,
                result.getStatus()
        );

        assertEquals(
                idempotencyKey,
                result.getIdempotencyKey()
        );

        verify(idempotencyService)
                .validateAndHash(
                        eq(secondMerchantId),
                        eq(idempotencyKey),
                        any(CreatePaymentRequest.class)
                );

        verify(idempotencyService)
                .lookup(
                        eq(secondMerchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(idempotencyService)
                .tryClaim(
                        eq(secondMerchantId),
                        eq(idempotencyKey),
                        eq(requestHash)
                );

        verify(idempotencyService)
                .complete(
                        eq(secondMerchantId),
                        eq(idempotencyKey),
                        eq(result.getPaymentId())
                );

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(paymentAttemptService)
                .createAttempt(
                        result.getPaymentId()
                );
    }

    // ============================================================
    // GET PAYMENT
    // ============================================================

    @Test
    void shouldReturnPaymentById() {

        UUID paymentId =
                UUID.randomUUID();

        Payment payment =
                new Payment();

        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(
                PaymentStatus.CREATED
        );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        Payment result =
                paymentService.getPayment(
                        paymentId
                );

        assertSame(
                payment,
                result
        );

        verify(paymentRepository)
                .findById(paymentId);
    }

    @Test
    void shouldRejectGetWhenPaymentDoesNotExist() {

        UUID paymentId =
                UUID.randomUUID();

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                RuntimeException.class,
                () ->
                        paymentService.getPayment(
                                paymentId
                        )
        );

        verify(paymentRepository)
                .findById(paymentId);
    }

    // ============================================================
    // INITIAL PROCESSING
    // ============================================================

    @Test
    void shouldProcessCreatedPaymentSuccessfully() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setAttemptId(
                attemptId
        );

        attempt.setPaymentId(
                paymentId
        );

        attempt.setAttemptNumber(
                1
        );

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        Payment expectedPayment =
                new Payment();

        expectedPayment.setPaymentId(
                paymentId
        );

        expectedPayment.setStatus(
                PaymentStatus.SUCCEEDED
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success(
                        "MOCK-TXN-1"
                );

        when(
                paymentProcessingTransactionService
                        .prepareInitialProcessing(
                                paymentId
                        )
        ).thenReturn(
                attempt
        );

        when(
                paymentGateway.process(
                        attempt
                )
        ).thenReturn(
                gatewayResult
        );

        when(
                paymentProcessingTransactionService
                        .completeGatewayAttempt(
                                attemptId,
                                gatewayResult
                        )
        ).thenReturn(
                expectedPayment
        );

        Payment result =
                paymentService.processPayment(
                        paymentId
                );

        assertSame(
                expectedPayment,
                result
        );

        verify(
                paymentProcessingTransactionService
        ).prepareInitialProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                attempt
        );

        verify(
                paymentProcessingTransactionService
        ).completeGatewayAttempt(
                attemptId,
                gatewayResult
        );
    }

    @Test
    void shouldReturnFailedPaymentWhenGatewayFails() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setAttemptId(
                attemptId
        );

        attempt.setPaymentId(
                paymentId
        );

        attempt.setAttemptNumber(
                1
        );

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        Payment expectedPayment =
                new Payment();

        expectedPayment.setPaymentId(
                paymentId
        );

        expectedPayment.setStatus(
                PaymentStatus.FAILED
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.failure(
                        "MOCK_FAILURE",
                        "Mock gateway configured to fail this attempt"
                );

        when(
                paymentProcessingTransactionService
                        .prepareInitialProcessing(
                                paymentId
                        )
        ).thenReturn(
                attempt
        );

        when(
                paymentGateway.process(
                        attempt
                )
        ).thenReturn(
                gatewayResult
        );

        when(
                paymentProcessingTransactionService
                        .completeGatewayAttempt(
                                attemptId,
                                gatewayResult
                        )
        ).thenReturn(
                expectedPayment
        );

        Payment result =
                paymentService.processPayment(
                        paymentId
                );

        assertSame(
                expectedPayment,
                result
        );

        assertEquals(
                PaymentStatus.FAILED,
                result.getStatus()
        );

        verify(
                paymentProcessingTransactionService
        ).prepareInitialProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                attempt
        );

        verify(
                paymentProcessingTransactionService
        ).completeGatewayAttempt(
                attemptId,
                gatewayResult
        );
    }

    @Test
    void shouldPropagateGatewayExceptionWithoutCompletingAttempt() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setAttemptId(
                attemptId
        );

        attempt.setPaymentId(
                paymentId
        );

        attempt.setAttemptNumber(
                1
        );

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        RuntimeException gatewayException =
                new RuntimeException(
                        "Gateway timeout"
                );

        when(
                paymentProcessingTransactionService
                        .prepareInitialProcessing(
                                paymentId
                        )
        ).thenReturn(
                attempt
        );

        when(
                paymentGateway.process(
                        attempt
                )
        ).thenThrow(
                gatewayException
        );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                paymentService.processPayment(
                                        paymentId
                                )
                );

        assertSame(
                gatewayException,
                exception
        );

        verify(
                paymentProcessingTransactionService
        ).prepareInitialProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                attempt
        );

        verify(
                paymentProcessingTransactionService,
                never()
        ).completeGatewayAttempt(
                any(UUID.class),
                any(PaymentGatewayResult.class)
        );
    }

    // ============================================================
    // RETRY PROCESSING
    // ============================================================

    @Test
    void shouldRetryFailedPaymentUsingNewAttemptAndSucceed() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt retryAttempt =
                new PaymentAttempt();

        retryAttempt.setAttemptId(
                attemptId
        );

        retryAttempt.setPaymentId(
                paymentId
        );

        retryAttempt.setAttemptNumber(
                2
        );

        retryAttempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        Payment expectedPayment =
                new Payment();

        expectedPayment.setPaymentId(
                paymentId
        );

        expectedPayment.setStatus(
                PaymentStatus.SUCCEEDED
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success(
                        "MOCK-TXN-2"
                );

        when(
                paymentProcessingTransactionService
                        .prepareRetryProcessing(
                                paymentId
                        )
        ).thenReturn(
                retryAttempt
        );

        when(
                paymentGateway.process(
                        retryAttempt
                )
        ).thenReturn(
                gatewayResult
        );

        when(
                paymentProcessingTransactionService
                        .completeGatewayAttempt(
                                attemptId,
                                gatewayResult
                        )
        ).thenReturn(
                expectedPayment
        );

        Payment result =
                paymentService.retryPayment(
                        paymentId
                );

        assertSame(
                expectedPayment,
                result
        );

        assertEquals(
                PaymentStatus.SUCCEEDED,
                result.getStatus()
        );

        verify(
                paymentProcessingTransactionService
        ).prepareRetryProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                retryAttempt
        );

        verify(
                paymentProcessingTransactionService
        ).completeGatewayAttempt(
                attemptId,
                gatewayResult
        );
    }

    @Test
    void shouldRetryFailedPaymentAndReturnFailedResult() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt retryAttempt =
                new PaymentAttempt();

        retryAttempt.setAttemptId(
                attemptId
        );

        retryAttempt.setPaymentId(
                paymentId
        );

        retryAttempt.setAttemptNumber(
                2
        );

        retryAttempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        Payment expectedPayment =
                new Payment();

        expectedPayment.setPaymentId(
                paymentId
        );

        expectedPayment.setStatus(
                PaymentStatus.FAILED
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.failure(
                        "MOCK_FAILURE",
                        "Mock gateway configured to fail this attempt"
                );

        when(
                paymentProcessingTransactionService
                        .prepareRetryProcessing(
                                paymentId
                        )
        ).thenReturn(
                retryAttempt
        );

        when(
                paymentGateway.process(
                        retryAttempt
                )
        ).thenReturn(
                gatewayResult
        );

        when(
                paymentProcessingTransactionService
                        .completeGatewayAttempt(
                                attemptId,
                                gatewayResult
                        )
        ).thenReturn(
                expectedPayment
        );

        Payment result =
                paymentService.retryPayment(
                        paymentId
                );

        assertSame(
                expectedPayment,
                result
        );

        assertEquals(
                PaymentStatus.FAILED,
                result.getStatus()
        );

        verify(
                paymentProcessingTransactionService
        ).prepareRetryProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                retryAttempt
        );

        verify(
                paymentProcessingTransactionService
        ).completeGatewayAttempt(
                attemptId,
                gatewayResult
        );
    }

    @Test
    void shouldPropagateGatewayExceptionDuringRetryWithoutCompletingAttempt() {

        UUID paymentId =
                UUID.randomUUID();

        UUID attemptId =
                UUID.randomUUID();

        PaymentAttempt retryAttempt =
                new PaymentAttempt();

        retryAttempt.setAttemptId(
                attemptId
        );

        retryAttempt.setPaymentId(
                paymentId
        );

        retryAttempt.setAttemptNumber(
                2
        );

        retryAttempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        RuntimeException gatewayException =
                new RuntimeException(
                        "Gateway timeout during retry"
                );

        when(
                paymentProcessingTransactionService
                        .prepareRetryProcessing(
                                paymentId
                        )
        ).thenReturn(
                retryAttempt
        );

        when(
                paymentGateway.process(
                        retryAttempt
                )
        ).thenThrow(
                gatewayException
        );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                paymentService.retryPayment(
                                        paymentId
                                )
                );

        assertSame(
                gatewayException,
                exception
        );

        verify(
                paymentProcessingTransactionService
        ).prepareRetryProcessing(
                paymentId
        );

        verify(
                paymentGateway
        ).process(
                retryAttempt
        );

        verify(
                paymentProcessingTransactionService,
                never()
        ).completeGatewayAttempt(
                any(UUID.class),
                any(PaymentGatewayResult.class)
        );
    }
}
