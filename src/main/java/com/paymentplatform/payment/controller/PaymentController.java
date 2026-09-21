package com.paymentplatform.payment.controller;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import com.paymentplatform.payment.controller.dto.PaymentAttemptResponse;
import com.paymentplatform.payment.controller.dto.PaymentResponse;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.service.PaymentAttemptService;
import com.paymentplatform.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAttemptService paymentAttemptService;

    public PaymentController(PaymentService paymentService, PaymentAttemptService paymentAttemptService) {
        this.paymentService = paymentService;
        this.paymentAttemptService = paymentAttemptService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestHeader("X-Merchant-Id") UUID merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        Payment payment = paymentService.createPayment(
                merchantId,
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                idempotencyKey
        );

        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getMerchantId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    @GetMapping("/{paymentId}/attempts")
    public List<PaymentAttemptResponse> getAttempts(
            @PathVariable UUID paymentId
    ) {
        return paymentAttemptService.getAttempts(paymentId)
                .stream()
                .map(attempt -> new PaymentAttemptResponse(
                        attempt.getAttemptId(),
                        attempt.getPaymentId(),
                        attempt.getAttemptNumber(),
                        attempt.getStatus(),
                        attempt.getGateway(),
                        attempt.getGatewayTransactionId(),
                        attempt.getFailureCode(),
                        attempt.getFailureMessage(),
                        attempt.getCreatedAt(),
                        attempt.getCompletedAt()
                ))
                .toList();
    }

    @PostMapping("/{paymentId}/process")
    public PaymentResponse processPayment(
            @PathVariable UUID paymentId
    ) {
        Payment payment = paymentService.processPayment(paymentId);

        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getMerchantId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    @PostMapping("/{paymentId}/retry")
    public PaymentResponse retryPayment(
            @PathVariable UUID paymentId
    ) {
        Payment payment =
                paymentService.retryPayment(paymentId);

        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getMerchantId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}