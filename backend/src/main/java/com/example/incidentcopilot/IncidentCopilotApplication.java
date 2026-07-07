package com.example.incidentcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Incident Copilot 应用的 Spring Boot 入口。
 *
 * <p>负责故障事件的全生命周期管理：告警接入、故障诊断、根因分析、
 * 动作建议和事后复盘报告的生成。通过集成 MCP 服务和 AI 能力，
 * 帮助运维团队快速定位和解决线上故障。</p>
 */
@SpringBootApplication
public class IncidentCopilotApplication {

  /**
   * 应用主入口。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(IncidentCopilotApplication.class, args);
  }
}
