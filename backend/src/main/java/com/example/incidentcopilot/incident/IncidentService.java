package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

  private final IncidentRepository incidentRepository;
  private final IncidentMetricsService incidentMetricsService;

  public IncidentService(IncidentRepository incidentRepository, IncidentMetricsService incidentMetricsService) {
    this.incidentRepository = incidentRepository;
    this.incidentMetricsService = incidentMetricsService;
  }

  @Transactional
  public IncidentResponse create(IncidentCreateRequest request) {
    Incident incident = incidentRepository.create(request);
    incidentMetricsService.recordInitialSnapshot(incident);
    log.info(
        "incident_created id={} incidentNo={} service={} source={}",
        incident.id(),
        incident.incidentNo(),
        incident.serviceName(),
        incident.source()
    );
    return IncidentResponse.from(incident);
  }

  @Transactional
  public IncidentResponse createFromAlert(
      IncidentCreateRequest request,
      BigDecimal errorRate,
      Integer p95Latency,
      Integer qps
  ) {
    Incident incident = incidentRepository.create(request);
    incidentMetricsService.recordAlertSnapshot(incident, errorRate, p95Latency, qps);
    log.info(
        "incident_created_from_alert id={} incidentNo={} service={} source={}",
        incident.id(),
        incident.incidentNo(),
        incident.serviceName(),
        incident.source()
    );
    return IncidentResponse.from(incident);
  }

  public List<IncidentResponse> list(
      String status,
      String serviceName,
      String severity,
      int page,
      int size
  ) {
    return incidentRepository.findAll(status, serviceName, severity, page, size).stream()
        .map(IncidentResponse::from)
        .toList();
  }

  public IncidentResponse get(Long id) {
    return IncidentResponse.from(findRequired(id));
  }

  public IncidentDetailResponse getDetail(Long id) {
    Incident incident = findRequired(id);
    return new IncidentDetailResponse(
        IncidentResponse.from(incident),
        incidentMetricsService.findLatestSnapshots(id, 10)
    );
  }

  @Transactional
  public IncidentResponse close(Long id, IncidentCloseRequest request) {
    Incident incident = findRequired(id);
    incidentMetricsService.recordRecoveredSnapshot(incident);
    Incident closed = incidentRepository.close(id);
    log.info("incident_closed id={} incidentNo={} closedBy={}", id, closed.incidentNo(), request.closedBy());
    return IncidentResponse.from(closed);
  }

  public Incident findRequired(Long id) {
    return incidentRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Incident not found: " + id));
  }
}
