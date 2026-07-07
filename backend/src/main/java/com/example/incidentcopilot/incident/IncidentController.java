package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.workflow.WorkflowResponse;
import com.example.incidentcopilot.workflow.WorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障单 REST 控制器。
 *
 * <p>提供故障创建、列表、详情、启动工作流、查询最新工作流和关闭故障能力。</p>
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
  private final IncidentService incidentService;
  private final WorkflowService workflowService;

  public IncidentController(IncidentService incidentService, WorkflowService workflowService) {
    this.incidentService = incidentService;
    this.workflowService = workflowService;
  }

  /**
   * 创建故障单。
   *
   * @param request 故障创建请求，包含标题、受影响服务、严重等级等必要信息
   * @return 创建的故障单信息
   */
  @PostMapping
  public ApiResponse<IncidentResponse> create(@Valid @RequestBody IncidentCreateRequest request) {
    return ApiResponse.ok(incidentService.create(request));
  }

  /**
   * 查询故障单列表，支持按状态、服务名、严重等级筛选及分页。
   *
   * @param status      过滤条件：故障状态（可选）
   * @param serviceName 过滤条件：受影响服务（可选）
   * @param severity    过滤条件：严重等级（可选）
   * @param page        分页页码，从 0 开始
   * @param size        每页记录数
   * @return 故障单响应列表
   */
  @GetMapping
  public ApiResponse<List<IncidentResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String serviceName,
      @RequestParam(required = false) String severity,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ApiResponse.ok(incidentService.list(status, serviceName, severity, page, size));
  }

  /**
   * 获取故障单详情。
   *
   * @param id 故障单 ID
   * @return 故障单详情，包含关联的告警和工作流信息
   */
  @GetMapping("/{id}")
  public ApiResponse<IncidentDetailResponse> get(@PathVariable Long id) {
    return ApiResponse.ok(incidentService.getDetail(id));
  }

  /**
   * 为指定故障单启动排障工作流。
   *
   * @param id 故障单 ID
   * @return 工作流执行结果
   */
  @PostMapping("/{id}/start-workflow")
  public ApiResponse<WorkflowResponse> startWorkflow(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.startIncidentWorkflow(id));
  }

  /**
   * 查询指定故障单的最新工作流。
   *
   * @param id 故障单 ID
   * @return 最新的工作流响应，若不存在则返回 null
   */
  @GetMapping("/{id}/workflow/latest")
  public ApiResponse<WorkflowResponse> latestWorkflow(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.getLatestByIncidentOrNull(id));
  }

  /**
   * 关闭指定故障单。
   *
   * @param id      故障单 ID
   * @param request 关闭请求，包含关闭原因、解决措施等
   * @return 更新后的故障单信息
   */
  @PostMapping("/{id}/close")
  public ApiResponse<IncidentResponse> close(
      @PathVariable Long id,
      @Valid @RequestBody IncidentCloseRequest request
  ) {
    return ApiResponse.ok(incidentService.close(id, request));
  }
}
