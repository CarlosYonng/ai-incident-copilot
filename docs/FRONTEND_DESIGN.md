# 前端页面设计

## 1. 设计原则

- 工作台风格，信息密度适中，避免营销式首页。
- 第一屏直接展示 Incident 列表和当前处理状态。
- 强调 Workflow 时间线、工具调用审计、人工确认卡片。
- 中高风险动作在视觉上明确标识，但不制造误导性的“自动执行”按钮。

## 2. 页面结构

```text
App
  Layout
    Sidebar
      Incidents
      Demo Faults
      Runbooks
    Header
    Main
```

## 3. 页面 1：Incident 列表

路径：`/incidents`

展示字段：

- Incident 编号
- 标题
- 服务
- 接口
- 等级
- 状态
- 创建时间
- 操作

操作：

- 查看详情
- 启动 Workflow
- 关闭 Incident

状态颜色：

- `OPEN`：蓝色
- `WORKFLOW_RUNNING`：黄色
- `WAITING_APPROVAL`：橙色
- `RECOVERING`：青色
- `CLOSED`：绿色
- `FAILED`：红色

## 4. 页面 2：Incident 详情

路径：`/incidents/:id`

区域：

- 基本信息：编号、标题、服务、接口、trace id、异常类型。
- 指标卡片：错误率、p95 延迟、QPS、状态。
- 诊断摘要：来自 MCP 诊断报告和 LLM 摘要。
- 相关 Runbook：命中的 Runbook 名称和关键段落。
- 候选处置方案：按风险等级排序。
- 复盘报告：生成后展示摘要和改进项。

## 5. 页面 3：Workflow 时间线

路径：`/workflows/:instanceId`

展示：

- 节点名称。
- 节点类型。
- 状态。
- 开始时间。
- 耗时。
- 输入 JSON。
- 输出 JSON。
- 错误信息。
- 重试按钮。

节点状态：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `SKIPPED`
- `WAITING_APPROVAL`

## 6. 页面 4：处置方案审核

路径：`/incidents/:id/actions`

卡片字段：

- 建议动作。
- 风险等级。
- 建议原因。
- 证据来源。
- 影响范围。
- 前置检查。
- 审核状态。

按钮：

- 批准方案。
- 驳回方案。
- 要求补充证据。
- 升级给 SRE。
- 标记线下已执行。
- 标记方案无效。

按钮约束：

- 高风险动作不展示“执行”语义，只展示“批准线下处理”或“标记线下已执行”。
- 所有人工操作都要求填写操作者和备注。

## 7. 页面 5：复盘报告

路径：`/incidents/:id/postmortem`

展示：

- 故障摘要。
- 影响范围。
- 根因。
- 时间线。
- 处理过程。
- 改进项。
- 预防措施。

操作：

- 生成复盘。
- 重新生成复盘。
- 复制 Markdown。

