package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.IdempotencyStatus;
import com.paymentplatform.payment.domain.model.IdempotencyRecord;
import com.paymentplatform.payment.domain.model.IdempotencyRecordId;
import com.paymentplatform.payment.exception.IdempotencyKeyConflictException;
import com.paymentplatform.payment.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private RequestHashService requestHashService;

    private IdempotencyService idempotencyService;

    private UUID merchantId;
    private UUID orderId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(
                repository,
                requestHashService
        );

        merchantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @Test
    void shouldValidateMerchantAndHashRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                orderId,
                customerId,
                new BigDecimal("499.50"),
                "INR"
        );

        when(requestHashService.hash(request))
                .thenReturn("hash-123");

        String result = idempotencyService.validateAndHash(
                merchantId,
                "key-123",
                request
        );

        assertEquals("hash-123", result);

        verify(requestHashService).hash(request);
    }

    @Test
    void shouldRejectMissingMerchantId() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                orderId,
                customerId,
                new BigDecimal("499.50"),
                "INR"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> idempotencyService.validateAndHash(
                        null,
                        "key-123",
                        request
                )
        );

        assertEquals(
                "Merchant ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(requestHashService);
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                orderId,
                customerId,
                new BigDecimal("499.50"),
                "INR"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> idempotencyService.validateAndHash(
                        merchantId,
                        null,
                        request
                )
        );

        assertEquals(
                "Idempotency key is required",
                exception.getMessage()
        );

        verifyNoInteractions(requestHashService);
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                orderId,
                customerId,
                new BigDecimal("499.50"),
                "INR"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> idempotencyService.validateAndHash(
                        merchantId,
                        "   ",
                        request
                )
        );

        assertEquals(
                "Idempotency key is required",
                exception.getMessage()
        );

        verifyNoInteractions(requestHashService);
    }

    @Test
    void shouldRejectIdempotencyKeyLongerThan100Characters() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                orderId,
                customerId,
                new BigDecimal("499.50"),
                "INR"
        );

        String key = "a".repeat(101);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> idempotencyService.validateAndHash(
                        merchantId,
                        key,
                        request
                )
        );

        assertEquals(
                "Idempotency key must not exceed 100 characters",
                exception.getMessage()
        );

        verifyNoInteractions(requestHashService);
    }

    @Test
    void shouldReturnNotFoundWhenIdempotencyRecordDoesNotExist() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        when(repository.findById(recordId))
                .thenReturn(Optional.empty());

        IdempotencyLookupResult result =
                idempotencyService.lookup(
                        merchantId,
                        idempotencyKey,
                        requestHash
                );

        assertNotNull(result);
        assertEquals(
                IdempotencyLookupState.NOT_FOUND,
                result.state()
        );
        assertNull(result.paymentId());

        verify(repository).findById(recordId);
    }

    @Test
    void shouldReturnProcessingWhenIdempotencyRecordIsProcessing() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.PROCESSING);
        record.setPaymentId(null);

        when(repository.findById(recordId))
                .thenReturn(Optional.of(record));

        IdempotencyLookupResult result =
                idempotencyService.lookup(
                        merchantId,
                        idempotencyKey,
                        requestHash
                );

        assertNotNull(result);
        assertEquals(
                IdempotencyLookupState.PROCESSING,
                result.state()
        );
        assertNull(result.paymentId());

        verify(repository).findById(recordId);
    }

    @Test
    void shouldReturnCompletedWhenIdempotencyRecordIsCompleted() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setPaymentId(paymentId);

        when(repository.findById(recordId))
                .thenReturn(Optional.of(record));

        IdempotencyLookupResult result =
                idempotencyService.lookup(
                        merchantId,
                        idempotencyKey,
                        requestHash
                );

        assertNotNull(result);
        assertEquals(
                IdempotencyLookupState.COMPLETED,
                result.state()
        );
        assertEquals(
                paymentId,
                result.paymentId()
        );

        verify(repository).findById(recordId);
    }

    @Test
    void shouldRejectCompletedRecordWithoutPaymentId() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setPaymentId(null);

        when(repository.findById(recordId))
                .thenReturn(Optional.of(record));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> idempotencyService.lookup(
                        merchantId,
                        idempotencyKey,
                        requestHash
                )
        );

        assertEquals(
                "Completed idempotency record has no payment ID",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDifferentRequestHash() {
        String idempotencyKey = "key-123";

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestHash("original-hash");
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setPaymentId(UUID.randomUUID());

        when(repository.findById(recordId))
                .thenReturn(Optional.of(record));

        IdempotencyKeyConflictException exception =
                assertThrows(
                        IdempotencyKeyConflictException.class,
                        () -> idempotencyService.lookup(
                                merchantId,
                                idempotencyKey,
                                "different-hash"
                        )
                );

        assertEquals(
                "Idempotency key has already been used for a different request",
                exception.getMessage()
        );

        verify(repository).findById(recordId);
    }

    @Test
    void shouldClaimIdempotencyKeyWhenRepositoryReturnsOne() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        when(repository.tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        )).thenReturn(1);

        boolean result = idempotencyService.tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        );

        assertTrue(result);

        verify(repository).tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        );
    }

    @Test
    void shouldNotClaimIdempotencyKeyWhenRepositoryReturnsZero() {
        String idempotencyKey = "key-123";
        String requestHash = "hash-123";

        when(repository.tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        )).thenReturn(0);

        boolean result = idempotencyService.tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        );

        assertFalse(result);

        verify(repository).tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        );
    }

    @Test
    void shouldCompleteIdempotencyRecord() {
        String idempotencyKey = "key-123";
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = new IdempotencyRecord();
        record.setRequestHash("hash-123");
        record.setStatus(IdempotencyStatus.PROCESSING);

        when(repository.findById(recordId))
                .thenReturn(Optional.of(record));

        idempotencyService.complete(
                merchantId,
                idempotencyKey,
                paymentId
        );

        assertEquals(
                paymentId,
                record.getPaymentId()
        );

        assertEquals(
                IdempotencyStatus.COMPLETED,
                record.getStatus()
        );

        verify(repository).findById(recordId);
        verify(repository).save(record);
    }

    @Test
    void shouldFailToCompleteWhenRecordDoesNotExist() {
        String idempotencyKey = "key-123";
        UUID paymentId = UUID.randomUUID();

        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        when(repository.findById(recordId))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> idempotencyService.complete(
                        merchantId,
                        idempotencyKey,
                        paymentId
                )
        );

        assertEquals(
                "Idempotency record not found for merchant: "
                        + merchantId
                        + ", key: "
                        + idempotencyKey,
                exception.getMessage()
        );

        verify(repository).findById(recordId);
        verify(repository, never()).save(any());
    }
}