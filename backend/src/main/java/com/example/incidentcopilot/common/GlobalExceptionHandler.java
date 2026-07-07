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

/**
 * 全局异常处理器。
 *
 * <p>统一把业务异常、参数校验异常和未预期异常包装为 {@link ApiResponse}，
 * 保证前端和脚本拿到稳定响应结构。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * 处理业务异常（{@link ApiException}）。
   *
   * @param exception 业务异常实例
   * @return 标准错误响应实体
   */
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

  /**
   * 处理参数校验异常（{@link MethodArgumentNotValidException} 和 {@link BindException}）。
   * <p>提取字段校验错误信息，拼接为易读的描述返回。</p>
   *
   * @param exception 参数校验异常实例
   * @return 400 错误响应实体
   */
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
      message = "请求参数校验失败";
    }
    log.warn("validation_failed message={}", message);
    return ResponseEntity
        .badRequest()
        .body(ApiResponse.error("VALIDATION_FAILED", message));
  }

  /**
   * 处理未预期的运行时异常。
   * <p>避免未捕获异常直接暴露给前端，统一包装为 500 响应。</p>
   *
   * @param exception 未预期异常实例
   * @return 500 错误响应实体
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
    log.error("unexpected_exception message={}", exception.getMessage(), exception);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_ERROR", exception.getMessage()));
  }
}
