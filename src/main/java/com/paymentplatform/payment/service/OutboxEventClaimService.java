package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxEventClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventClaimService(
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository =
                outboxEventRepository;
    }

    @Transactional
    public List<OutboxEvent> claimBatch(
            int batchSize
    ) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Batch size must be greater than zero"
            );
        }

        List<OutboxEvent> events =
                outboxEventRepository
                        .findNewForUpdateSkipLocked(
                                batchSize
                        );

        OffsetDateTime publishingStartedAt =
                OffsetDateTime.now();

        for (OutboxEvent event : events) {

            event.setStatus(
                    OutboxEventStatus.PUBLISHING
            );

            event.setPublishingStartedAt(
                    publishingStartedAt
            );
        }

        return events;
    }
}