package com.example.incidentcopilot.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JDBC JSON 序列化与反序列化工具组件。
 * <p>封装 Jackson 的 {@link ObjectMapper}，提供对象 JSON 序列化
 * 及数据库 JSON 字段的安全读取方法。</p>
 */
@Component
public class JdbcJson {
  /** Jackson JSON 处理器。 */
  private final ObjectMapper objectMapper;

  /**
   * 构造 JDBC JSON 工具。
   *
   * @param objectMapper Jackson ObjectMapper 实例
   */
  public JdbcJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 将 Java 对象序列化为 JSON 字符串。
   *
   * @param value 待序列化对象
   * @return JSON 字符串
   * @throws ApiException 序列化失败时抛出内部服务错误异常
   */
  public String stringify(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new ApiException("JSON_SERIALIZE_FAILED", exception.getMessage(), org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * 返回空 JSON 对象字符串 "{}"。
   *
   * @return 空 JSON 对象字符串
   */
  public String emptyObject() {
    return stringify(Map.of());
  }

  /**
   * 从数据库 JSON 字段读取字符串数组。读取失败时返回空列表，避免复盘详情接口被历史脏数据拖垮。
   *
   * @param json JSON 字符串
   * @return 字符串列表，解析失败或输入为空时返回空列表
   */
  public List<String> readStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readerForListOf(String.class).readValue(json);
    } catch (Exception exception) {
      return List.of();
    }
  }
}
