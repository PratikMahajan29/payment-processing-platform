package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.IdempotencyKeyConflictException;
import com.paymentplatform.payment.exception.IdempotencyRequestInProgressException;
import com.paymentplatform.payment.exception.InvalidPaymentStateException;
import com.paymentplatform.payment.exception.RetryLimitExceededException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
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

    @Spy
    private PaymentStateMachine paymentStateMachine =
            new PaymentStateMachine();

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentRetryPolicy paymentRetryPolicy;

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
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentAttemptService.createAttempt(any(UUID.class)))
                .thenReturn(new PaymentAttempt());

        Payment result = paymentService.createPayment(
                merchantId,
                orderId,
                customerId,
                49950L,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertNotNull(result.getPaymentId());

        assertEquals(merchantId, result.getMerchantId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(49950L, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(idempotencyKey, result.getIdempotencyKey());

        verify(idempotencyService).validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        );

        verify(idempotencyService).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService).tryClaim(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService).complete(
                eq(merchantId),
                eq(idempotencyKey),
                eq(result.getPaymentId())
        );

        verify(paymentRepository).save(any(Payment.class));

        verify(paymentAttemptService)
                .createAttempt(result.getPaymentId());
    }

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyIsCompleted() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        UUID existingPaymentId = UUID.randomUUID();

        Payment existingPayment = new Payment();
        existingPayment.setMerchantId(merchantId);
        existingPayment.setPaymentId(existingPaymentId);
        existingPayment.setOrderId(orderId);
        existingPayment.setCustomerId(customerId);
        existingPayment.setAmount(49950L);
        existingPayment.setCurrency("INR");
        existingPayment.setStatus(PaymentStatus.CREATED);
        existingPayment.setIdempotencyKey(idempotencyKey);

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
                IdempotencyLookupResult.completed(existingPaymentId)
        );

        when(paymentRepository.findById(existingPaymentId))
                .thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.createPayment(
                merchantId,
                orderId,
                customerId,
                49950L,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(existingPaymentId, result.getPaymentId());
        assertEquals(merchantId, result.getMerchantId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(49950L, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(idempotencyKey, result.getIdempotencyKey());

        verify(idempotencyService).validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        );

        verify(idempotencyService).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(paymentRepository).findById(existingPaymentId);

        verify(idempotencyService, never())
                .tryClaim(any(UUID.class), anyString(), anyString());

        verify(idempotencyService, never())
                .complete(any(UUID.class), anyString(), any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));
    }

    @Test
    void shouldRejectRequestWhenIdempotencyKeyIsProcessing() {
        String idempotencyKey = "key-processing";
        String requestHash = "hash-processing";

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
                        () -> paymentService.createPayment(
                                merchantId,
                                orderId,
                                customerId,
                                49950L,
                                "INR",
                                idempotencyKey
                        )
                );

        assertEquals(
                "A request with this idempotency key is already being processed",
                exception.getMessage()
        );

        verify(idempotencyService).validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        );

        verify(idempotencyService).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService, never())
                .tryClaim(any(UUID.class), anyString(), anyString());

        verify(idempotencyService, never())
                .complete(any(UUID.class), anyString(), any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));
    }

    @Test
    void shouldRejectSameMerchantWhenIdempotencyKeyIsReusedForDifferentPayload() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-new";

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

        IdempotencyKeyConflictException exception = assertThrows(
                IdempotencyKeyConflictException.class,
                () -> paymentService.createPayment(
                        merchantId,
                        orderId,
                        customerId,
                        99999L,
                        "INR",
                        idempotencyKey
                )
        );

        assertEquals(
                "Idempotency key has already been used for a different request",
                exception.getMessage()
        );

        verify(idempotencyService).validateAndHash(
                eq(merchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        );

        verify(idempotencyService).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService, never())
                .tryClaim(any(UUID.class), anyString(), anyString());

        verify(idempotencyService, never())
                .complete(any(UUID.class), anyString(), any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));
    }

    @Test
    void shouldReturnCompletedPaymentWhenAnotherRequestClaimsTheKeyFirst() {
        String idempotencyKey = "key-concurrent";
        String requestHash = "hash-concurrent";

        UUID existingPaymentId = UUID.randomUUID();

        Payment existingPayment = new Payment();
        existingPayment.setPaymentId(existingPaymentId);
        existingPayment.setMerchantId(merchantId);
        existingPayment.setOrderId(orderId);
        existingPayment.setCustomerId(customerId);
        existingPayment.setAmount(49950L);
        existingPayment.setCurrency("INR");
        existingPayment.setStatus(PaymentStatus.CREATED);
        existingPayment.setIdempotencyKey(idempotencyKey);

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
                IdempotencyLookupResult.completed(existingPaymentId)
        );

        when(idempotencyService.tryClaim(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        )).thenReturn(false);

        when(paymentRepository.findById(existingPaymentId))
                .thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.createPayment(
                merchantId,
                orderId,
                customerId,
                49950L,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(existingPaymentId, result.getPaymentId());
        assertEquals(merchantId, result.getMerchantId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(49950L, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(idempotencyKey, result.getIdempotencyKey());

        verify(idempotencyService, times(2)).lookup(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService).tryClaim(
                eq(merchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(paymentRepository).findById(existingPaymentId);

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(idempotencyService, never())
                .complete(
                        any(UUID.class),
                        anyString(),
                        any(UUID.class)
                );
    }

    @Test
    void shouldAllowSameIdempotencyKeyForDifferentMerchant() {
        UUID secondMerchantId = UUID.randomUUID();

        String idempotencyKey = "same-key";
        String requestHash = "same-hash";

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
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentAttemptService.createAttempt(any(UUID.class)))
                .thenReturn(new PaymentAttempt());

        Payment result = paymentService.createPayment(
                secondMerchantId,
                orderId,
                customerId,
                49950L,
                "INR",
                idempotencyKey
        );

        assertNotNull(result);
        assertNotNull(result.getPaymentId());

        assertEquals(secondMerchantId, result.getMerchantId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(49950L, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(idempotencyKey, result.getIdempotencyKey());

        verify(idempotencyService).validateAndHash(
                eq(secondMerchantId),
                eq(idempotencyKey),
                any(CreatePaymentRequest.class)
        );

        verify(idempotencyService).lookup(
                eq(secondMerchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService).tryClaim(
                eq(secondMerchantId),
                eq(idempotencyKey),
                eq(requestHash)
        );

        verify(idempotencyService).complete(
                eq(secondMerchantId),
                eq(idempotencyKey),
                eq(result.getPaymentId())
        );

        verify(paymentRepository).save(any(Payment.class));

        verify(paymentAttemptService)
                .createAttempt(result.getPaymentId());
    }

    // ============================================================
    // INITIAL PAYMENT PROCESSING
    // ============================================================

    @Test
    void shouldProcessCreatedPaymentSuccessfully() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt attempt = new PaymentAttempt();
        UUID attemptId = UUID.randomUUID();

        attempt.setAttemptId(attemptId);
        attempt.setPaymentId(paymentId);
        attempt.setAttemptNumber(1);
        attempt.setStatus(PaymentAttemptStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(attempt));

        when(paymentGateway.process(attempt))
                .thenReturn(
                        PaymentGatewayResult.success("MOCK-TXN-1")
                );

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result = paymentService.processPayment(paymentId);

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());

        assertEquals(
                PaymentAttemptStatus.SUCCEEDED,
                attempt.getStatus()
        );

        assertEquals(
                "MOCK-TXN-1",
                attempt.getGatewayTransactionId()
        );

        assertEquals(
                "MOCK",
                attempt.getGateway()
        );

        assertNotNull(attempt.getCompletedAt());

        verify(paymentRepository).findByIdForUpdate(paymentId);
        verify(paymentGateway).process(attempt);
        verify(paymentRepository).save(payment);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verifyNoInteractions(paymentRetryPolicy);
    }

    @Test
    void shouldFailCreatedPaymentWhenGatewayFails() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setAttemptId(UUID.randomUUID());
        attempt.setPaymentId(paymentId);
        attempt.setAttemptNumber(1);
        attempt.setStatus(PaymentAttemptStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(attempt));

        when(paymentGateway.process(attempt))
                .thenReturn(
                        PaymentGatewayResult.failure(
                                "MOCK_FAILURE",
                                "Mock gateway configured to fail this attempt"
                        )
                );

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result = paymentService.processPayment(paymentId);

        assertEquals(
                PaymentStatus.FAILED,
                result.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                attempt.getStatus()
        );

        assertEquals(
                "MOCK",
                attempt.getGateway()
        );

        assertEquals(
                "MOCK_FAILURE",
                attempt.getFailureCode()
        );

        assertEquals(
                "Mock gateway configured to fail this attempt",
                attempt.getFailureMessage()
        );

        assertNull(attempt.getGatewayTransactionId());

        assertNotNull(attempt.getCompletedAt());

        verify(paymentGateway).process(attempt);
        verify(paymentRepository).save(payment);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verifyNoInteractions(paymentRetryPolicy);
    }

    @Test
    void shouldRejectInitialProcessingWhenPaymentAlreadyFailed() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> paymentService.processPayment(paymentId)
        );

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(paymentRetryPolicy);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    // ============================================================
    // RETRY PROCESSING
    // ============================================================

    @Test
    void shouldRetryFailedPaymentUsingNewAttemptAndSucceed() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.FAILED);

        PaymentAttempt firstAttempt = new PaymentAttempt();

        firstAttempt.setAttemptId(UUID.randomUUID());
        firstAttempt.setPaymentId(paymentId);
        firstAttempt.setAttemptNumber(1);
        firstAttempt.setStatus(PaymentAttemptStatus.FAILED);
        firstAttempt.setGateway("MOCK");
        firstAttempt.setFailureCode("MOCK_FAILURE");
        firstAttempt.setFailureMessage(
                "Mock gateway configured to fail this attempt"
        );
        firstAttempt.setCompletedAt(OffsetDateTime.now());

        PaymentAttempt retryAttempt = new PaymentAttempt();

        retryAttempt.setAttemptId(UUID.randomUUID());
        retryAttempt.setPaymentId(paymentId);
        retryAttempt.setAttemptNumber(2);
        retryAttempt.setStatus(PaymentAttemptStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(firstAttempt));

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(1);

        when(paymentAttemptService.createAttempt(paymentId))
                .thenReturn(retryAttempt);

        when(paymentGateway.process(retryAttempt))
                .thenReturn(
                        PaymentGatewayResult.success("MOCK-TXN-2")
                );

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result =
                paymentService.retryPayment(paymentId);

        assertEquals(
                PaymentStatus.SUCCEEDED,
                result.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                firstAttempt.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.SUCCEEDED,
                retryAttempt.getStatus()
        );

        assertEquals(
                2,
                retryAttempt.getAttemptNumber()
        );

        assertNotEquals(
                firstAttempt.getAttemptId(),
                retryAttempt.getAttemptId()
        );

        assertEquals(
                "MOCK-TXN-2",
                retryAttempt.getGatewayTransactionId()
        );

        assertEquals(
                "MOCK",
                retryAttempt.getGateway()
        );

        assertNotNull(retryAttempt.getCompletedAt());

        verify(paymentRepository).findByIdForUpdate(paymentId);

        verify(paymentRetryPolicy)
                .validateCanRetry(1);

        verify(paymentAttemptService)
                .createAttempt(paymentId);

        verify(paymentGateway)
                .process(retryAttempt);

        verify(paymentRepository)
                .save(payment);
    }

    @Test
    void shouldRetryFailedPaymentAndFailAgain() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.FAILED);

        PaymentAttempt firstAttempt = new PaymentAttempt();

        firstAttempt.setAttemptId(UUID.randomUUID());
        firstAttempt.setPaymentId(paymentId);
        firstAttempt.setAttemptNumber(1);
        firstAttempt.setStatus(PaymentAttemptStatus.FAILED);

        PaymentAttempt secondAttempt = new PaymentAttempt();

        secondAttempt.setAttemptId(UUID.randomUUID());
        secondAttempt.setPaymentId(paymentId);
        secondAttempt.setAttemptNumber(2);
        secondAttempt.setStatus(PaymentAttemptStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(firstAttempt));

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(1);

        when(paymentAttemptService.createAttempt(paymentId))
                .thenReturn(secondAttempt);

        when(paymentGateway.process(secondAttempt))
                .thenReturn(
                        PaymentGatewayResult.failure(
                                "MOCK_FAILURE",
                                "Mock gateway configured to fail this attempt"
                        )
                );

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result =
                paymentService.retryPayment(paymentId);

        assertEquals(
                PaymentStatus.FAILED,
                result.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                firstAttempt.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                secondAttempt.getStatus()
        );

        assertEquals(
                2,
                secondAttempt.getAttemptNumber()
        );

        assertEquals(
                "MOCK",
                secondAttempt.getGateway()
        );

        assertEquals(
                "MOCK_FAILURE",
                secondAttempt.getFailureCode()
        );

        assertEquals(
                "Mock gateway configured to fail this attempt",
                secondAttempt.getFailureMessage()
        );

        assertNull(secondAttempt.getGatewayTransactionId());

        assertNotNull(secondAttempt.getCompletedAt());

        verify(paymentRetryPolicy)
                .validateCanRetry(1);

        verify(paymentAttemptService)
                .createAttempt(paymentId);

        verify(paymentGateway)
                .process(secondAttempt);

        verify(paymentRepository)
                .save(payment);
    }

    @Test
    void shouldEventuallySucceedAfterTwoFailedAttempts() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt attempt1 = new PaymentAttempt();
        attempt1.setAttemptId(UUID.randomUUID());
        attempt1.setPaymentId(paymentId);
        attempt1.setAttemptNumber(1);
        attempt1.setStatus(PaymentAttemptStatus.PENDING);

        PaymentAttempt attempt2 = new PaymentAttempt();
        attempt2.setAttemptId(UUID.randomUUID());
        attempt2.setPaymentId(paymentId);
        attempt2.setAttemptNumber(2);
        attempt2.setStatus(PaymentAttemptStatus.PENDING);

        PaymentAttempt attempt3 = new PaymentAttempt();
        attempt3.setAttemptId(UUID.randomUUID());
        attempt3.setPaymentId(paymentId);
        attempt3.setAttemptNumber(3);
        attempt3.setStatus(PaymentAttemptStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        // Call sequence:
        // 1. processPayment() -> attempt1
        // 2. retryPayment()   -> latest is still attempt1
        // 3. retryPayment()   -> latest is attempt2
        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(
                        Optional.of(attempt1),
                        Optional.of(attempt1),
                        Optional.of(attempt2)
                );

        when(paymentAttemptService.createAttempt(paymentId))
                .thenReturn(attempt2, attempt3);

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(1);

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(2);

        when(paymentGateway.process(attempt1))
                .thenReturn(
                        PaymentGatewayResult.failure(
                                "MOCK_FAILURE",
                                "Mock gateway configured to fail this attempt"
                        )
                );

        when(paymentGateway.process(attempt2))
                .thenReturn(
                        PaymentGatewayResult.failure(
                                "MOCK_FAILURE",
                                "Mock gateway configured to fail this attempt"
                        )
                );

        when(paymentGateway.process(attempt3))
                .thenReturn(
                        PaymentGatewayResult.success("MOCK-TXN-3")
                );

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        // Attempt 1: initial processing
        Payment firstResult =
                paymentService.processPayment(paymentId);

        assertEquals(
                PaymentStatus.FAILED,
                firstResult.getStatus()
        );

        // Attempt 2: explicit retry
        Payment secondResult =
                paymentService.retryPayment(paymentId);

        assertEquals(
                PaymentStatus.FAILED,
                secondResult.getStatus()
        );

        // Attempt 3: explicit retry
        Payment thirdResult =
                paymentService.retryPayment(paymentId);

        assertEquals(
                PaymentStatus.SUCCEEDED,
                thirdResult.getStatus()
        );

        // Verify final attempt states
        assertEquals(
                PaymentAttemptStatus.FAILED,
                attempt1.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                attempt2.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.SUCCEEDED,
                attempt3.getStatus()
        );

        // Verify attempt numbers
        assertEquals(1, attempt1.getAttemptNumber());
        assertEquals(2, attempt2.getAttemptNumber());
        assertEquals(3, attempt3.getAttemptNumber());

        // Verify successful gateway result
        assertEquals(
                "MOCK-TXN-3",
                attempt3.getGatewayTransactionId()
        );

        assertEquals(
                "MOCK",
                attempt3.getGateway()
        );

        assertNotNull(attempt1.getCompletedAt());
        assertNotNull(attempt2.getCompletedAt());
        assertNotNull(attempt3.getCompletedAt());

        // Verify gateway was invoked once per attempt
        verify(paymentGateway, times(3))
                .process(any(PaymentAttempt.class));

        // Verify only retries create new attempts
        verify(paymentAttemptService, times(2))
                .createAttempt(paymentId);

        // Verify retry policy was not used for initial processing
        // and was used once for each retry.
        verify(paymentRetryPolicy, times(1))
                .validateCanRetry(1);

        verify(paymentRetryPolicy, times(1))
                .validateCanRetry(2);

        // One save per processing operation
        verify(paymentRepository, times(3))
                .save(payment);
    }

    // ============================================================
    // RETRY STATE VALIDATION
    // ============================================================

    @Test
    void shouldRejectRetryWhenPaymentIsCreated() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.CREATED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> paymentService.retryPayment(paymentId)
        );

        verifyNoInteractions(paymentRetryPolicy);
        verifyNoInteractions(paymentGateway);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectRetryWhenPaymentIsSucceeded() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> paymentService.retryPayment(paymentId)
        );

        verifyNoInteractions(paymentRetryPolicy);
        verifyNoInteractions(paymentGateway);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectRetryWhenPaymentIsCancelled() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.CANCELLED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> paymentService.retryPayment(paymentId)
        );

        verifyNoInteractions(paymentRetryPolicy);
        verifyNoInteractions(paymentGateway);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectRetryWhenMaximumAttemptsReached() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);

        PaymentAttempt latestAttempt = new PaymentAttempt();
        latestAttempt.setAttemptId(UUID.randomUUID());
        latestAttempt.setPaymentId(paymentId);
        latestAttempt.setAttemptNumber(3);
        latestAttempt.setStatus(PaymentAttemptStatus.FAILED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(latestAttempt));

        doThrow(
                new RetryLimitExceededException(
                        "Maximum payment attempts reached: 3"
                )
        ).when(paymentRetryPolicy)
                .validateCanRetry(3);

        RetryLimitExceededException exception =
                assertThrows(
                        RetryLimitExceededException.class,
                        () -> paymentService.retryPayment(paymentId)
                );

        assertEquals(
                "Maximum payment attempts reached: 3",
                exception.getMessage()
        );

        verify(paymentRetryPolicy)
                .validateCanRetry(3);

        verify(paymentAttemptService, never())
                .createAttempt(any(UUID.class));

        verify(paymentGateway, never())
                .process(any(PaymentAttempt.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }
}