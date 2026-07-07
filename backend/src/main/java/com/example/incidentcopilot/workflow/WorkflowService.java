package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.DomainConstants.IncidentStatus;
import com.example.incidentcopilot.common.DomainConstants.WorkflowStatus;
import com.example.incidentcopilot.audit.ToolCallLogResponse;
import com.example.incidentcopilot.audit.ToolCallLogger;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.incident.IncidentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工作流应用服务。
 *
 * <p>负责启动故障处理编排、查询节点时间线和工具调用审计；真正的节点执行由工作流引擎完成。</p>
 */
@Service
public class WorkflowService {
  private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

  /** 事件应用服务 */
  private final IncidentService incidentService;
  /** 事件持久化仓库 */
  private final IncidentRepository incidentRepository;
  /** 工作流持久化仓库 */
  private final WorkflowRepository workflowRepository;
  /** 工作流引擎 */
  private final WorkflowEngine workflowEngine;
  /** MCP 工具调用日志记录器 */
  private final ToolCallLogger toolCallLogger;

  /**
   * 构造工作流服务。
   *
   * @param incidentService     事件服务
   * @param incidentRepository  事件仓库
   * @param workflowRepository  工作流仓库
   * @param workflowEngine      工作流引擎
   * @param toolCallLogger      MCP 工具调用日志记录器
   */
  public WorkflowService(
      IncidentService incidentService,
      IncidentRepository incidentRepository,
      WorkflowRepository workflowRepository,
      WorkflowEngine workflowEngine,
      ToolCallLogger toolCallLogger
  ) {
    this.incidentService = incidentService;
    this.incidentRepository = incidentRepository;
    this.workflowRepository = workflowRepository;
    this.workflowEngine = workflowEngine;
    this.toolCallLogger = toolCallLogger;
  }

  /**
   * 启动指定事件的故障处理工作流。
   * <p>
   * 检查事件状态和已有工作流状态，防止重复触发；
   * 创建新工作流实例后交由 {@link WorkflowEngine} 依次执行各节点。
   * </p>
   *
   * @param incidentId 事件 ID
   * @return 执行完毕的工作流响应体
   * @throws ApiException 事件已关闭、事件不存在时抛出
   */
  public WorkflowResponse startIncidentWorkflow(Long incidentId) {
    Incident incident = incidentService.findRequired(incidentId);
    if (IncidentStatus.CLOSED.equals(incident.status())) {
      throw ApiException.conflict("已关闭的故障单不能启动工作流: " + incidentId);
    }
    var latestWorkflow = workflowRepository.findLatestByIncident(incidentId);
    if (latestWorkflow.isPresent() && isStillAwaitingOperator(latestWorkflow.get().status())) {
      // 防止重复点击或重复告警导致同一故障单反复生成处置方案。
      return WorkflowResponse.from(latestWorkflow.get());
    }
    incidentRepository.updateWorkflowRunning(incidentId);
    WorkflowInstance instance = workflowRepository.createInstance(incidentId);
    log.info(
        "workflow_started workflowInstanceId={} incidentId={} incidentNo={} service={}",
        instance.id(),
        incidentId,
        incident.incidentNo(),
        incident.serviceName()
    );
    WorkflowInstance finished = workflowEngine.run(new WorkflowContext(instance.id(), incident));
    log.info(
        "workflow_finished workflowInstanceId={} incidentId={} status={}",
        finished.id(),
        incidentId,
        finished.status()
    );
    return WorkflowResponse.from(finished);
  }

  /**
   * 判断工作流是否仍处于等待操作员的状态（运行中或等待审批）。
   *
   * @param status 工作流当前状态
   * @return 若状态为 RUNNING 或 WAITING_APPROVAL 返回 true
   */
  private boolean isStillAwaitingOperator(String status) {
    return WorkflowStatus.RUNNING.equals(status) || WorkflowStatus.WAITING_APPROVAL.equals(status);
  }

  /**
   * 根据 ID 获取工作流实例详情。
   *
   * @param instanceId 工作流实例 ID
   * @return 工作流响应体
   * @throws ApiException 工作流不存在时抛出
   */
  public WorkflowResponse get(Long instanceId) {
    return workflowRepository.findInstance(instanceId)
        .map(WorkflowResponse::from)
        .orElseThrow(() -> ApiException.notFound("Workflow not found: " + instanceId));
  }

  /**
   * 获取指定事件最近一次工作流实例，若不存在则返回 null。
   *
   * @param incidentId 事件 ID
   * @return 工作流响应体，不存在时返回 null
   * @throws ApiException 事件不存在时抛出
   */
  public WorkflowResponse getLatestByIncidentOrNull(Long incidentId) {
    incidentService.findRequired(incidentId);
    return workflowRepository.findLatestByIncident(incidentId)
        .map(WorkflowResponse::from)
        .orElse(null);
  }

  /**
   * 获取指定工作流实例的所有节点执行记录。
   *
   * @param instanceId 工作流实例 ID
   * @return 节点执行记录列表
   * @throws ApiException 工作流不存在时抛出
   */
  public List<WorkflowNodeExecutionResponse> listNodes(Long instanceId) {
    if (workflowRepository.findInstance(instanceId).isEmpty()) {
      throw ApiException.notFound("Workflow not found: " + instanceId);
    }
    return workflowRepository.findNodeExecutions(instanceId).stream()
        .map(WorkflowNodeExecutionResponse::from)
        .toList();
  }

  /**
   * 获取指定工作流实例的 MCP 工具调用审计日志。
   *
   * @param instanceId 工作流实例 ID
   * @return 工具调用日志列表
   * @throws ApiException 工作流不存在时抛出
   */
  public List<ToolCallLogResponse> listToolCalls(Long instanceId) {
    if (workflowRepository.findInstance(instanceId).isEmpty()) {
      throw ApiException.notFound("Workflow not found: " + instanceId);
    }
    return toolCallLogger.findByWorkflow(instanceId).stream()
        .map(ToolCallLogResponse::from)
        .toList();
  }
}
