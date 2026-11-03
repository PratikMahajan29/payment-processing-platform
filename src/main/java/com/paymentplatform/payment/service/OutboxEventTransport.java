package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.model.OutboxEvent;

public interface OutboxEventTransport {

    void publish(OutboxEvent event);
}