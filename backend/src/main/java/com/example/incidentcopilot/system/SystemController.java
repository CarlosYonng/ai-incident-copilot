package com.example.incidentcopilot.system;

import com.example.incidentcopilot.common.ApiResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统状态接口。
 *
 * <p>聚合数据库、MCP 诊断服务配置和 Runbook 目录状态，供本地脚本和前端健康检查使用。</p>
 */
@RestController
@RequestMapping("/api")
public class SystemController {

  /** Spring JDBC 模板，用于检测数据库连接状态 */
  private final JdbcTemplate jdbcTemplate;

  /** MCP 诊断服务基础 URL */
  private final String diagnosisMcpBaseUrl;

  /** 是否启用 MCP 诊断 Fallback */
  private final boolean diagnosisMcpFallbackEnabled;

  /** Runbook 手册存储目录路径 */
  private final String runbookDir;

  /**
   * 构造方法，注入依赖和配置项。
   *
   * @param jdbcTemplate                 Spring JDBC 模板
   * @param diagnosisMcpBaseUrl          MCP 诊断服务基础 URL
   * @param diagnosisMcpFallbackEnabled  是否启用 MCP 诊断 Fallback
   * @param runbookDir                   Runbook 手册存储目录路径
   */
  public SystemController(
      JdbcTemplate jdbcTemplate,
      @Value("${incident-copilot.diagnosis-mcp-base-url}") String diagnosisMcpBaseUrl,
      @Value("${incident-copilot.diagnosis-mcp-fallback-enabled}") boolean diagnosisMcpFallbackEnabled,
      @Value("${incident-copilot.runbook-dir}") String runbookDir
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.diagnosisMcpBaseUrl = diagnosisMcpBaseUrl;
    this.diagnosisMcpFallbackEnabled = diagnosisMcpFallbackEnabled;
    this.runbookDir = runbookDir;
  }

  /**
   * 健康检查端点，聚合数据库、MCP 诊断服务和 Runbook 目录的健康状态。
   *
   * <p>数据库不可用时返回 {@code DEGRADED} 而非 {@code DOWN}，允许前端根据降级状态动态
   * 展示部分功能受限。</p>
   *
   * @return 健康检查响应体，包含各依赖组件状态
   */
  @GetMapping("/health")
  public ApiResponse<HealthResponse> health() {
    Map<String, Object> dependencies = new LinkedHashMap<>();
    boolean databaseReady = isDatabaseReady();
    dependencies.put("database", Map.of("status", databaseReady ? "UP" : "DOWN"));
    dependencies.put("diagnosisMcp", Map.of(
        "status", "OPTIONAL",
        "baseUrl", diagnosisMcpBaseUrl,
        "fallbackEnabled", diagnosisMcpFallbackEnabled
    ));
    dependencies.put("runbooks", Map.of(
        "status", Files.isDirectory(Path.of(runbookDir)) ? "UP" : "DOWN",
        "path", runbookDir
    ));
    String status = databaseReady ? "UP" : "DEGRADED";
    return ApiResponse.ok(new HealthResponse(
        status,
        "incident-copilot-backend",
        LocalDateTime.now(),
        dependencies
    ));
  }

  /**
   * 检测数据库是否可达。
   *
   * <p>执行 {@code SELECT 1} 语句验证数据库连接，捕获所有运行时异常以返回安全结果。</p>
   *
   * @return 数据库可达返回 true，否则返回 false
   */
  private boolean isDatabaseReady() {
    try {
      Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return value != null && value == 1;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
