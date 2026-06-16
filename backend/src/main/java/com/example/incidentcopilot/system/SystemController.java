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

@RestController
@RequestMapping("/api")
public class SystemController {
  private final JdbcTemplate jdbcTemplate;
  private final String diagnosisMcpBaseUrl;
  private final String runbookDir;

  public SystemController(
      JdbcTemplate jdbcTemplate,
      @Value("${incident-copilot.diagnosis-mcp-base-url}") String diagnosisMcpBaseUrl,
      @Value("${incident-copilot.runbook-dir}") String runbookDir
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.diagnosisMcpBaseUrl = diagnosisMcpBaseUrl;
    this.runbookDir = runbookDir;
  }

  @GetMapping("/health")
  public ApiResponse<HealthResponse> health() {
    Map<String, Object> dependencies = new LinkedHashMap<>();
    boolean databaseReady = isDatabaseReady();
    dependencies.put("database", Map.of("status", databaseReady ? "UP" : "DOWN"));
    dependencies.put("diagnosisMcp", Map.of(
        "status", "OPTIONAL",
        "baseUrl", diagnosisMcpBaseUrl,
        "fallbackEnabled", true
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

  private boolean isDatabaseReady() {
    try {
      Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return value != null && value == 1;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
