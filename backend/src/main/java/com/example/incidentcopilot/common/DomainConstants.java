package com.example.incidentcopilot.common;

/**
 * 项目内跨模块共享的业务字典。
 *
 * <p>当前数据库字段仍使用字符串保存状态，集中到这里可以避免服务层、工作流节点和测试中散落魔法值。
 */
public final class DomainConstants {
  private DomainConstants() {
  }

  /** 事件严重等级常量。 */
  public static final class Severity {
    /** P1 — 严重，系统核心功能完全不可用。 */
    public static final String P1 = "P1";
    /** P2 — 较高，主要功能受影响但有临时规避措施。 */
    public static final String P2 = "P2";
    /** P3 — 轻微，非核心功能问题或小范围影响。 */
    public static final String P3 = "P3";

    private Severity() {
    }
  }

  /** 事件生命周期状态常量。 */
  public static final class IncidentStatus {
    /** 事件已创建，等待处理。 */
    public static final String OPEN = "OPEN";
    /** 工作流正在执行中。 */
    public static final String WORKFLOW_RUNNING = "WORKFLOW_RUNNING";
    /** 等待人工审批。 */
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    /** 恢复操作进行中。 */
    public static final String RECOVERING = "RECOVERING";
    /** 事件已闭环。 */
    public static final String CLOSED = "CLOSED";
    /** 处理流程失败。 */
    public static final String FAILED = "FAILED";

    private IncidentStatus() {
    }
  }

  /** 工作流执行状态常量。 */
  public static final class WorkflowStatus {
    /** 工作流正在执行。 */
    public static final String RUNNING = "RUNNING";
    /** 工作流执行成功。 */
    public static final String SUCCESS = "SUCCESS";
    /** 工作流等待人工审批。 */
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    /** 工作流执行失败。 */
    public static final String FAILED = "FAILED";

    private WorkflowStatus() {
    }
  }

  /** 操作/建议风险评估等级常量。 */
  public static final class RiskLevel {
    /** 低风险，可直接执行。 */
    public static final String LOW = "LOW";
    /** 中等风险，建议人工审核。 */
    public static final String MEDIUM = "MEDIUM";
    /** 高风险，必须人工审批。 */
    public static final String HIGH = "HIGH";

    private RiskLevel() {
    }
  }

  /** 恢复措施操作状态常量。 */
  public static final class ActionStatus {
    /** 可执行状态。 */
    public static final String READY = "READY";
    /** 待处理状态。 */
    public static final String PENDING = "PENDING";
    /** 已批准。 */
    public static final String APPROVED = "APPROVED";
    /** 已拒绝。 */
    public static final String REJECTED = "REJECTED";
    /** 已升级至更高级别处理。 */
    public static final String ESCALATED = "ESCALATED";
    /** 已在离线环境执行。 */
    public static final String OFFLINE_EXECUTED = "OFFLINE_EXECUTED";
    /** 未被选中。 */
    public static final String NOT_SELECTED = "NOT_SELECTED";

    private ActionStatus() {
    }
  }

  /** 人工审批决策类型常量。 */
  public static final class ApprovalDecision {
    /** 批准。 */
    public static final String APPROVED = "APPROVED";
    /** 拒绝。 */
    public static final String REJECTED = "REJECTED";
    /** 升级处理。 */
    public static final String ESCALATED = "ESCALATED";
    /** 标记为已在离线环境执行。 */
    public static final String MARK_OFFLINE_EXECUTED = "MARK_OFFLINE_EXECUTED";

    private ApprovalDecision() {
    }
  }

  /** 指标健康状态常量。 */
  public static final class MetricStatus {
    /** 指标降级。 */
    public static final String DEGRADED = "degraded";
    /** 指标正在恢复。 */
    public static final String RECOVERING = "recovering";
    /** 指标已恢复。 */
    public static final String RECOVERED = "recovered";

    private MetricStatus() {
    }
  }

  /** 工作流上下文键名常量，用于在工作流各节点间传递数据。 */
  public static final class WorkflowContextKey {
    /** 工作流最终状态键。 */
    public static final String WORKFLOW_FINAL_STATUS = "workflowFinalStatus";
    /** 事件最终状态键。 */
    public static final String INCIDENT_FINAL_STATUS = "incidentFinalStatus";

    private WorkflowContextKey() {
    }
  }
}
