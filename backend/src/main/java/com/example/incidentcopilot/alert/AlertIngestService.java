package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentCreateRequest;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警入站服务。
 *
 * <p>核心业务逻辑：接收告警入站请求后按顺序执行幂等去重、持久化、阈值过滤、
 * 关联活跃故障单或创建新故障单，并记录指标快照。</p>
 */
@Service
public class AlertIngestService {
  /** 错误率阈值 2%，超过此值认为告警可处理。 */
  private static final BigDecimal ERROR_RATE_THRESHOLD = new BigDecimal("0.0200");
  /** P95 延迟阈值 1000ms，超过此值认为告警可处理。 */
  private static final int P95_LATENCY_THRESHOLD_MS = 1000;
  /** 受影响请求数阈值 10，超过此值认为告警可处理。 */
  private static final int AFFECTED_REQUEST_THRESHOLD = 10;

  /** 告警事件数据访问层。 */
  private final AlertEventRepository alertEventRepository;
  /** 故障单数据访问层。 */
  private final IncidentRepository incidentRepository;
  /** 故障单业务服务。 */
  private final IncidentService incidentService;
  /** 故障单指标记录服务。 */
  private final IncidentMetricsService incidentMetricsService;
  /** JSON 序列化工具，用于将 Map 转换为 JSON 字符串。 */
  private final JdbcJson jdbcJson;

  /**
   * 构造告警入站服务。
   *
   * @param alertEventRepository   告警事件仓库
   * @param incidentRepository     故障单仓库
   * @param incidentService        故障单服务
   * @param incidentMetricsService 指标记录服务
   * @param jdbcJson               JSON 序列化工具
   */
  public AlertIngestService(
      AlertEventRepository alertEventRepository,
      IncidentRepository incidentRepository,
      IncidentService incidentService,
      IncidentMetricsService incidentMetricsService,
      JdbcJson jdbcJson
  ) {
    this.alertEventRepository = alertEventRepository;
    this.incidentRepository = incidentRepository;
    this.incidentService = incidentService;
    this.incidentMetricsService = incidentMetricsService;
    this.jdbcJson = jdbcJson;
  }

  /**
   * 执行告警入站处理。
   *
   * <p>处理流程：
   * <ol>
   *   <li>按 eventId 幂等去重，已有记录直接返回；</li>
   *   <li>原始 payload 落库持久化；</li>
   *   <li>阈值过滤，未达阈值标记为忽略；</li>
   *   <li>尝试按服务名/endpoint/traceId 关联活跃故障单；</li>
   *   <li>无法关联时创建新的故障单。</li>
   * </ol>
   * </p>
   *
   * @param request 告警入站请求
   * @return 告警入站处理结果，包含事件和故障单信息
   */
  @Transactional
  public AlertIngestResult ingest(AlertIngestRequest request) {
    // 入站告警处理顺序：
    // 1. 按 eventId 幂等去重；2. 原始 payload 落库；3. 阈值过滤；
    // 4. 尝试关联活跃 Incident；5. 无法关联时新建 Incident 并等待编排。
    var existingEvent = alertEventRepository.findByEventId(request.eventId());
    if (existingEvent.isPresent()) {
      AlertEvent event = existingEvent.get();
      IncidentResponse incident = event.incidentId() == null
          ? null
          : IncidentResponse.from(incidentService.findRequired(event.incidentId()));
      return new AlertIngestResult(event, incident);
    }

    AlertEvent event = alertEventRepository.create(request, rawPayloadJson(request));
    if (!isActionable(request)) {
      AlertEvent ignored = alertEventRepository.markIgnored(event.id(), ignoreReason(request));
      return new AlertIngestResult(ignored, null);
    }

    var correlated = incidentRepository.findActiveByCorrelation(
        request.serviceName(),
        request.endpoint(),
        request.traceId()
    );
    if (correlated.isPresent()) {
      Incident incident = correlated.get();
      incidentMetricsService.recordAlertSnapshot(
          incident,
          errorRateOrDefault(request.errorRate()),
          valueOrDefault(request.p95Latency(), P95_LATENCY_THRESHOLD_MS),
          valueOrDefault(request.qps(), 1000)
      );
      AlertEvent correlatedEvent = alertEventRepository.markCorrelated(
          event.id(),
          incident.id(),
          "按服务名以及 traceId 或 endpoint 命中活跃故障单。"
      );
      return new AlertIngestResult(correlatedEvent, IncidentResponse.from(incident));
    }

    IncidentResponse incident = incidentService.createFromAlert(
        new IncidentCreateRequest(
            title(request),
            request.serviceName(),
            request.endpoint(),
            request.source(),
            request.traceId(),
            request.exceptionType(),
            summary(request)
        ),
        errorRateOrDefault(request.errorRate()),
        valueOrDefault(request.p95Latency(), P95_LATENCY_THRESHOLD_MS),
        valueOrDefault(request.qps(), 1000)
    );
    AlertEvent created = alertEventRepository.markIncidentCreated(
        event.id(),
        incident.id(),
        "告警超过故障阈值，系统创建新的故障单。"
    );
    return new AlertIngestResult(created, incident);
  }

