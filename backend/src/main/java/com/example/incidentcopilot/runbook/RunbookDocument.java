package com.example.incidentcopilot.runbook;

/**
 * 应急处置手册文档。
 *
 * <p>封装从手册系统中检索到的文档元数据和内容片段，
 * 包含文件名、标题、全文及与查询的匹配评分。</p>
 *
 * @param fileName 文档文件名（用于定位来源）
 * @param title    文档标题
 * @param content  文档完整内容
 * @param score    与查询的匹配评分
 * @param excerpt  匹配片段摘要
 */
public record RunbookDocument(
    String fileName,
    String title,
    String content,
    int score,
    String excerpt
) {
}
