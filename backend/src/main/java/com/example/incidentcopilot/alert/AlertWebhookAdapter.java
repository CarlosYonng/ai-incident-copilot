package com.example.incidentcopilot.alert;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertWebhookAdapter {

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
        firstText(labels, annotations, "severity", "severity_hint", "P2"),
        payload,
        true
    );
  }

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
        firstText(labels, annotations, "severity", "severity_hint", "P2"),
        payload,
        true
    );
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> firstAlert(Map<String, Object> payload) {
    Object alerts = payload.get("alerts");
    if (alerts instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return payload;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Map<String, Object> value, String key) {
    Object nested = value.get(key);
    if (nested instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

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

  private String textValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

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

  private String stableEventId(String source, String serviceName, String signalName, String startsAt) {
    String seed = source + "|" + serviceName + "|" + signalName + "|" + startsAt;
    return source + "-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
