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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 诊断 MCP 客户端，通过 JSON-RPC 协议调用外部 diagnosis-service 获取故障证据。
 *
 * <p>支持日志搜索、代码搜索、工单查询和报告生成等功能。
 * 每次工具调用都会经由 {@link ToolCallLogger} 写入审计表，
 * 方便复盘时追踪请求参数、响应内容、失败原因和耗时。</p>
 *
 * <p>提供 fallback 机制：当 MCP 调用失败且 fallback 开启时，
 * 返回模拟数据以避免工作流中断，适用于开发和演示环境。</p>
 */
@Component
public class DiagnosisMcpClient {
  private static final Logger log = LoggerFactory.getLogger(DiagnosisMcpClient.class);
  /** JSON-RPC 请求 ID 生成器，保证每次调用 ID 唯一递增。 */
  private static final AtomicLong JSON_RPC_ID = new AtomicLong(1);

  /** HTTP 客户端，用于向 diagnosis-service 发送 MCP 请求。 */
  private final RestClient restClient;
  /** Jackson 对象映射器，用于解析 MCP 响应 JSON。 */
  private final ObjectMapper objectMapper;
  /** 工具调用审计日志写入器，记录每次 MCP 调用的请求/响应。 */
  private final ToolCallLogger toolCallLogger;
  /** MCP 服务认证令牌（Bearer Token）。 */
  private final String authToken;
  /** 是否启用 fallback 模式。开启时 MCP 调用失败返回模拟数据。 */
  private final boolean fallbackEnabled;

