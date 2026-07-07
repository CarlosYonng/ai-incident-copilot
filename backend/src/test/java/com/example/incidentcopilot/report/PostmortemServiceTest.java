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

/**
 * PostmortemService 的单元测试。
 * 测试故障事后报告（Postmortem）的生成逻辑：包括根因分析、行动项生成以及报告持久化。
 */
class PostmortemServiceTest {

  /**
   * 验证 generate 方法能正确生成事后报告：
   * - 摘要中包含故障标题
   * - 根因分析中包含已执行的离线处置方案及其风险等级
   * - 行动项列表包含 3 项
   * - 报告内容包含 "Incident Postmortem"
   * - 报告通过 upsert 正确持久化
   */
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

  /**
   * 构造一个支付超时的故障单对象，状态为 RECOVERING。
   *
   * @return Incident 实例
   */
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

  /**
   * 构造一个故障处理工作流实例。
   *
   * @return WorkflowInstance 实例
   */
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

  /**
   * 构造一个已离线执行的处置方案（开启支付回调延迟重试）。
   *
   * @return ActionProposal 实例
   */
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
