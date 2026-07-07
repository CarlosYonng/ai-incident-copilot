package com.example.incidentcopilot.action;

import com.example.incidentcopilot.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处置方案接口。
 *
 * <p>提供方案列表、审批、驳回、升级和线下执行结果回填能力；接口只记录决策，不直接执行生产动作。</p>
 */
@RestController
public class ActionProposalController {
  private final ActionProposalService actionProposalService;

  /**
   * 构造控制器，注入处置方案服务。
   *
   * @param actionProposalService 处置方案服务
   */
  public ActionProposalController(ActionProposalService actionProposalService) {
    this.actionProposalService = actionProposalService;
  }

  /**
   * 获取指定故障单下的所有处置方案列表。
   *
   * @param incidentId 故障单 ID
   * @return 处置方案响应列表
   */
  @GetMapping("/api/incidents/{incidentId}/actions")
  public ApiResponse<List<ActionProposalResponse>> list(@PathVariable Long incidentId) {
    return ApiResponse.ok(actionProposalService.listByIncident(incidentId));
  }

  /**
   * 审批指定处置方案。
   *
   * @param actionId 方案 ID
   * @param request  审批请求（审批人 + 备注）
   * @return 更新后的处置方案
   */
  @PostMapping("/api/actions/{actionId}/approve")
  public ApiResponse<ActionProposalResponse> approve(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.approve(actionId, request));
  }

  /**
   * 驳回指定处置方案。
   *
   * @param actionId 方案 ID
   * @param request  驳回请求（操作人 + 原因）
   * @return 更新后的处置方案
   */
  @PostMapping("/api/actions/{actionId}/reject")
  public ApiResponse<ActionProposalResponse> reject(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.reject(actionId, request));
  }

  /**
   * 标记处置方案已在线下执行完成。
   *
   * @param actionId 方案 ID
   * @param request  线下执行请求（执行人 + 结果详情）
   * @return 更新后的处置方案
   */
  @PostMapping("/api/actions/{actionId}/mark-offline-executed")
  public ApiResponse<ActionProposalResponse> markOfflineExecuted(
      @PathVariable Long actionId,
      @Valid @RequestBody MarkOfflineExecutedRequest request
  ) {
    return ApiResponse.ok(actionProposalService.markOfflineExecuted(actionId, request));
  }

  /**
   * 回填处置方案的执行结果。
   *
   * @param actionId 方案 ID
   * @param request  执行结果请求（执行人 + 结果详情）
   * @return 更新后的处置方案
   */
  @PostMapping("/api/actions/{actionId}/record-result")
  public ApiResponse<ActionProposalResponse> recordResult(
      @PathVariable Long actionId,
      @Valid @RequestBody MarkOfflineExecutedRequest request
  ) {
    return ApiResponse.ok(actionProposalService.recordResult(actionId, request));
  }

  /**
   * 升级指定处置方案（转交上级或 SRE 处理）。
   *
   * @param actionId 方案 ID
   * @param request  升级请求（操作人 + 备注）
   * @return 更新后的处置方案
   */
  @PostMapping("/api/actions/{actionId}/escalate")
  public ApiResponse<ActionProposalResponse> escalate(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.escalate(actionId, request));
  }
}
