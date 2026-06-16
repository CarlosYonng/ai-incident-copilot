package com.example.incidentcopilot.workflow.nodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SeverityClassifierNodeTest {

  @Test
  void classifiesPaymentTimeoutAsP1() {
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    SeverityClassifierNode node = new SeverityClassifierNode(incidentRepository);
    WorkflowContext context = new WorkflowContext(10L, incident(
        "payment-service 支付回调超时",
        "payment-service",
        "TimeoutError",
        "p95 延迟升至 3200ms"
    ));

    NodeResult result = node.execute(context);

    assertThat(result.output()).containsEntry("severity", "P1");
    assertThat(context.getString("severity", "")).isEqualTo("P1");
    verify(incidentRepository).updateSeverity(1L, "P1");
  }

  @Test
  void classifiesNullPointerAsP2() {
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    SeverityClassifierNode node = new SeverityClassifierNode(incidentRepository);
    WorkflowContext context = new WorkflowContext(10L, incident(
        "order-service 创建订单空指针",
        "order-service",
        "NullPointerException",
        "创建订单时 userProfile 为空导致 500 错误"
    ));

    NodeResult result = node.execute(context);

    assertThat(result.output()).containsEntry("severity", "P2");
    verify(incidentRepository).updateSeverity(1L, "P2");
  }

  @Test
  void classifiesLowSignalIncidentAsP3() {
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    SeverityClassifierNode node = new SeverityClassifierNode(incidentRepository);
    WorkflowContext context = new WorkflowContext(10L, incident(
        "profile-service warning",
        "profile-service",
        "Warning",
        "minor warning"
    ));

    NodeResult result = node.execute(context);

    assertThat(result.output()).containsEntry("severity", "P3");
    verify(incidentRepository).updateSeverity(1L, "P3");
  }

  private Incident incident(String title, String serviceName, String exceptionType, String summary) {
    return new Incident(
        1L,
        "INC-20260616-0001",
        title,
        serviceName,
        "/api/test",
        "P2",
        "OPEN",
        "DEMO",
        "trace-001",
        exceptionType,
        summary,
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }
}
