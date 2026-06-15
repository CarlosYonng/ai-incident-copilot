package com.example.incidentcopilot.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final String errorCode;
  private final HttpStatus status;

  public ApiException(String errorCode, String message, HttpStatus status) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  public static ApiException notFound(String message) {
    return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  public static ApiException conflict(String message) {
    return new ApiException("CONFLICT", message, HttpStatus.CONFLICT);
  }

  public static ApiException workflowFailed(String message) {
    return new ApiException("WORKFLOW_NODE_FAILED", message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
