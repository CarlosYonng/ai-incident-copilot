package com.example.incidentcopilot.system;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统健康检查响应体。
 *
 * <p>包含应用整体状态、名称、检查时间和各依赖组件的健康状态。</p>
 *
 * @param status       整体健康状态（如 "UP"、"DOWN"）
 * @param application  应用名称
 * @param checkedAt    健康检查执行时间
 * @param dependencies 各依赖组件的健康状态映射（组件名 -> 状态详情）
 */
public record HealthResponse(
    String status,
    String application,
    LocalDateTime checkedAt,
    Map<String, Object> dependencies
) {
}
