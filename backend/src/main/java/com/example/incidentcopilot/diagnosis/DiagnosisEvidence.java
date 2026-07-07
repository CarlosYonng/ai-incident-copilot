package com.example.incidentcopilot.diagnosis;

import java.util.List;

/**
 * 故障诊断证据的聚合结果，由 {@code DiagnosisMcpClient} 生成。
 *
 * <p>包含从日志、代码搜索、工单系统和 AI 报告等多个来源汇集的诊断信息，
 * 供后续根因分析和动作建议使用。</p>
 *
 * @param summary 诊断摘要，描述故障原因或建议
 * @param logs 相关错误日志列表
 * @param codeHints 代码层面的线索和修改建议
 * @param tickets 关联的历史工单或类似案例
 * @param reportId 诊断报告的唯一标识
 * @param reportMarkdown 诊断报告的 Markdown 格式完整内容
 * @param fallbackUsed 是否使用了 fallback 数据（当 MCP 调用失败时）
 */
public record DiagnosisEvidence(
    String summary,
    List<String> logs,
    List<String> codeHints,
    List<String> tickets,
    String reportId,
    String reportMarkdown,
    boolean fallbackUsed
) {
}
