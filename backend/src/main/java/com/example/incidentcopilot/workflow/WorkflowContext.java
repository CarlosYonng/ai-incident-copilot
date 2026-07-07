package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.incident.Incident;
import java.util.HashMap;
import java.util.Map;

/**
 * 单次同步工作流运行期间，节点之间共享的内存上下文。
 *
 * <p>可审计数据会落库到 Repository；这里仅传递指标、诊断证据、Runbook、最终状态提示等临时值。</p>
 */
public class WorkflowContext {
  /** 所属工作流实例 ID */
  private final Long workflowInstanceId;
  /** 当前处理的事件 */
  private final Incident incident;
  /** 节点间共享的临时属性集合（运行时内存，不持久化） */
  private final Map<String, Object> attributes = new HashMap<>();

  /**
   * 构造一个工作流上下文。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param incident           当前处理的事件
   */
  public WorkflowContext(Long workflowInstanceId, Incident incident) {
    this.workflowInstanceId = workflowInstanceId;
    this.incident = incident;
  }

  /**
   * 返回工作流实例 ID。
   *
   * @return 工作流实例 ID
   */
  public Long workflowInstanceId() {
    return workflowInstanceId;
  }

  /**
   * 返回当前事件。
   *
   * @return 事件对象
   */
  public Incident incident() {
    return incident;
  }

  /**
   * 向上下文中存入一个属性值。
   *
   * @param key   属性键
   * @param value 属性值
   */
  public void put(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * 根据键获取属性值。
   *
   * @param key 属性键
   * @return 属性值，不存在则返回 null
   */
  public Object get(String key) {
    return attributes.get(key);
  }

  /**
   * 获取字符串类型的属性值，若不存在则返回默认值。
   *
   * @param key          属性键
   * @param defaultValue 默认值
   * @return 字符串形式的属性值或默认值
   */
  public String getString(String key, String defaultValue) {
    Object value = attributes.get(key);
    return value == null ? defaultValue : String.valueOf(value);
  }

  /**
   * 返回上下文属性的不可变副本。
   *
   * @return 属性映射（只读）
   */
  public Map<String, Object> attributes() {
    return Map.copyOf(attributes);
  }
}
