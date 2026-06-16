package com.example.incidentcopilot.diagnosis;

import com.example.incidentcopilot.audit.ToolCallLogger;
import com.example.incidentcopilot.incident.Incident;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DiagnosisMcpClient {
  private static final AtomicLong JSON_RPC_ID = new AtomicLong(1);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final ToolCallLogger toolCallLogger;
  private final String authToken;

  public DiagnosisMcpClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      ToolCallLogger toolCallLogger,
      @Value("${incident-copilot.diagnosis-mcp-base-url}") String baseUrl,
      @Value("${incident-copilot.diagnosis-mcp-token}") String authToken
  ) {
    this.restClient = restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
    this.objectMapper = objectMapper;
    this.toolCallLogger = toolCallLogger;
    this.authToken = authToken;
  }

  public DiagnosisEvidence collectEvidence(Long workflowInstanceId, String nodeName, Incident incident) {
    List<String> logs = callTool(workflowInstanceId, nodeName, "search_logs", Map.of(
        "service", incident.serviceName(),
        "trace_id", valueOrEmpty(incident.traceId()),
        "level", "ERROR",
        "hours", 24
    ), fallbackLogs(incident));

    List<String> codeHints = callTool(workflowInstanceId, nodeName, "search_code", Map.of(
        "service", incident.serviceName(),
        "query", valueOrEmpty(incident.exceptionType()) + " " + incident.serviceName()
    ), fallbackCodeHints(incident));

    List<String> tickets = callTool(workflowInstanceId, nodeName, "search_tickets", Map.of(
        "service", incident.serviceName(),
        "symptom", incident.title()
    ), fallbackTickets(incident));

    Map<String, Object> report = callToolObject(workflowInstanceId, nodeName, "generate_report", Map.of(
        "service", incident.serviceName(),
        "trace_id", valueOrEmpty(incident.traceId()),
        "message", valueOrEmpty(incident.summary())
    ), fallbackReport(incident, logs, codeHints, tickets));

    String markdown = String.valueOf(report.getOrDefault("markdown", report.getOrDefault("report_markdown", "")));
    String reportId = String.valueOf(report.getOrDefault("report_id", report.getOrDefault("id", "fallback-report")));
    String summary = String.valueOf(report.getOrDefault(
        "summary",
        incident.serviceName() + " 出现 " + valueOrDefault(incident.exceptionType(), "异常") + "，建议结合日志、Runbook 和指标处理。"
    ));

    return new DiagnosisEvidence(
        summary,
        logs,
        codeHints,
        tickets,
        reportId,
        markdown.isBlank() ? fallbackReportMarkdown(incident, logs) : markdown,
        reportId.startsWith("fallback")
    );
  }

  private List<String> callTool(
      Long workflowInstanceId,
      String nodeName,
      String toolName,
      Map<String, Object> arguments,
      List<String> fallback
  ) {
    Object result = callToolRaw(workflowInstanceId, nodeName, toolName, arguments, fallback);
    if (result instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of(String.valueOf(result));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> callToolObject(
      Long workflowInstanceId,
      String nodeName,
      String toolName,
      Map<String, Object> arguments,
      Map<String, Object> fallback
  ) {
    Object result = callToolRaw(workflowInstanceId, nodeName, toolName, arguments, fallback);
    if (result instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of("summary", String.valueOf(result), "markdown", String.valueOf(result));
  }

  private Object callToolRaw(
      Long workflowInstanceId,
      String nodeName,
      String toolName,
      Map<String, Object> arguments,
      Object fallback
  ) {
    Map<String, Object> request = Map.of(
        "jsonrpc", "2.0",
        "id", JSON_RPC_ID.getAndIncrement(),
        "method", "tools/call",
        "params", Map.of("name", toolName, "arguments", arguments)
    );
    Instant started = Instant.now();
    try {
      RestClient.RequestBodySpec requestSpec = restClient.post().uri("/mcp");
      if (authToken != null && !authToken.isBlank()) {
        requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
      }
      String responseBody = requestSpec.body(request).retrieve().body(String.class);
      Object parsed = parseToolResult(responseBody);
      toolCallLogger.log(
          workflowInstanceId,
          nodeName,
          toolName,
          request,
          parsed,
          true,
          null,
          Duration.between(started, Instant.now()).toMillis()
      );
      return parsed;
    } catch (RuntimeException exception) {
      toolCallLogger.log(
          workflowInstanceId,
          nodeName,
          toolName,
          request,
          fallback,
          false,
          exception.getMessage(),
          Duration.between(started, Instant.now()).toMillis()
      );
      return fallback;
    }
  }

  private Object parseToolResult(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      if (root.has("error")) {
        throw new IllegalStateException(root.get("error").path("message").asText("MCP error"));
      }
      String text = root.path("result").path("content").path(0).path("text").asText();
      if (text == null || text.isBlank()) {
        return Map.of();
      }
      JsonNode textNode = objectMapper.readTree(text);
      return objectMapper.convertValue(textNode, Object.class);
    } catch (Exception parseException) {
      return responseBody;
    }
  }

  private List<String> fallbackLogs(Incident incident) {
    return List.of(
        "[ERROR] " + incident.serviceName() + " " + valueOrDefault(incident.exceptionType(), "RuntimeException") + ": " + valueOrDefault(incident.summary(), incident.title()),
        "[WARN] downstream latency increased near traceId=" + valueOrDefault(incident.traceId(), "unknown")
    );
  }

  private List<String> fallbackCodeHints(Incident incident) {
    return List.of(
        incident.serviceName() + " handler should verify downstream timeout and retry boundary",
        "Add defensive logging around endpoint " + valueOrDefault(incident.endpoint(), "unknown endpoint")
    );
  }

  private List<String> fallbackTickets(Incident incident) {
    return List.of(
        "历史工单: 类似 " + incident.serviceName() + " 延迟升高时通过降级/重试缓解",
        "历史工单: 补充超时监控和链路追踪字段"
    );
  }

  private Map<String, Object> fallbackReport(
      Incident incident,
      List<String> logs,
      List<String> codeHints,
      List<String> tickets
  ) {
    return Map.of(
        "report_id", "fallback-" + incident.id(),
        "summary", incident.title() + "，证据显示错误率和延迟同时升高。",
        "root_causes", List.of("下游依赖响应变慢或超时", "重试策略导致瞬时压力放大"),
        "actions", List.of("开启延迟重试", "观察错误率和 p95 延迟", "补充下游依赖监控"),
        "evidence", Map.of("logs", logs, "codeHints", codeHints, "tickets", tickets),
        "markdown", fallbackReportMarkdown(incident, logs)
    );
  }

  private String fallbackReportMarkdown(Incident incident, List<String> logs) {
    return "# 诊断摘要\n\n" + incident.title() + "\n\n## 关键日志\n\n- " + String.join("\n- ", logs);
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String valueOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
