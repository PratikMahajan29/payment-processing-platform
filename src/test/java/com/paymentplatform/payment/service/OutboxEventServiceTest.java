package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.enums.PaymentEventType;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final JsonMapper objectMapper =
            new JsonMapper();

    @Test
    void shouldCreateSuccessfulPaymentEvent() throws Exception {

        OutboxEventService service =
                new OutboxEventService(
                        outboxEventRepository
                );

        Payment payment =
                payment(
                        PaymentStatus.SUCCEEDED
                );

        when(
                outboxEventRepository.save(
                        any(OutboxEvent.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        OutboxEvent result =
                service.createPaymentEvent(
                        payment,
                        PaymentEventType.PAYMENT_SUCCEEDED
                );

        assertNotNull(
                result.getEventId()
        );

        assertEquals(
                "PAYMENT",
                result.getAggregateType()
        );

        assertEquals(
                payment.getPaymentId(),
                result.getAggregateId()
        );

        assertEquals(
                PaymentEventType.PAYMENT_SUCCEEDED.name(),
                result.getEventType()
        );

        assertEquals(
                OutboxEventStatus.NEW,
                result.getStatus()
        );

        assertNotNull(
                result.getCreatedAt()
        );

        assertNotNull(
                result.getPayload()
        );

        JsonNode payload =
                objectMapper.readTree(
                        result.getPayload()
                );

        assertEquals(
                result.getEventId().toString(),
                payload.get("eventId").asText()
        );

        assertEquals(
                PaymentEventType.PAYMENT_SUCCEEDED.name(),
                payload.get("eventType").asText()
        );

        assertEquals(
                payment.getPaymentId().toString(),
                payload.get("paymentId").asText()
        );

        assertEquals(
                payment.getMerchantId().toString(),
                payload.get("merchantId").asText()
        );

        assertEquals(
                payment.getOrderId().toString(),
                payload.get("orderId").asText()
        );

        assertEquals(
                payment.getCustomerId().toString(),
                payload.get("customerId").asText()
        );

        assertEquals(
                payment.getCurrency(),
                payload.get("currency").asText()
        );

        assertEquals(
                payment.getStatus().name(),
                payload.get("status").asText()
        );

        verify(
                outboxEventRepository
        ).save(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldCreateFailedPaymentEvent() throws Exception {

        OutboxEventService service =
                new OutboxEventService(
                        outboxEventRepository
                );

        Payment payment =
                payment(
                        PaymentStatus.FAILED
                );

        when(
                outboxEventRepository.save(
                        any(OutboxEvent.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        OutboxEvent result =
                service.createPaymentEvent(
                        payment,
                        PaymentEventType.PAYMENT_FAILED
                );

        assertNotNull(
                result.getEventId()
        );

        assertEquals(
                "PAYMENT",
                result.getAggregateType()
        );

        assertEquals(
                payment.getPaymentId(),
                result.getAggregateId()
        );

        assertEquals(
                PaymentEventType.PAYMENT_FAILED.name(),
                result.getEventType()
        );

        assertEquals(
                OutboxEventStatus.NEW,
                result.getStatus()
        );

        assertNotNull(
                result.getCreatedAt()
        );

        JsonNode payload =
                objectMapper.readTree(
                        result.getPayload()
                );

        assertEquals(
                result.getEventId().toString(),
                payload.get("eventId").asText()
        );

        assertEquals(
                PaymentEventType.PAYMENT_FAILED.name(),
                payload.get("eventType").asText()
        );

        assertEquals(
                payment.getPaymentId().toString(),
                payload.get("paymentId").asText()
        );

        assertEquals(
                payment.getMerchantId().toString(),
                payload.get("merchantId").asText()
        );

        assertEquals(
                payment.getOrderId().toString(),
                payload.get("orderId").asText()
        );

        assertEquals(
                payment.getCustomerId().toString(),
                payload.get("customerId").asText()
        );

        assertEquals(
                payment.getCurrency(),
                payload.get("currency").asText()
        );

        assertEquals(
                payment.getStatus().name(),
                payload.get("status").asText()
        );

        verify(
                outboxEventRepository
        ).save(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldPersistExactlyTheEventThatWasCreated() {

        OutboxEventService service =
                new OutboxEventService(
                        outboxEventRepository
                );

        Payment payment =
                payment(
                        PaymentStatus.SUCCEEDED
                );

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(
                        OutboxEvent.class
                );

        when(
                outboxEventRepository.save(
                        any(OutboxEvent.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        OutboxEvent result =
                service.createPaymentEvent(
                        payment,
                        PaymentEventType.PAYMENT_SUCCEEDED
                );

        verify(
                outboxEventRepository
        ).save(
                captor.capture()
        );

        OutboxEvent savedEvent =
                captor.getValue();

        assertEquals(
                result.getEventId(),
                savedEvent.getEventId()
        );

        assertEquals(
                result.getAggregateType(),
                savedEvent.getAggregateType()
        );

        assertEquals(
                result.getAggregateId(),
                savedEvent.getAggregateId()
        );

        assertEquals(
                result.getEventType(),
                savedEvent.getEventType()
        );

        assertEquals(
                result.getPayload(),
                savedEvent.getPayload()
        );

        assertEquals(
                result.getStatus(),
                savedEvent.getStatus()
        );
    }

    @Test
    void shouldRejectNullPayment() {

        OutboxEventService service =
                new OutboxEventService(
                        outboxEventRepository
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.createPaymentEvent(
                                        null,
                                        PaymentEventType.PAYMENT_SUCCEEDED
                                )
                );

        assertEquals(
                "Payment cannot be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                outboxEventRepository
        );
    }

    @Test
    void shouldRejectNullEventType() {

        OutboxEventService service =
                new OutboxEventService(
                        outboxEventRepository
                );

        Payment payment =
                payment(
                        PaymentStatus.SUCCEEDED
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.createPaymentEvent(
                                        payment,
                                        null
                                )
                );

        assertEquals(
                "Payment event type cannot be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                outboxEventRepository
        );
    }

    private Payment payment(
            PaymentStatus status
    ) {

        Payment payment =
                new Payment();

        payment.setPaymentId(
                UUID.randomUUID()
        );

        payment.setMerchantId(
                UUID.randomUUID()
        );

        payment.setOrderId(
                UUID.randomUUID()
        );

        payment.setCustomerId(
                UUID.randomUUID()
        );

        payment.setAmount(
                49950L
        );

        payment.setCurrency(
                "INR"
        );

        payment.setStatus(
                status
        );

        payment.setIdempotencyKey(
                "test-idempotency-key"
        );

        return payment;
    }
}