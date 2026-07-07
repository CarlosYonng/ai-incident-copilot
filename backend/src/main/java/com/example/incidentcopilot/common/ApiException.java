package com.example.incidentcopilot.common;

import org.springframework.http.HttpStatus;

/**
 * 自定义业务异常，封装错误码与 HTTP 状态码。
 * <p>继承自 {@link RuntimeException}，用于在业务层抛出可被
 * {@link GlobalExceptionHandler 全局异常处理器} 统一捕获的异常。</p>
 */
public class ApiException extends RuntimeException {
  /** 业务错误码，用于前端识别具体错误类型。 */
  private final String errorCode;
  /** HTTP 状态码。 */
  private final HttpStatus status;

  /**
   * 构造业务异常。
   *
   * @param errorCode 业务错误码
   * @param message   异常描述信息
   * @param status    HTTP 状态码
   */
  public ApiException(String errorCode, String message, HttpStatus status) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  /**
   * 快速创建资源未找到异常（404）。
   *
   * @param message 异常描述信息
   * @return ApiException 实例
   */
  public static ApiException notFound(String message) {
    return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  /**
   * 快速创建资源冲突异常（409）。
   *
   * @param message 异常描述信息
   * @return ApiException 实例
   */
  public static ApiException conflict(String message) {
    return new ApiException("CONFLICT", message, HttpStatus.CONFLICT);
  }

  /**
   * 快速创建工作流节点失败异常（500）。
   *
   * @param message 异常描述信息
   * @return ApiException 实例
   */
  public static ApiException workflowFailed(String message) {
    return new ApiException("WORKFLOW_NODE_FAILED", message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * 获取业务错误码。
   *
   * @return 业务错误码字符串
   */
  public String errorCode() {
    return errorCode;
  }

  /**
   * 获取 HTTP 状态码。
   *
   * @return HTTP 状态码
   */
  public HttpStatus status() {
    return status;
  }
}
