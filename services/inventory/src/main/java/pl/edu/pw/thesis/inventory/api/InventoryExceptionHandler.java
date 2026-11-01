package pl.edu.pw.thesis.inventory.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.edu.pw.thesis.contracts.common.ApiError;
import pl.edu.pw.thesis.contracts.common.ErrorCode;
import pl.edu.pw.thesis.contracts.common.IdempotencyConflictException;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ApiError> handleInventory(InventoryException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiError.of(ex.code(), ex.getMessage(), ex.idempotencyKey()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotency(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.toApiError());
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentNotValidException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }
}
