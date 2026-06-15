package com.example.incidentcopilot.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record ApiResponse<T>(
    boolean success,
    T data,
    String errorCode,
    String message,
    String requestId
) {
  private static final DateTimeFormatter REQUEST_ID_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, nextRequestId());
  }

  public static ApiResponse<Void> error(String errorCode, String message) {
    return new ApiResponse<>(false, null, errorCode, message, nextRequestId());
  }

  private static String nextRequestId() {
    return "req-" + LocalDateTime.now().format(REQUEST_ID_FORMAT);
  }
}
