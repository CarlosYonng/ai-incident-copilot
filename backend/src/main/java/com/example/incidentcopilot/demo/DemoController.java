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

@RestController
@RequestMapping("/api/demo")
public class DemoController {
  private final AlertIngestService alertIngestService;
  private final IncidentMetricsService incidentMetricsService;

  public DemoController(AlertIngestService alertIngestService, IncidentMetricsService incidentMetricsService) {
    this.alertIngestService = alertIngestService;
    this.incidentMetricsService = incidentMetricsService;
  }

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

  @GetMapping("/metrics/{incidentId}")
  public ApiResponse<List<MetricSnapshot>> metrics(@PathVariable Long incidentId) {
    return ApiResponse.ok(incidentMetricsService.findLatestSnapshots(incidentId, 20));
  }
}
