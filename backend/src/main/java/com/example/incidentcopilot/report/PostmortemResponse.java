package com.example.incidentcopilot.report;

import java.util.List;

/**
 * 复盘报告接口响应体。
 *
 * <p>包含故障摘要、根因、影响、行动项及完整报告正文，供前端直接展示。</p>
 *
 * @param incidentId     故障单 ID
 * @param summary        简短故障摘要
 * @param rootCause      根因分析
 * @param impact         用户或业务影响
 * @param actionItems    后续行动项列表
 * @param preventionItems 预防改进项列表
 * @param reportContent  渲染后的复盘正文
 */
public record PostmortemResponse(
    Long incidentId,
    String summary,
    String rootCause,
    String impact,
    List<String> actionItems,
    List<String> preventionItems,
    String reportContent
) {
}
