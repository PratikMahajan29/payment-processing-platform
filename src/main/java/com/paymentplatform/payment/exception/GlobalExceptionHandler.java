package com.paymentplatform.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyConflict(
            IdempotencyKeyConflictException exception
    ) {
        return new ErrorResponse(
                "IDEMPOTENCY_KEY_CONFLICT",
                exception.getMessage(),
                OffsetDateTime.now()
        );
    }

    public record ErrorResponse(
            String code,
            String message,
            OffsetDateTime timestamp
    ) {
    }

    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyRequestInProgress(
            IdempotencyRequestInProgressException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidPaymentState(
            InvalidPaymentStateException exception
    ) {
        return new ErrorResponse(
                "INVALID_PAYMENT_STATE",
                exception.getMessage(),
                OffsetDateTime.now()
        );
    }

    @ExceptionHandler(RetryLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRetryLimitExceeded(
            RetryLimitExceededException exception
    ) {
        return new ErrorResponse(
                "RETRY_LIMIT_EXCEEDED",
                exception.getMessage(),
                OffsetDateTime.now()
        );
    }
}