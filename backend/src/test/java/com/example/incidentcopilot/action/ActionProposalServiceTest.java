package com.example.incidentcopilot.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import com.example.incidentcopilot.runbook.RunbookDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActionProposalServiceTest {

  @Test
  void generateDefaultsCreatesLowMediumAndHighRiskProposals() {
    ActionProposalRepository actionRepository = mock(ActionProposalRepository.class);
    when(actionRepository.findByWorkflow(100L)).thenReturn(List.of());
    when(actionRepository.create(
        anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyBoolean(), anyString()
    )).thenAnswer(invocation -> proposal(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(2),
        invocation.getArgument(3),
        invocation.getArgument(4),
        invocation.getArgument(10),
        invocation.getArgument(9)
    ));
    ActionProposalService service = newService(actionRepository, mock(IncidentRepository.class), mock(IncidentMetricsService.class));

    List<ActionProposal> proposals = service.generateDefaults(
        paymentIncident(),
        100L,
        diagnosisEvidence(),
        List.of(new RunbookDocument("payment-callback-timeout.md", "支付回调超时", "", 8, "")),
        "P1"
    );

    assertThat(proposals).hasSize(3);
    assertThat(proposals).extracting(ActionProposal::riskLevel).containsExactly("LOW", "MEDIUM", "HIGH");
    assertThat(proposals.get(1).title()).isEqualTo("开启支付回调延迟重试");
    assertThat(proposals.get(1).actionType()).isEqualTo("ENABLE_DELAYED_RETRY");
    assertThat(proposals.get(1).requiresApproval()).isTrue();
  }

  @Test
  void generateDefaultsCreatesPortfolioSpecificMediumRiskProposal() {
    ActionProposalRepository actionRepository = mock(ActionProposalRepository.class);
    when(actionRepository.findByWorkflow(101L)).thenReturn(List.of());
    when(actionRepository.create(
        anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyBoolean(), anyString()
    )).thenAnswer(invocation -> proposal(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(2),
        invocation.getArgument(3),
        invocation.getArgument(4),
        invocation.getArgument(10),
        invocation.getArgument(9)
    ));
    ActionProposalService service = newService(actionRepository, mock(IncidentRepository.class), mock(IncidentMetricsService.class));

    List<ActionProposal> proposals = service.generateDefaults(
        portfolioQdrantIncident(),
        101L,
        diagnosisEvidence(),
        List.of(new RunbookDocument("portfolio-qdrant-unavailable.md", "Qdrant 向量库不可用", "", 8, "")),
        "P1"
    );

    assertThat(proposals).hasSize(3);
    assertThat(proposals.get(1).title()).isEqualTo("启用 RAG 检索兜底并暂停批量向量写入");
    assertThat(proposals.get(1).actionType()).isEqualTo("ENABLE_RAG_RETRIEVAL_FALLBACK");
  }


  @Test
  void markOfflineExecutedRecordsApprovalActionRecordAndRecoveringMetrics() {
    ActionProposalRepository actionRepository = mock(ActionProposalRepository.class);
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    IncidentMetricsService metricsService = mock(IncidentMetricsService.class);
    ActionProposal pending = proposal(1L, 100L, "开启支付回调延迟重试", "ENABLE_DELAYED_RETRY", "MEDIUM", "PENDING", true);
    ActionProposal executed = proposal(1L, 100L, "开启支付回调延迟重试", "ENABLE_DELAYED_RETRY", "MEDIUM", "OFFLINE_EXECUTED", true);
    when(actionRepository.findById(200L)).thenReturn(Optional.of(pending), Optional.of(executed));
    when(actionRepository.findExecutedByIncident(1L)).thenReturn(List.of());
    Incident recoveringIncident = paymentIncident("RECOVERING");
    when(incidentRepository.updateStatus(1L, "RECOVERING")).thenReturn(recoveringIncident);
    ActionProposalService service = newService(actionRepository, incidentRepository, metricsService);

    ActionProposalResponse response = service.markOfflineExecuted(
        200L,
        new MarkOfflineExecutedRequest("sre-demo", "offline action done")
    );

    assertThat(response.status()).isEqualTo("OFFLINE_EXECUTED");
    verify(actionRepository).createApproval(200L, 1L, "MARK_OFFLINE_EXECUTED", "offline action done", "sre-demo");
    verify(actionRepository).createActionRecord(1L, 200L, "ENABLE_DELAYED_RETRY", "sre-demo", "SUCCESS", "offline action done");
    verify(actionRepository).updateStatus(200L, "OFFLINE_EXECUTED");
    verify(actionRepository).markUnselectedActions(1L, 200L);
    verify(metricsService).recordRecoveringSnapshot(recoveringIncident, pending);
  }

  @Test
  void recordResultRejectsSecondSelectedActionForSameIncident() {
    ActionProposalRepository actionRepository = mock(ActionProposalRepository.class);
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    IncidentMetricsService metricsService = mock(IncidentMetricsService.class);
    ActionProposal highRisk = proposal(1L, 100L, "回滚最近发布或切换生产配置", "ROLLBACK_OR_CONFIG_CHANGE", "HIGH", "PENDING", true);
    ActionProposal selected = proposal(1L, 100L, "开启支付回调延迟重试", "ENABLE_DELAYED_RETRY", "MEDIUM", "OFFLINE_EXECUTED", true);
    when(actionRepository.findById(301L)).thenReturn(Optional.of(highRisk));
    when(actionRepository.findExecutedByIncident(1L)).thenReturn(List.of(selected));
    ActionProposalService service = newService(actionRepository, incidentRepository, metricsService);

    assertThatThrownBy(() -> service.recordResult(
        301L,
        new MarkOfflineExecutedRequest("sre-demo", "try another action")
    )).hasMessageContaining("Incident already selected action");
  }

  private ActionProposalService newService(
      ActionProposalRepository actionRepository,
      IncidentRepository incidentRepository,
      IncidentMetricsService metricsService
  ) {
    return new ActionProposalService(
        actionRepository,
        incidentRepository,
        metricsService,
        new JdbcJson(new ObjectMapper())
    );
  }

  private ActionProposal proposal(
      Long incidentId,
      Long workflowInstanceId,
      String title,
      String actionType,
      String riskLevel,
      String status,
      boolean requiresApproval
  ) {
    return new ActionProposal(
        "LOW".equals(riskLevel) ? 101L : "MEDIUM".equals(riskLevel) ? 200L : 301L,
        incidentId,
        workflowInstanceId,
        title,
        actionType,
        riskLevel,
        "reason",
        "{}",
        "impact",
        "precheck",
        requiresApproval,
        status,
        LocalDateTime.now(),
        LocalDateTime.now()
    );
  }

  private Incident paymentIncident() {
    return paymentIncident("WAITING_APPROVAL");
  }

  private Incident paymentIncident(String status) {
    return new Incident(
        1L,
        "INC-20260616-0001",
        "payment-service 支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "P1",
        status,
        "DEMO",
        "trace-payment-timeout-001",
        "TimeoutError",
        "5 分钟内 500 错误率升高",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }

  private Incident portfolioQdrantIncident() {
    return new Incident(
        2L,
        "INC-20260704-0002",
        "ai-agent-portfolio Qdrant 向量检索超时",
        "ai-agent-portfolio",
        "/api/chat/stream",
        "P1",
        "WAITING_APPROVAL",
        "GRAFANA",
        "trace-qdrant-timeout-001",
        "QdrantUnavailable",
        "RAG 检索调用 Qdrant timeout，向量召回失败",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }


  private DiagnosisEvidence diagnosisEvidence() {
    return new DiagnosisEvidence(
        "支付链路超时",
        List.of("TimeoutError"),
        List.of("PaymentCallbackHandler"),
        List.of("similar incident"),
        "report-1",
        "# report",
        false
    );
  }
}
