package com.example.incidentcopilot.report;

import java.time.LocalDateTime;

/**
 * 系统生成的故障复盘报告。
 *
 * <p>结构化 JSON 字段供前端展示，{@code reportContent} 保存可复制到外部系统的长文本报告。</p>
 *
 * @param id 数据库主键
 * @param incidentId 复盘对应的故障单 ID
 * @param summary 简短故障摘要
 * @param rootCause 根因分析
 * @param impact 用户或业务影响
 * @param timelineJson 序列化后的故障时间线
 * @param actionItemsJson 序列化后的后续行动项
 * @param preventionItemsJson 序列化后的预防改进项
 * @param reportContent 渲染后的复盘正文
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record PostmortemReport(
    Long id,
    Long incidentId,
    String summary,
    String rootCause,
    String impact,
    String timelineJson,
    String actionItemsJson,
    String preventionItemsJson,
    String reportContent,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
