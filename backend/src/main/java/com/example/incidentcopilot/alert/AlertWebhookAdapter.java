package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.common.DomainConstants.Severity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Webhook 适配器，将外部监控系统的告警格式归一化为内部统一模型。
 *
 * <p>当前支持 Grafana 和 Prometheus Alertmanager 两种来源。
 * 新增外部告警源时，优先在此适配层完成字段映射，
 * 避免将外部字段名泄露到业务服务中。</p>
 */
@Component
public class AlertWebhookAdapter {

  /**
   * 将 Grafana webhook payload 归一化为内部告警入站模型。
   * 新增外部告警源时，优先在适配层完成字段映射，不把外部字段名泄露到业务服务里。
   */
  public AlertIngestRequest fromGrafana(Map<String, Object> payload) {
    Map<String, Object> alert = firstAlert(payload);
    Map<String, Object> labels = mapValue(alert, "labels");
    Map<String, Object> annotations = mapValue(alert, "annotations");
    Map<String, Object> values = mapValue(alert, "values");
    String signalName = firstText(labels, annotations, "alertname", "summary", "Grafana 告警");
    String serviceName = firstText(labels, annotations, "service_name", "service", "app", "unknown-service");
    String startsAt = textValue(alert.get("startsAt"));
    String eventId = firstText(alert, labels, "fingerprint", "event_id", "");
    if (!hasText(eventId)) {
      eventId = stableEventId("grafana", serviceName, signalName, startsAt);
    }
    return new AlertIngestRequest(
        eventId,
        "grafana",
        signalName,
        serviceName,
        firstText(labels, annotations, "endpoint", "path", null),
        firstText(labels, annotations, "trace_id", "traceId", null),
        firstText(labels, annotations, "exception_type", "exceptionType", null),
        firstText(annotations, labels, "description", "summary", signalName),
        decimalValue(labels, annotations, values, "error_rate", "errorRate"),
        intValue(labels, annotations, values, "p95_latency", "p95Latency", "p95"),
        intValue(labels, annotations, values, "qps"),
        intValue(labels, annotations, values, "affected_requests", "affectedRequests"),
        firstText(labels, annotations, "severity", "severity_hint", Severity.P2),
        payload,
        true
    );
  }

  /**
   * 将 Alertmanager webhook payload 归一化为内部告警入站模型。
   */
  public AlertIngestRequest fromAlertmanager(Map<String, Object> payload) {
    Map<String, Object> alert = firstAlert(payload);
    Map<String, Object> labels = mapValue(alert, "labels");
    Map<String, Object> annotations = mapValue(alert, "annotations");
    String signalName = firstText(labels, annotations, "alertname", "summary", "Alertmanager 告警");
    String serviceName = firstText(labels, annotations, "service_name", "service", "job", "unknown-service");
    String startsAt = textValue(alert.get("startsAt"));
    String eventId = firstText(alert, labels, "fingerprint", "event_id", "");
    if (!hasText(eventId)) {
      eventId = stableEventId("alertmanager", serviceName, signalName, startsAt);
    }
    return new AlertIngestRequest(
        eventId,
        "alertmanager",
        signalName,
        serviceName,
        firstText(labels, annotations, "endpoint", "path", null),
        firstText(labels, annotations, "trace_id", "traceId", null),
        firstText(labels, annotations, "exception_type", "exceptionType", null),
        firstText(annotations, labels, "description", "summary", signalName),
        decimalValue(labels, annotations, Map.of(), "error_rate", "errorRate"),
        intValue(labels, annotations, Map.of(), "p95_latency", "p95Latency", "p95"),
        intValue(labels, annotations, Map.of(), "qps"),
        intValue(labels, annotations, Map.of(), "affected_requests", "affectedRequests"),
        firstText(labels, annotations, "severity", "severity_hint", Severity.P2),
        payload,
        true
    );
  }

