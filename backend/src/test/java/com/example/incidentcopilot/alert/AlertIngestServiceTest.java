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

class AlertIngestServiceTest {

  @Test
  void ingestCreatesIncidentWhenAlertCrossesThreshold() {
    AlertEventRepository alertRepository = mock(AlertEventRepository.class);
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    IncidentService incidentService = mock(IncidentService.class);
    AlertIngestRequest request = paymentAlert(new BigDecimal("0.0820"), 3200);
    AlertEvent received = event(10L, request, null, "RECEIVED", null);
    AlertEvent created = event(10L, request, 1L, "INCIDENT_CREATED", "created");
    when(alertRepository.findByEventId(request.eventId())).thenReturn(Optional.empty());
    when(alertRepository.create(any(AlertIngestRequest.class), anyString())).thenReturn(received);
    when(incidentRepository.findActiveByCorrelation("payment-service", "/api/payment/callback", "trace-1"))
        .thenReturn(Optional.empty());
    when(incidentService.createFromAlert(any(IncidentCreateRequest.class), any(), any(), any()))
        .thenReturn(IncidentResponse.from(paymentIncident()));
    when(alertRepository.markIncidentCreated(10L, 1L, "Actionable alert event crossed incident thresholds and opened a new incident."))
        .thenReturn(created);
    AlertIngestService service = newService(alertRepository, incidentRepository, incidentService, mock(IncidentMetricsService.class));

    AlertIngestResult result = service.ingest(request);

    assertThat(result.event().status()).isEqualTo("INCIDENT_CREATED");
    assertThat(result.incident().id()).isEqualTo(1L);
    verify(incidentService).createFromAlert(any(IncidentCreateRequest.class), any(), any(), any());
  }

  @Test
  void ingestIgnoresLowSignalAlert() {
    AlertEventRepository alertRepository = mock(AlertEventRepository.class);
    AlertIngestRequest request = paymentAlert(new BigDecimal("0.0010"), 180);
    AlertEvent received = event(11L, request, null, "RECEIVED", null);
    AlertEvent ignored = event(11L, request, null, "IGNORED", "below threshold");
    when(alertRepository.findByEventId(request.eventId())).thenReturn(Optional.empty());
    when(alertRepository.create(any(AlertIngestRequest.class), anyString())).thenReturn(received);
    when(alertRepository.markIgnored(11L, "Alert did not cross thresholds: errorRate >= 2%, p95 >= 1000ms, affectedRequests >= 10, or exception with summary."))
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
