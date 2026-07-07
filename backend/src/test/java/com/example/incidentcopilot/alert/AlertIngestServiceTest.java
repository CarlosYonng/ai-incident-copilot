package com.example.incidentcopilot.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentCreateRequest;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * AlertIngestService 的单元测试。
 * 测试告警接入逻辑：根据告警指标（错误率、P95 延迟、请求量等）判断是否达到故障阈值，
 * 并相应地创建故障单或忽略该告警。
 */
class AlertIngestServiceTest {

  /**
   * 验证当告警的错误率和延迟超过故障阈值时，ingest 方法能正确创建故障单，
   * 并将告警事件标记为 INCIDENT_CREATED。
   */
  @Test
  void ingestCreatesIncidentWhenAlertCrossesThreshold() {
    AlertEventRepository alertRepository = mock(AlertEventRepository.class);
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    IncidentService incidentService = mock(IncidentService.class);
    AlertIngestRequest request = paymentAlert(new BigDecimal("0.0820"), 3200);
    AlertEvent received = event(10L, request, null, "RECEIVED", null);
    AlertEvent created = event(10L, request, 1L, "INCIDENT_CREATED", "告警超过故障阈值");
    when(alertRepository.findByEventId(request.eventId())).thenReturn(Optional.empty());
    when(alertRepository.create(any(AlertIngestRequest.class), anyString())).thenReturn(received);
    when(incidentRepository.findActiveByCorrelation("payment-service", "/api/payment/callback", "trace-1"))
        .thenReturn(Optional.empty());
    when(incidentService.createFromAlert(any(IncidentCreateRequest.class), any(), any(), any()))
        .thenReturn(IncidentResponse.from(paymentIncident()));
    when(alertRepository.markIncidentCreated(10L, 1L, "告警超过故障阈值，系统创建新的故障单。"))
        .thenReturn(created);
    AlertIngestService service = newService(alertRepository, incidentRepository, incidentService, mock(IncidentMetricsService.class));

    AlertIngestResult result = service.ingest(request);

    assertThat(result.event().status()).isEqualTo("INCIDENT_CREATED");
    assertThat(result.incident().id()).isEqualTo(1L);
    verify(incidentService).createFromAlert(any(IncidentCreateRequest.class), any(), any(), any());
  }

  /**
   * 验证当告警信号较弱（低错误率、低延迟）时，ingest 方法不会创建故障单，
   * 直接将告警事件标记为 IGNORED，并且返回的 incident 为 null。
   */
  @Test
  void ingestIgnoresLowSignalAlert() {
    AlertEventRepository alertRepository = mock(AlertEventRepository.class);
    AlertIngestRequest request = paymentAlert(new BigDecimal("0.0010"), 180);
    AlertEvent received = event(11L, request, null, "RECEIVED", null);
    AlertEvent ignored = event(11L, request, null, "IGNORED", "未达到阈值");
    when(alertRepository.findByEventId(request.eventId())).thenReturn(Optional.empty());
    when(alertRepository.create(any(AlertIngestRequest.class), anyString())).thenReturn(received);
    when(alertRepository.markIgnored(11L, "告警未达到故障阈值：错误率 >= 2%、p95 >= 1000ms、影响请求数 >= 10，或异常类型加摘要。"))
        .thenReturn(ignored);
    AlertIngestService service = newService(
        alertRepository,
        mock(IncidentRepository.class),
        mock(IncidentService.class),
        mock(IncidentMetricsService.class)
    );

    AlertIngestResult result = service.ingest(request);

    assertThat(result.event().status()).isEqualTo("IGNORED");
    assertThat(result.incident()).isNull();
  }

  /**
   * 创建 AlertIngestService 实例，注入 Mock 依赖。
   *
   * @param alertRepository     AlertEventRepository Mock
   * @param incidentRepository  IncidentRepository Mock
   * @param incidentService     IncidentService Mock
   * @param metricsService      IncidentMetricsService Mock
   * @return AlertIngestService 实例
   */
  private AlertIngestService newService(
      AlertEventRepository alertRepository,
      IncidentRepository incidentRepository,
      IncidentService incidentService,
      IncidentMetricsService metricsService
  ) {
    return new AlertIngestService(
        alertRepository,
        incidentRepository,
        incidentService,
        metricsService,
        new JdbcJson(new ObjectMapper())
    );
  }

  /**
   * 构造一个支付回调的告警请求参数。
   *
   * @param errorRate  错误率
   * @param p95Latency P95 延迟（ms）
   * @return AlertIngestRequest 实例
   */
  private AlertIngestRequest paymentAlert(BigDecimal errorRate, Integer p95Latency) {
    return new AlertIngestRequest(
        "event-1",
        "payment-gateway-apm",
        "支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "trace-1",
        null,
        null,
        errorRate,
        p95Latency,
        1260,
        0,
        "P1",
        Map.of("businessOperation", "payment_callback"),
        true
    );
  }

  /**
   * 构造一个告警事件对象。
   *
   * @param id          事件 ID
   * @param request      告警请求参数
   * @param incidentId   关联故障单 ID（可能为 null）
   * @param status       事件状态
   * @param reason       状态原因说明
   * @return AlertEvent 实例
   */
  private AlertEvent event(
      Long id,
      AlertIngestRequest request,
      Long incidentId,
      String status,
      String reason
  ) {
    return new AlertEvent(
        id,
        request.eventId(),
        incidentId,
        request.source(),
        request.signalName(),
        request.serviceName(),
        request.endpoint(),
        request.traceId(),
        request.exceptionType(),
        request.summary(),
        request.errorRate(),
        request.p95Latency(),
        request.qps(),
        request.affectedRequests(),
        request.severityHint(),
        "{}",
        status,
        reason,
        LocalDateTime.now(),
        LocalDateTime.now()
    );
  }

  /**
   * 构造一个支付超时的故障单对象。
   *
   * @return Incident 实例
   */
  private Incident paymentIncident() {
    return new Incident(
        1L,
        "INC-20260704-0001",
        "payment-service 支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "P2",
        "OPEN",
        "payment-gateway-apm",
        "trace-1",
        "TimeoutError",
        "payment callback timeout",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }
}
