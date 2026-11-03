package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

public class OutboxPublisherService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventTransport outboxEventTransport;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            OutboxEventTransport outboxEventTransport
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventTransport = outboxEventTransport;
    }

    public int publishBatch(int batchSize) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Batch size must be greater than zero"
            );
        }

        List<OutboxEvent> events =
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.NEW
                        );

        int publishedCount = 0;

        for (OutboxEvent event : events) {

            if (publishedCount >= batchSize) {
                break;
            }

            try {

                outboxEventTransport.publish(event);

                event.setStatus(
                        OutboxEventStatus.PUBLISHED
                );

                event.setPublishedAt(
                        OffsetDateTime.now()
                );

                outboxEventRepository.save(event);

                publishedCount++;

            } catch (RuntimeException exception) {

                log.error(
                        "Failed to publish outbox event: {}",
                        event.getEventId(),
                        exception
                );
            }
        }

        return publishedCount;
    }
}