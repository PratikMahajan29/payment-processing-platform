package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.enums.PaymentEventType;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OutboxEventService {

    private static final String PAYMENT_AGGREGATE =
            "PAYMENT";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository =
                outboxEventRepository;
    }

    public OutboxEvent createPaymentEvent(
            Payment payment,
            PaymentEventType eventType
    ) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment cannot be null"
            );
        }

        if (eventType == null) {
            throw new IllegalArgumentException(
                    "Payment event type cannot be null"
            );
        }

        UUID eventId =
                UUID.randomUUID();

        OutboxEvent event =
                new OutboxEvent();

        event.setEventId(eventId);

        event.setAggregateType(
                PAYMENT_AGGREGATE
        );

        event.setAggregateId(
                payment.getPaymentId()
        );

        event.setEventType(
                eventType.name()
        );

        event.setPayload(
                buildPayload(
                        eventId,
                        payment,
                        eventType
                )
        );

        event.setStatus(
                OutboxEventStatus.NEW
        );

        event.setCreatedAt(
                OffsetDateTime.now()
        );

        return outboxEventRepository.save(event);
    }

    private String buildPayload(
            UUID eventId,
            Payment payment,
            PaymentEventType eventType
    ) {

        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "paymentId": "%s",
                  "merchantId": "%s",
                  "orderId": "%s",
                  "customerId": "%s",
                  "amountMinor": %d,
                  "currency": "%s",
                  "status": "%s"
                }
                """.formatted(
                eventId,
                eventType.name(),
                payment.getPaymentId(),
                payment.getMerchantId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus()
        );
    }
}