package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.IdempotencyKeyConflictException;
import com.paymentplatform.payment.exception.IdempotencyRequestInProgressException;
import com.paymentplatform.payment.gateway.PaymentGateway;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private PaymentStateMachine paymentStateMachine;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private IdempotencyService idempotencyService;

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
}