  /**
   * 构造诊断 MCP 客户端。
   *
   * @param restClientBuilder RestClient 构建器（Spring 注入）
   * @param objectMapper Jackson 对象映射器
   * @param toolCallLogger 工具调用审计日志写入器
   * @param baseUrl diagnosis-service 的基础 URL
   * @param authToken MCP 服务认证令牌
   * @param fallbackEnabled 是否启用 fallback 模式
   */
  public DiagnosisMcpClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      ToolCallLogger toolCallLogger,
      @Value("${incident-copilot.diagnosis-mcp-base-url}") String baseUrl,
      @Value("${incident-copilot.diagnosis-mcp-token}") String authToken,
      @Value("${incident-copilot.diagnosis-mcp-fallback-enabled}") boolean fallbackEnabled
  ) {
    this.restClient = restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
    this.objectMapper = objectMapper;
    this.toolCallLogger = toolCallLogger;
    this.authToken = authToken;
    this.fallbackEnabled = fallbackEnabled;
  }

  /**
   * 汇总一次故障诊断所需的外部证据。
   *
   * <p>Copilot 只通过 MCP JSON-RPC 调用 diagnosis-service，不直接读对方数据库，
   * 这样可以保持服务边界清晰，也方便把每次工具调用完整写入审计表。</p>
   */
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

  /**
   * 调用 MCP 工具并返回字符串列表结果。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param nodeName 发起调用的节点名称
   * @param toolName MCP 工具名称
   * @param arguments 工具调用参数
   * @param fallback MCP 调用失败时的备选数据
   * @return 工具返回的字符串列表；若返回非列表类型则包装为单元素列表
   */
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

  /**
   * 调用 MCP 工具并返回 Map 类型结果。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param nodeName 发起调用的节点名称
   * @param toolName MCP 工具名称
   * @param arguments 工具调用参数
   * @param fallback MCP 调用失败时的备选数据
   * @return 工具返回的 Map 结果
   */
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

  /**
   * 执行底层的 MCP JSON-RPC 工具调用，包括鉴权、请求发送、响应解析和审计日志记录。
   *
   * <p>当 MCP 调用失败时，根据 {@link #fallbackEnabled} 决定返回 fallback 数据还是抛出异常。
   * 失败信息也会记录到审计表。</p>
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param nodeName 发起调用的节点名称
   * @param toolName MCP 工具名称
   * @param arguments 工具调用参数
   * @param fallback MCP 调用失败时的备选数据
   * @return 工具返回的原始对象（可能是 List、Map 或 String）
   * @throws IllegalStateException 当 fallback 未启用且 MCP 调用失败时抛出
   */
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
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      toolCallLogger.log(
          workflowInstanceId,
          nodeName,
          toolName,
          request,
          parsed,
          true,
          null,
          durationMs
      );
      log.info(
          "mcp_tool_call_succeeded workflowInstanceId={} node={} tool={} durationMs={}",
          workflowInstanceId,
          nodeName,
          toolName,
          durationMs
      );
      return parsed;
    } catch (RuntimeException exception) {
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      Object responseForAudit = fallbackEnabled ? fallback : Map.of();
      toolCallLogger.log(
          workflowInstanceId,
          nodeName,
          toolName,
          request,
          responseForAudit,
          false,
          exception.getMessage(),
          durationMs
      );
      log.warn(
          "mcp_tool_call_failed workflowInstanceId={} node={} tool={} fallbackEnabled={} durationMs={} message={}",
          workflowInstanceId,
          nodeName,
          toolName,
          fallbackEnabled,
          durationMs,
          exception.getMessage()
      );
      if (!fallbackEnabled) {
        // 严格模式用于真实链路验收：diagnosis-service 缺失或不健康时必须让工作流失败，
        // 避免系统悄悄使用演示 fallback 掩盖依赖问题。
        throw new IllegalStateException("Diagnosis MCP tool call failed in strict mode: " + toolName, exception);
      }
      return fallback;
    }
  }

  /**
   * 解析 MCP JSON-RPC 响应体，提取工具调用结果。
   *
   * <p>标准 MCP 响应结构为 {@code result.content[0].text}，其中 text 是 JSON 字符串。
   * 若无法按标准结构解析，则保留原始响应字符串。</p>
   *
   * @param responseBody MCP 服务返回的原始 JSON 响应体
   * @return 解析后的工具调用结果（Map、List 或原始字符串）
   * @throws IllegalStateException 如果响应中包含 error 字段
   */
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
      // 某些 MCP 工具可能返回纯文本。这里保留原始响应，避免把潜在证据直接丢弃。
      return responseBody;
    }
  }

  /**
   * 生成日志搜索的 fallback 数据，用于 MCP 调用失败时的降级。
   *
   * @param incident 当前故障事件
   * @return 模拟的错误日志列表
   */
  private List<String> fallbackLogs(Incident incident) {
    return List.of(
        "[ERROR] " + incident.serviceName() + " " + valueOrDefault(incident.exceptionType(), "RuntimeException") + ": " + valueOrDefault(incident.summary(), incident.title()),
        "[WARN] downstream latency increased near traceId=" + valueOrDefault(incident.traceId(), "unknown")
    );
  }

  /**
   * 生成代码搜索的 fallback 数据，用于 MCP 调用失败时的降级。
   *
   * @param incident 当前故障事件
   * @return 模拟的代码线索列表
   */
  private List<String> fallbackCodeHints(Incident incident) {
    return List.of(
        incident.serviceName() + " 处理逻辑需要确认下游超时和重试边界",
        "在接口 " + valueOrDefault(incident.endpoint(), "未知接口") + " 周围补充防御性日志"
    );
  }

  /**
   * 生成工单搜索的 fallback 数据，用于 MCP 调用失败时的降级。
   *
   * @param incident 当前故障事件
   * @return 模拟的历史工单列表
   */
  private List<String> fallbackTickets(Incident incident) {
    return List.of(
        "历史工单: 类似 " + incident.serviceName() + " 延迟升高时通过降级/重试缓解",
        "历史工单: 补充超时监控和链路追踪字段"
    );
  }

  /**
   * 生成诊断报告的 fallback 数据，用于 MCP 调用失败时的降级。
   *
   * @param incident 当前故障事件
   * @param logs 日志列表（可能也是 fallback 数据）
   * @param codeHints 代码线索列表
   * @param tickets 历史工单列表
   * @return 模拟的诊断报告 Map
   */
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

  /**
   * 生成诊断报告的 fallback Markdown 内容。
   *
   * @param incident 当前故障事件
   * @param logs 关键日志列表
   * @return Markdown 格式的诊断摘要
   */
  private String fallbackReportMarkdown(Incident incident, List<String> logs) {
    return "# 诊断摘要\n\n" + incident.title() + "\n\n## 关键日志\n\n- " + String.join("\n- ", logs);
  }

  /**
   * 将 null 值转换为空字符串。
   *
   * @param value 原始字符串，可能为 null
   * @return 原始字符串，若为 null 则返回空字符串
   */
  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * 将 null 或空白字符串替换为默认值。
   *
   * @param value 原始字符串，可能为 null 或空白
   * @param defaultValue 默认值
   * @return 原始字符串（若非空白），否则返回默认值
   */
  private String valueOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
