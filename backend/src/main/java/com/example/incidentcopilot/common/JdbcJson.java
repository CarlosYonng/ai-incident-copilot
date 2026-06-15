package com.example.incidentcopilot.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdbcJson {
  private final ObjectMapper objectMapper;

  public JdbcJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String stringify(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new ApiException("JSON_SERIALIZE_FAILED", exception.getMessage(), org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  public String emptyObject() {
    return stringify(Map.of());
  }
}
