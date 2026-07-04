package com.example.incidentcopilot.common;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
    log.warn(
        "api_exception status={} errorCode={} message={}",
        exception.status().value(),
        exception.errorCode(),
        exception.getMessage()
    );
    return ResponseEntity
        .status(exception.status())
        .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
    String message;
    if (exception instanceof MethodArgumentNotValidException validationException) {
      message = validationException.getBindingResult().getFieldErrors().stream()
          .map(error -> error.getField() + " " + error.getDefaultMessage())
          .collect(Collectors.joining("; "));
    } else if (exception instanceof BindException bindException) {
      message = bindException.getBindingResult().getFieldErrors().stream()
          .map(error -> error.getField() + " " + error.getDefaultMessage())
          .collect(Collectors.joining("; "));
    } else {
      message = "Request validation failed";
    }
    log.warn("validation_failed message={}", message);
    return ResponseEntity
        .badRequest()
        .body(ApiResponse.error("VALIDATION_FAILED", message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
    log.error("unexpected_exception message={}", exception.getMessage(), exception);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_ERROR", exception.getMessage()));
  }
}
