package com.example.incidentcopilot.demo;

import com.example.incidentcopilot.alert.AlertIngestRequest;
import com.example.incidentcopilot.alert.AlertIngestService;
import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import com.example.incidentcopilot.metrics.MetricSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地演示接口。
 *
 * <p>用于快速注入支付超时、订单空指针等模拟故障，不代表真实生产告警源。</p>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
  /** 告警注入服务，用于模拟创建告警与事件。 */
  private final AlertIngestService alertIngestService;
  /** 指标查询服务，用于获取事件关联的监控指标。 */
  private final IncidentMetricsService incidentMetricsService;

  /**
   * 构造演示控制器。
   *
   * @param alertIngestService    告警注入服务
   * @param incidentMetricsService 指标查询服务
   */
  public DemoController(AlertIngestService alertIngestService, IncidentMetricsService incidentMetricsService) {
    this.alertIngestService = alertIngestService;
    this.incidentMetricsService = incidentMetricsService;
  }

  /**
   * 模拟支付回调超时故障注入。
   * <p>构造支付超时告警并触发事件创建工作流，用于本地调试端到端流程。</p>
   *
   * @param request 可选的自定义故障参数
   * @return 创建成功的事件响应
   */
  @PostMapping("/faults/payment-timeout")
  public ApiResponse<IncidentResponse> paymentTimeout(@RequestBody(required = false) DemoFaultRequest request) {
    return ApiResponse.ok(alertIngestService.ingest(new AlertIngestRequest(
        "demo-payment-" + UUID.randomUUID(),
        "demo-payment-gateway",
        "支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "trace-payment-timeout-001",
        "TimeoutError",
        "5 分钟内 500 错误率升高，p95 延迟升至 3200ms",
        new BigDecimal("0.0820"),
        3200,
        1260,
        238,
        "P1",
        Map.of(
            "businessOperation", "payment_callback",
            "gateway", "sandbox-pay-gateway",
            "window", "5m",
            "timeoutCount", 238
        ),
        false
    )).incident());
  }

  /**
   * 模拟创建订单空指针故障注入。
   * <p>构造订单服务空指针告警并触发事件创建工作流，用于本地调试端到端流程。</p>
   *
   * @param request 可选的自定义故障参数
   * @return 创建成功的事件响应
   */
  @PostMapping("/faults/order-npe")
  public ApiResponse<IncidentResponse> orderNpe(@RequestBody(required = false) DemoFaultRequest request) {
    return ApiResponse.ok(alertIngestService.ingest(new AlertIngestRequest(
        "demo-order-" + UUID.randomUUID(),
        "demo-order-api",
        "创建订单空指针",
        "order-service",
        "/api/orders",
        "trace-order-npe-001",
        "NullPointerException",
        "创建订单时 userProfile 为空导致 500 错误",
        new BigDecimal("0.0360"),
        1180,
        880,
        42,
        "P2",
        Map.of(
            "businessOperation", "order_create",
            "nullField", "userProfile",
            "window", "10m",
            "errorCount", 42
        ),
        false
    )).incident());
  }

  /**
   * 查询指定事件的最新监控指标快照。
   *
   * @param incidentId 事件 ID
   * @return 指标快照列表（最多 20 条）
   */
  @GetMapping("/metrics/{incidentId}")
  public ApiResponse<List<MetricSnapshot>> metrics(@PathVariable Long incidentId) {
    return ApiResponse.ok(incidentMetricsService.findLatestSnapshots(incidentId, 20));
  }
}
