package com.example.incidentcopilot.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局统一 API 响应封装。
 * <p>所有控制器方法均通过此类返回标准结构，
 * 包含成功标志、状态码、消息和数据负载。</p>
 *
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(
    /** 操作是否成功 */
    boolean success,
    /** 响应数据负载 */
    T data,
    /** 业务错误码，成功时为 null */
    String errorCode,
    /** 响应消息，成功时为 null */
    String message,
    /** 请求唯一标识 */
    String requestId
) {
  private static final DateTimeFormatter REQUEST_ID_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

  /**
   * 创建成功响应。
   *
   * @param data 响应数据
   * @param <T>  数据类型
   * @return 成功响应实例
   */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, nextRequestId());
  }

  /**
   * 创建错误响应。
   *
   * @param errorCode 业务错误码
   * @param message   错误描述信息
   * @return 错误响应实例
   */
  public static ApiResponse<Void> error(String errorCode, String message) {
    return new ApiResponse<>(false, null, errorCode, message, nextRequestId());
  }

  /**
   * 生成全局唯一的请求 ID。
   *
   * @return 请求 ID 字符串，格式为 "req-{yyyyMMddHHmmssSSS}"
   */
  private static String nextRequestId() {
    return "req-" + LocalDateTime.now().format(REQUEST_ID_FORMAT);
  }
}
