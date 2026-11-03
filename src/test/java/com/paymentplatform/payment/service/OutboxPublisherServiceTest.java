package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventTransport outboxEventTransport;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {

        outboxPublisherService =
                new OutboxPublisherService(
                        outboxEventRepository,
                        outboxEventTransport
                );
    }

    @Test
    void shouldPublishNewEventsAndMarkThemPublished() {

        OutboxEvent firstEvent =
                createEvent();

        OutboxEvent secondEvent =
                createEvent();

        when(
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.NEW
                        )
        )
                .thenReturn(
                        List.of(
                                firstEvent,
                                secondEvent
                        )
                );

        int publishedCount =
                outboxPublisherService.publishBatch(10);

        assertEquals(
                2,
                publishedCount
        );

        verify(
                outboxEventTransport
        )
                .publish(firstEvent);

        verify(
                outboxEventTransport
        )
                .publish(secondEvent);

        assertEquals(
                OutboxEventStatus.PUBLISHED,
                firstEvent.getStatus()
        );

        assertEquals(
                OutboxEventStatus.PUBLISHED,
                secondEvent.getStatus()
        );

        assertNotNull(
                firstEvent.getPublishedAt()
        );

        assertNotNull(
                secondEvent.getPublishedAt()
        );

        verify(
                outboxEventRepository
        )
                .save(firstEvent);

        verify(
                outboxEventRepository
        )
                .save(secondEvent);
    }

    @Test
    void shouldRespectBatchSize() {

        OutboxEvent firstEvent =
                createEvent();

        OutboxEvent secondEvent =
                createEvent();

        OutboxEvent thirdEvent =
                createEvent();

        when(
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.NEW
                        )
        )
                .thenReturn(
                        List.of(
                                firstEvent,
                                secondEvent,
                                thirdEvent
                        )
                );

        int publishedCount =
                outboxPublisherService.publishBatch(2);

        assertEquals(
                2,
                publishedCount
        );

        verify(
                outboxEventTransport
        )
                .publish(firstEvent);

        verify(
                outboxEventTransport
        )
                .publish(secondEvent);

        verify(
                outboxEventTransport,
                never()
        )
                .publish(thirdEvent);

        assertEquals(
                OutboxEventStatus.PUBLISHED,
                firstEvent.getStatus()
        );

        assertEquals(
                OutboxEventStatus.PUBLISHED,
                secondEvent.getStatus()
        );

        assertEquals(
                OutboxEventStatus.NEW,
                thirdEvent.getStatus()
        );
    }

    @Test
    void shouldLeaveEventNewWhenPublishingFails() {

        OutboxEvent event =
                createEvent();

        when(
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.NEW
                        )
        )
                .thenReturn(
                        List.of(event)
                );

        doThrow(
                new RuntimeException(
                        "Simulated transport failure"
                )
        )
                .when(outboxEventTransport)
                .publish(event);

        int publishedCount =
                outboxPublisherService.publishBatch(10);

        assertEquals(
                0,
                publishedCount
        );

        assertEquals(
                OutboxEventStatus.NEW,
                event.getStatus()
        );

        verify(
                outboxEventRepository,
                never()
        )
                .save(event);
    }

    @Test
    void shouldRejectInvalidBatchSize() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        outboxPublisherService
                                .publishBatch(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        outboxPublisherService
                                .publishBatch(-1)
        );
    }

    private OutboxEvent createEvent() {

        OutboxEvent event =
                new OutboxEvent();

        event.setEventId(
                UUID.randomUUID()
        );

        event.setAggregateType(
                "PAYMENT"
        );

        event.setAggregateId(
                UUID.randomUUID()
        );

        event.setEventType(
                "PAYMENT_SUCCEEDED"
        );

        event.setPayload(
                "{\"eventType\":\"PAYMENT_SUCCEEDED\"}"
        );

        event.setStatus(
                OutboxEventStatus.NEW
        );

        event.setCreatedAt(
                OffsetDateTime.now()
        );

        return event;
    }
}