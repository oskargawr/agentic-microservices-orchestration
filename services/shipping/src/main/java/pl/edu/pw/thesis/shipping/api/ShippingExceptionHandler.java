package pl.edu.pw.thesis.shipping.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.edu.pw.thesis.contracts.common.ApiError;
import pl.edu.pw.thesis.contracts.common.ErrorCode;
import pl.edu.pw.thesis.contracts.common.IdempotencyConflictException;
import pl.edu.pw.thesis.contracts.transition.IllegalTransitionException;

@RestControllerAdvice
public class ShippingExceptionHandler {

    @ExceptionHandler(ShippingException.class)
    public ResponseEntity<ApiError> handleShipping(ShippingException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiError.of(ex.code(), ex.getMessage(), ex.idempotencyKey()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotency(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.toApiError());
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ApiError> handleTransition(IllegalTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ErrorCode.INVALID_STATE_TRANSITION, ex.getMessage()));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentNotValidException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }
}
