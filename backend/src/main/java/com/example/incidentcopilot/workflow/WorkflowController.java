package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.audit.ToolCallLogResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流查询接口。
 *
 * <p>提供编排实例详情、节点执行时间线和 MCP 工具调用审计查询。</p>
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
  /** 工作流服务层 */
  private final WorkflowService workflowService;

  /**
   * 构造工作流控制器。
   *
   * @param workflowService 工作流服务
   */
  public WorkflowController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  /**
   * 获取指定工作流实例的详情。
   *
   * @param instanceId 工作流实例 ID
   * @return 工作流实例详情
   */
  @GetMapping("/{instanceId}")
  public ApiResponse<WorkflowResponse> get(@PathVariable Long instanceId) {
    return ApiResponse.ok(workflowService.get(instanceId));
  }

  /**
   * 获取指定工作流实例的所有节点执行记录。
   *
   * @param instanceId 工作流实例 ID
   * @return 节点执行记录列表
   */
  @GetMapping("/{instanceId}/nodes")
  public ApiResponse<List<WorkflowNodeExecutionResponse>> nodes(@PathVariable Long instanceId) {
    return ApiResponse.ok(workflowService.listNodes(instanceId));
  }

  /**
   * 获取指定工作流实例的 MCP 工具调用审计日志。
   *
   * @param instanceId 工作流实例 ID
   * @return 工具调用日志列表
   */
  @GetMapping("/{instanceId}/tool-calls")
  public ApiResponse<List<ToolCallLogResponse>> toolCalls(@PathVariable Long instanceId) {
    return ApiResponse.ok(workflowService.listToolCalls(instanceId));
  }
}
