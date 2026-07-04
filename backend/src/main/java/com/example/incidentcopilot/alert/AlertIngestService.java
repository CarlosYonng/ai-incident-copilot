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

@Service
public class AlertIngestService {
  private static final BigDecimal ERROR_RATE_THRESHOLD = new BigDecimal("0.0200");
  private static final int P95_LATENCY_THRESHOLD_MS = 1000;
  private static final int AFFECTED_REQUEST_THRESHOLD = 10;

  private final AlertEventRepository alertEventRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentService incidentService;
  private final IncidentMetricsService incidentMetricsService;
  private final JdbcJson jdbcJson;

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

  @Transactional
  public AlertIngestResult ingest(AlertIngestRequest request) {
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
          "Matched active incident by service plus traceId or endpoint."
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
        "Actionable alert event crossed incident thresholds and opened a new incident."
    );
    return new AlertIngestResult(created, incident);
  }

  public List<AlertEventResponse> listByIncident(Long incidentId) {
    incidentService.findRequired(incidentId);
    return alertEventRepository.findByIncident(incidentId).stream()
        .map(AlertEventResponse::from)
        .toList();
  }

  private boolean isActionable(AlertIngestRequest request) {
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

  private String ignoreReason(AlertIngestRequest request) {
    return "Alert did not cross thresholds: errorRate >= 2%, p95 >= 1000ms, affectedRequests >= 10, or exception with summary.";
  }

  private String title(AlertIngestRequest request) {
    return request.serviceName() + " " + request.signalName();
  }

  private String summary(AlertIngestRequest request) {
    return "%s | errorRate=%s, p95=%sms, qps=%s, affectedRequests=%s".formatted(
        valueOrDefault(request.summary(), "upstream alert event"),
        request.errorRate() == null ? "unknown" : request.errorRate(),
        request.p95Latency() == null ? "unknown" : request.p95Latency(),
        request.qps() == null ? "unknown" : request.qps(),
        request.affectedRequests() == null ? "unknown" : request.affectedRequests()
    );
  }

  private String rawPayloadJson(AlertIngestRequest request) {
    Map<String, Object> payload = request.rawPayload() == null ? Map.of() : request.rawPayload();
    return jdbcJson.stringify(payload);
  }

  private BigDecimal errorRateOrDefault(BigDecimal value) {
    return value == null ? ERROR_RATE_THRESHOLD : value;
  }

  private int valueOrDefault(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }

  private String valueOrDefault(String value, String defaultValue) {
    return hasText(value) ? value : defaultValue;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