  /**
   * 查询指定故障单关联的所有告警事件响应。
   *
   * <p>会先验证故障单存在性，再返回关联的告警事件列表。</p>
   *
   * @param incidentId 故障单 ID
   * @return 告警事件响应列表
   */
  public List<AlertEventResponse> listByIncident(Long incidentId) {
    incidentService.findRequired(incidentId);
    return alertEventRepository.findByIncident(incidentId).stream()
        .map(AlertEventResponse::from)
        .toList();
  }

  /**
   * 判断告警是否达到可处理阈值。
   *
   * <p>MVP 阈值规则：错误率 >= 2%、P95 延迟 >= 1000ms、受影响请求数 >= 10
   * 三者满足其一，或同时携带异常类型和摘要，即认为需要进入故障单处理。</p>
   *
   * @param request 告警入站请求
   * @return true 如果告警超过了任意阈值需要处理
   */
  private boolean isActionable(AlertIngestRequest request) {
    // MVP 阈值规则：错误率、p95 延迟、影响请求数任一超过阈值，或同时携带异常类型和摘要，即认为需要进入 Incident 处理。
    if (request.errorRate() != null && request.errorRate().compareTo(ERROR_RATE_THRESHOLD) >= 0) {
      return true;
    }
    if (request.p95Latency() != null && request.p95Latency() >= P95_LATENCY_THRESHOLD_MS) {
      return true;
    }
    if (request.affectedRequests() != null && request.affectedRequests() >= AFFECTED_REQUEST_THRESHOLD) {
      return true;
    }
    return hasText(request.exceptionType()) && hasText(request.summary());
  }

  /**
   * 生成告警被忽略的原因说明。
   *
   * @param request 告警入站请求
   * @return 忽略原因字符串
   */
  private String ignoreReason(AlertIngestRequest request) {
    return "告警未达到故障阈值：错误率 >= 2%、p95 >= 1000ms、影响请求数 >= 10，或异常类型加摘要。";
  }

  /**
   * 生成故障单标题。
   *
   * @param request 告警入站请求
   * @return 故障单标题字符串，格式为 "服务名 信号名称"
   */
  private String title(AlertIngestRequest request) {
    return request.serviceName() + " " + request.signalName();
  }

  /**
   * 生成故障单摘要，包含总结和关键指标。
   *
   * @param request 告警入站请求
   * @return 摘要字符串
   */
  private String summary(AlertIngestRequest request) {
    return "%s | errorRate=%s, p95=%sms, qps=%s, affectedRequests=%s".formatted(
        valueOrDefault(request.summary(), "上游告警事件"),
        request.errorRate() == null ? "unknown" : request.errorRate(),
        request.p95Latency() == null ? "unknown" : request.p95Latency(),
        request.qps() == null ? "unknown" : request.qps(),
        request.affectedRequests() == null ? "unknown" : request.affectedRequests()
    );
  }

  /**
   * 将请求中的原始负载 Map 序列化为 JSON 字符串。
   *
   * @param request 告警入站请求
   * @return JSON 字符串
   */
  private String rawPayloadJson(AlertIngestRequest request) {
    Map<String, Object> payload = request.rawPayload() == null ? Map.of() : request.rawPayload();
    return jdbcJson.stringify(payload);
  }

  /**
   * 获取错误率值，null 时使用阈值默认值。
   *
   * @param value 原始错误率值
   * @return 错误率，不为 null
   */
  private BigDecimal errorRateOrDefault(BigDecimal value) {
    return value == null ? ERROR_RATE_THRESHOLD : value;
  }

  /**
   * 获取整型值，null 时使用默认值。
   *
   * @param value        原始整型值
   * @param defaultValue 默认值
   * @return 非 null 的整型值
   */
  private int valueOrDefault(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * 获取字符串值，空串或 null 时使用默认值。
   *
   * @param value        原始字符串
   * @param defaultValue 默认值
   * @return 非空字符串
   */
  private String valueOrDefault(String value, String defaultValue) {
    return hasText(value) ? value : defaultValue;
  }

  /**
   * 判断字符串是否不为 null 且不包含空白。
   *
   * @param value 待检查的字符串
   * @return true 如果字符串非空
   */
  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