  /**
   * 从 webhook 负载中提取第一条告警数据。
   *
   * <p>Grafana 和 Alertmanager 的 webhook 负载中 alerts 字段为数组，
   * 取第一条作为代表。若不存在 alerts 数组则直接返回整个 payload。</p>
   *
   * @param payload 原始 webhook 负载
   * @return 第一条告警的 Map 表示
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> firstAlert(Map<String, Object> payload) {
    Object alerts = payload.get("alerts");
    if (alerts instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return payload;
  }

  /**
   * 从 Map 中安全获取嵌套 Map 类型的字段值。
   *
   * @param value 父级 Map
   * @param key   字段名
   * @return 子 Map，若不存在或类型不匹配则返回空 Map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Map<String, Object> value, String key) {
    Object nested = value.get(key);
    if (nested instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  /**
   * 在两个 Map 中按优先级查找两个候选键，返回第一个非空的文本值。
   *
   * @param primary      优先查找的 Map
   * @param secondary    备选查找的 Map
   * @param firstKey     第一候选键
   * @param secondKey    第二候选键
   * @param defaultValue 默认值（所有候选均未找到时返回）
   * @return 查找到的文本值，或默认值
   */
  private String firstText(Map<String, Object> primary, Map<String, Object> secondary, String firstKey, String secondKey, String defaultValue) {
    String first = textValue(primary.get(firstKey));
    if (hasText(first)) {
      return first;
    }
    String second = textValue(primary.get(secondKey));
    if (hasText(second)) {
      return second;
    }
    String fallbackFirst = textValue(secondary.get(firstKey));
    if (hasText(fallbackFirst)) {
      return fallbackFirst;
    }
    String fallbackSecond = textValue(secondary.get(secondKey));
    return hasText(fallbackSecond) ? fallbackSecond : defaultValue;
  }

  /**
   * 在两个 Map 中按优先级查找三个候选键，返回第一个非空的文本值。
   *
   * @param first        优先查找的 Map
   * @param second       备选查找的 Map
   * @param keyA         第一候选键
   * @param keyB         第二候选键
   * @param keyC         第三候选键
   * @param defaultValue 默认值
   * @return 查找到的文本值，或默认值
   */
  private String firstText(Map<String, Object> first, Map<String, Object> second, String keyA, String keyB, String keyC, String defaultValue) {
    for (String key : List.of(keyA, keyB, keyC)) {
      String value = textValue(first.get(key));
      if (hasText(value)) {
        return value;
      }
      value = textValue(second.get(key));
      if (hasText(value)) {
        return value;
      }
    }
    return defaultValue;
  }

  /**
   * 将任意对象安全地转换为字符串。
   *
   * @param value 任意对象
   * @return 字符串表示，null 输入返回 null
   */
  private String textValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 在三个 Map 中按优先级依次查找多个候选键，将首个匹配值解析为 BigDecimal。
   *
   * @param first  第一优先级 Map
   * @param second 第二优先级 Map
   * @param third  第三优先级 Map
   * @param keys   候选键列表，按优先级排列
   * @return BigDecimal 值，解析失败或未找到时返回 null
   */
  private BigDecimal decimalValue(
      Map<String, Object> first,
      Map<String, Object> second,
      Map<String, Object> third,
      String... keys
  ) {
    String value = firstMatchingText(first, second, third, keys);
    if (!hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /**
   * 在三个 Map 中按优先级依次查找多个候选键，将首个匹配值解析为 Integer。
   *
   * @param first  第一优先级 Map
   * @param second 第二优先级 Map
   * @param third  第三优先级 Map
   * @param keys   候选键列表，按优先级排列
   * @return Integer 值，解析失败或未找到时返回 null
   */
  private Integer intValue(
      Map<String, Object> first,
      Map<String, Object> second,
      Map<String, Object> third,
      String... keys
  ) {
    String value = firstMatchingText(first, second, third, keys);
    if (!hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value).intValue();
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /**
   * 在三个 Map 中按优先级依次查找多个候选键，返回第一个非空的文本值。
   *
   * @param first  第一优先级 Map
   * @param second 第二优先级 Map
   * @param third  第三优先级 Map
   * @param keys   候选键列表，按优先级排列
   * @return 第一个非空的文本值，未找到时返回 null
   */
  private String firstMatchingText(Map<String, Object> first, Map<String, Object> second, Map<String, Object> third, String... keys) {
    for (String key : keys) {
      for (Map<String, Object> map : List.of(first, second, third)) {
        String value = textValue(map.get(key));
        if (hasText(value)) {
          return value;
        }
      }
    }
    return null;
  }

  /**
   * 生成稳定的告警事件 ID。
   *
   * <p>当 webhook 负载中未携带 fingerprint 或 event_id 时，
   * 使用来源、服务名、信号名称和开始时间的组合生成 UUID 作为唯一标识，
   * 确保相同告警重复发送时能幂等去重。</p>
   *
   * @param source      告警来源
   * @param serviceName 服务名称
   * @param signalName  信号名称
   * @param startsAt    告警开始时间
   * @return 稳定的 UUID 字符串
   */
  private String stableEventId(String source, String serviceName, String signalName, String startsAt) {
    String seed = source + "|" + serviceName + "|" + signalName + "|" + startsAt;
    return source + "-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 判断字符串是否不为 null 且不包含空白。
   *
   * @param value 待检查的字符串
   * @return true 如果字符串非空
   */
  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
