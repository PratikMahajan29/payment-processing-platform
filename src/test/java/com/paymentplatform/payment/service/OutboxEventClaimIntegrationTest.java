package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(
        properties = "payment.gateway.mock.fail-attempts=0"
)
@DirtiesContext
class OutboxEventClaimIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventClaimService outboxEventClaimService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOutboxEvents() {
        jdbcTemplate.update(
                "DELETE FROM outbox_events"
        );
    }

    @Test
    void shouldClaimOnlyRequestedNumberOfNewEvents() {

        OutboxEvent firstEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(3)
                );

        OutboxEvent secondEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(2)
                );

        OutboxEvent thirdEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(1)
                );

        outboxEventRepository.save(firstEvent);
        outboxEventRepository.save(secondEvent);
        outboxEventRepository.save(thirdEvent);

        List<OutboxEvent> claimedEvents =
                outboxEventClaimService.claimBatch(2);

        assertEquals(
                2,
                claimedEvents.size()
        );

        assertEquals(
                firstEvent.getEventId(),
                claimedEvents.get(0).getEventId()
        );

        assertEquals(
                secondEvent.getEventId(),
                claimedEvents.get(1).getEventId()
        );

        OutboxEvent persistedFirst =
                outboxEventRepository
                        .findById(
                                firstEvent.getEventId()
                        )
                        .orElseThrow();

        OutboxEvent persistedSecond =
                outboxEventRepository
                        .findById(
                                secondEvent.getEventId()
                        )
                        .orElseThrow();

        OutboxEvent persistedThird =
                outboxEventRepository
                        .findById(
                                thirdEvent.getEventId()
                        )
                        .orElseThrow();

        assertEquals(
                OutboxEventStatus.PUBLISHING,
                persistedFirst.getStatus()
        );

        assertEquals(
                OutboxEventStatus.PUBLISHING,
                persistedSecond.getStatus()
        );

        assertEquals(
                OutboxEventStatus.NEW,
                persistedThird.getStatus()
        );

        assertNotNull(
                persistedFirst.getPublishingStartedAt()
        );

        assertNotNull(
                persistedSecond.getPublishingStartedAt()
        );
    }

    @Test
    void shouldNotClaimPublishedEvents() {

        OutboxEvent publishedEvent =
                createEvent(
                        OffsetDateTime.now()
                                .minusSeconds(1)
                );

        publishedEvent.setStatus(
                OutboxEventStatus.PUBLISHED
        );

        publishedEvent.setPublishedAt(
                OffsetDateTime.now()
        );

        outboxEventRepository.save(
                publishedEvent
        );

        List<OutboxEvent> claimedEvents =
                outboxEventClaimService.claimBatch(10);

        assertEquals(
                0,
                claimedEvents.size()
        );

        OutboxEvent persisted =
                outboxEventRepository
                        .findById(
                                publishedEvent.getEventId()
                        )
                        .orElseThrow();

        assertEquals(
                OutboxEventStatus.PUBLISHED,
                persisted.getStatus()
        );
    }

    private OutboxEvent createEvent(
            OffsetDateTime createdAt
    ) {

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
                createdAt
        );

        return event;
    }

    @Test
    void shouldNotClaimSameEventsFromConcurrentPublishers()
            throws Exception {

        OutboxEvent firstEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(4)
                );

        OutboxEvent secondEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(3)
                );

        OutboxEvent thirdEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(2)
                );

        OutboxEvent fourthEvent =
                createEvent(
                        OffsetDateTime.now().minusSeconds(1)
                );

        outboxEventRepository.saveAll(
                List.of(
                        firstEvent,
                        secondEvent,
                        thirdEvent,
                        fourthEvent
                )
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Callable<List<OutboxEvent>> claimTask =
                    () ->
                            outboxEventClaimService
                                    .claimBatch(2);

            Future<List<OutboxEvent>> firstPublisher =
                    executor.submit(claimTask);

            Future<List<OutboxEvent>> secondPublisher =
                    executor.submit(claimTask);

            List<OutboxEvent> firstBatch =
                    firstPublisher.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<OutboxEvent> secondBatch =
                    secondPublisher.get(
                            10,
                            TimeUnit.SECONDS
                    );

            assertEquals(
                    2,
                    firstBatch.size()
            );

            assertEquals(
                    2,
                    secondBatch.size()
            );

            Set<UUID> claimedEventIds =
                    new HashSet<>();

            firstBatch.forEach(
                    event ->
                            claimedEventIds.add(
                                    event.getEventId()
                            )
            );

            secondBatch.forEach(
                    event ->
                            claimedEventIds.add(
                                    event.getEventId()
                            )
            );

            assertEquals(
                    4,
                    claimedEventIds.size()
            );

            Set<UUID> expectedEventIds =
                    Set.of(
                            firstEvent.getEventId(),
                            secondEvent.getEventId(),
                            thirdEvent.getEventId(),
                            fourthEvent.getEventId()
                    );

            assertEquals(
                    expectedEventIds,
                    claimedEventIds
            );

            long publishingCount =
                    outboxEventRepository
                            .findAll()
                            .stream()
                            .filter(
                                    event ->
                                            event.getStatus()
                                                    == OutboxEventStatus.PUBLISHING
                            )
                            .count();

            assertEquals(
                    4,
                    publishingCount
            );

        } finally {

            executor.shutdownNow();
        }
    }
}