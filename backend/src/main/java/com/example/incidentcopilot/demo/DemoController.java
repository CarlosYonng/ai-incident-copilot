package com.example.incidentcopilot.demo;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.incident.IncidentCreateRequest;
import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.metrics.MetricSnapshot;
import com.example.incidentcopilot.metrics.MockMetricsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
  private final IncidentService incidentService;
  private final MockMetricsService mockMetricsService;

  public DemoController(IncidentService incidentService, MockMetricsService mockMetricsService) {
    this.incidentService = incidentService;
    this.mockMetricsService = mockMetricsService;
  }

  @PostMapping("/faults/payment-timeout")
  public ApiResponse<IncidentResponse> paymentTimeout(@RequestBody(required = false) DemoFaultRequest request) {
    return ApiResponse.ok(incidentService.create(new IncidentCreateRequest(
        "payment-service 支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "DEMO",
        "trace-payment-timeout-001",
        "TimeoutError",
        "5 分钟内 500 错误率升高，p95 延迟升至 3200ms"
    )));
  }

  @PostMapping("/faults/order-npe")
  public ApiResponse<IncidentResponse> orderNpe(@RequestBody(required = false) DemoFaultRequest request) {
    return ApiResponse.ok(incidentService.create(new IncidentCreateRequest(
        "order-service 创建订单空指针",
        "order-service",
        "/api/orders",
        "DEMO",
        "trace-order-npe-001",
        "NullPointerException",
        "创建订单时 userProfile 为空导致 500 错误"
    )));
  }

  @GetMapping("/metrics/{incidentId}")
  public ApiResponse<List<MetricSnapshot>> metrics(@PathVariable Long incidentId) {
    return ApiResponse.ok(mockMetricsService.findLatestSnapshots(incidentId, 20));
  }
}
