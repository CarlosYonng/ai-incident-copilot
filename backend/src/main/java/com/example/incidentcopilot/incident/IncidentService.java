package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.metrics.MockMetricsService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
  private final IncidentRepository incidentRepository;
  private final MockMetricsService mockMetricsService;

  public IncidentService(IncidentRepository incidentRepository, MockMetricsService mockMetricsService) {
    this.incidentRepository = incidentRepository;
    this.mockMetricsService = mockMetricsService;
  }

  @Transactional
  public IncidentResponse create(IncidentCreateRequest request) {
    Incident incident = incidentRepository.create(request);
    mockMetricsService.recordInitialSnapshot(incident);
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
        mockMetricsService.findLatestSnapshots(id, 10)
    );
  }

  @Transactional
  public IncidentResponse close(Long id, IncidentCloseRequest request) {
    Incident incident = findRequired(id);
    mockMetricsService.recordRecoveredSnapshot(incident);
    return IncidentResponse.from(incidentRepository.close(id));
  }

  public Incident findRequired(Long id) {
    return incidentRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Incident not found: " + id));
  }
}
