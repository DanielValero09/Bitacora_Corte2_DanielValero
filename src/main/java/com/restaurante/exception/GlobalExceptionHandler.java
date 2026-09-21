package com.restaurante.exception;

import com.restaurante.model.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return businessError(exception, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyExists(
            ResourceAlreadyExistsException exception, HttpServletRequest request) {
        return businessError(exception, HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException exception, HttpServletRequest request) {
        return businessError(exception, HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", request);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(
            InvalidOrderStateException exception, HttpServletRequest request) {
        return businessError(exception, HttpStatus.CONFLICT, "INVALID_ORDER_STATE", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.merge(error.getField(),
                        Objects.toString(error.getDefaultMessage(), "Valor inválido"),
                        // Elegir el menor mensaje hace independiente el orden del proveedor.
                        (first, second) -> first.compareTo(second) <= 0 ? first : second));
        log.warn("Validación fallida en {}: {}", request.getRequestURI(), fieldErrors.keySet());
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene campos inválidos", request, fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Error inesperado en {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error interno del servidor", request, Map.of());
    }

    private ResponseEntity<ErrorResponse> businessError(
            RuntimeException exception, HttpStatus status, String code, HttpServletRequest request) {
        log.warn("{} en {}: {}", code, request.getRequestURI(), exception.getMessage());
        return response(status, code, exception.getMessage(), request, Map.of());
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message,
            HttpServletRequest request, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(), status.value(), code, message, request.getRequestURI(), fieldErrors));
    }
}
