package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.enums.IdempotencyStatus;
import com.paymentplatform.payment.domain.model.IdempotencyRecord;
import com.paymentplatform.payment.domain.model.IdempotencyRecordId;
import com.paymentplatform.payment.exception.IdempotencyKeyConflictException;
import com.paymentplatform.payment.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final RequestHashService requestHashService;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            RequestHashService requestHashService
    ) {
        this.repository = repository;
        this.requestHashService = requestHashService;
    }

    public String validateAndHash(
            UUID merchantId,
            String idempotencyKey,
            CreatePaymentRequest request
    ) {
        validateMerchantId(merchantId);
        validateKeyFormat(idempotencyKey);

        return requestHashService.hash(request);
    }

    public IdempotencyLookupResult lookup(
            UUID merchantId,
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        return repository.findById(recordId)
                .map(existing -> {

                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyKeyConflictException(
                                "Idempotency key has already been used for a different request"
                        );
                    }

                    if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                        return IdempotencyLookupResult.processing();
                    }

                    if (existing.getStatus() == IdempotencyStatus.COMPLETED) {

                        if (existing.getPaymentId() == null) {
                            throw new IllegalStateException(
                                    "Completed idempotency record has no payment ID"
                            );
                        }

                        return IdempotencyLookupResult.completed(
                                existing.getPaymentId()
                        );
                    }

                    throw new IllegalStateException(
                            "Unsupported idempotency status: "
                                    + existing.getStatus()
                    );
                })
                .orElseGet(IdempotencyLookupResult::notFound);
    }

    public boolean tryClaim(
            UUID merchantId,
            String idempotencyKey,
            String requestHash
    ) {
        return repository.tryClaim(
                merchantId,
                idempotencyKey,
                requestHash
        ) == 1;
    }

    public void complete(
            UUID merchantId,
            String idempotencyKey,
            UUID paymentId
    ) {
        IdempotencyRecordId recordId =
                new IdempotencyRecordId(
                        merchantId,
                        idempotencyKey
                );

        IdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record not found for merchant: "
                                + merchantId
                                + ", key: "
                                + idempotencyKey
                ));

        record.setPaymentId(paymentId);
        record.setStatus(IdempotencyStatus.COMPLETED);

        repository.save(record);
    }

    private void validateMerchantId(UUID merchantId) {
        if (merchantId == null) {
            throw new IllegalArgumentException(
                    "Merchant ID is required"
            );
        }
    }

    private void validateKeyFormat(String idempotencyKey) {
        if (idempotencyKey == null ||
                idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must not exceed 100 characters"
            );
        }
    }
}