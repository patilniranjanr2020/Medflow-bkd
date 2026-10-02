package com.medflow.shared.web;

import com.medflow.shared.api.ApiError;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.exception.BusinessRuleViolationException;
import com.medflow.shared.exception.DuplicateResourceException;
import com.medflow.shared.exception.InvalidCredentialsException;
import com.medflow.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates exceptions into the {@link ApiResponse} envelope with consistent status codes. */
@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
    var errors = exception.getBindingResult().getFieldErrors().stream()
        .map(e -> new ApiError(e.getField(), "VALIDATION_ERROR", e.getDefaultMessage()))
        .toList();
    return response(HttpStatus.BAD_REQUEST, "Validation failed", errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException exception) {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException exception) {
    return response(HttpStatus.BAD_REQUEST,
        "Invalid value for parameter '" + exception.getName() + "'", List.of());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
  }

  @ExceptionHandler(DuplicateResourceException.class)
  ResponseEntity<ApiResponse<Void>> conflict(DuplicateResourceException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
  }

  @ExceptionHandler(com.medflow.shared.exception.OptimisticLockException.class)
  ResponseEntity<ApiResponse<Void>> optimisticLock(com.medflow.shared.exception.OptimisticLockException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
  }

  @ExceptionHandler(BusinessRuleViolationException.class)
  ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleViolationException exception) {
    return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), List.of());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ApiResponse<Void>> unauthorized(InvalidCredentialsException exception) {
    return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException exception) {
    return response(HttpStatus.FORBIDDEN, "You do not have permission to perform this action",
        List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
    log.error("Unhandled exception", exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", List.of());
  }

  private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message,
      List<ApiError> errors) {
    return ResponseEntity.status(status).body(ApiResponse.failure(message, errors));
  }
}
