package com.example.incidentcopilot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.action.ActionProposalRepository;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.workflow.WorkflowInstance;
import com.example.incidentcopilot.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmortemServiceTest {

  @Test
  void generateIncludesOfflineExecutedActionInRootCauseAndPersistsReport() {
    PostmortemRepository postmortemRepository = mock(PostmortemRepository.class);
    IncidentService incidentService = mock(IncidentService.class);
    WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    ActionProposalRepository actionRepository = mock(ActionProposalRepository.class);
    Incident incident = paymentIncident();
    when(incidentService.findRequired(1L)).thenReturn(incident);
    when(workflowRepository.findByIncident(1L)).thenReturn(List.of(workflowInstance()));
    when(workflowRepository.findNodeExecutions(10L)).thenReturn(List.of());
    when(actionRepository.findExecutedByIncident(1L)).thenReturn(List.of(executedAction()));
    PostmortemService service = new PostmortemService(
        postmortemRepository,
        incidentService,
        workflowRepository,
        actionRepository,
        new JdbcJson(new ObjectMapper())
    );

    PostmortemResponse response = service.generate(1L);

    assertThat(response.summary()).contains("payment-service 支付回调超时");
    assertThat(response.rootCause()).contains("本次采用 开启支付回调延迟重试");
    assertThat(response.rootCause()).contains("中风险");
    assertThat(response.actionItems()).hasSize(3);
    assertThat(response.reportContent()).contains("Incident Postmortem");
    verify(postmortemRepository).upsert(
        eq(1L),
        eq(response.summary()),
        eq(response.rootCause()),
        eq(response.impact()),
        anyString(),
        anyString(),
        anyString(),
        eq(response.reportContent())
    );
  }

  private Incident paymentIncident() {
    return new Incident(
        1L,
        "INC-20260616-0001",
        "payment-service 支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "P1",
        "RECOVERING",
        "DEMO",
        "trace-payment-timeout-001",
        "TimeoutError",
        "5 分钟内 500 错误率升高",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }

  private WorkflowInstance workflowInstance() {
    return new WorkflowInstance(
        10L,
        1L,
        "IncidentHandlingWorkflow",
        "WAITING_APPROVAL",
        null,
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now()
    );
  }

  private ActionProposal executedAction() {
    return new ActionProposal(
        200L,
        1L,
        10L,
        "开启支付回调延迟重试",
        "ENABLE_DELAYED_RETRY",
        "MEDIUM",
        "reason",
        "{}",
        "impact",
        "precheck",
        true,
        "OFFLINE_EXECUTED",
        LocalDateTime.now(),
        LocalDateTime.now()
    );
  }
}
