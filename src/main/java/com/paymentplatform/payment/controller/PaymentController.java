package com.paymentplatform.payment.controller;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        return paymentService.createPayment(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                idempotencyKey
        );
    }
}