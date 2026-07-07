package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 故障单应用服务。
 *
 * <p>负责故障单创建、查询、详情聚合和关闭时的指标状态推进；复杂编排逻辑交给工作流服务。</p>
 */
@Service
public class IncidentService {
  /** SLF4J 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

  /** 故障单数据访问组件 */
  private final IncidentRepository incidentRepository;
  /** 故障指标服务，用于记录和查询指标快照 */
  private final IncidentMetricsService incidentMetricsService;

  public IncidentService(IncidentRepository incidentRepository, IncidentMetricsService incidentMetricsService) {
    this.incidentRepository = incidentRepository;
    this.incidentMetricsService = incidentMetricsService;
  }

  /**
   * 创建故障单，并记录初始指标快照。
   *
   * <p>人工创建故障时写入一组降级指标，方便本地演示详情页和恢复曲线。</p>
   *
   * @param request 故障创建请求，包含标题、受影响服务等必要信息
   * @return 创建的故障单响应
   */
  @Transactional
  public IncidentResponse create(IncidentCreateRequest request) {
    // 人工创建故障时写入一组降级指标，方便本地演示详情页和恢复曲线。
    Incident incident = incidentRepository.create(request);
    incidentMetricsService.recordInitialSnapshot(incident);
    log.info(
        "incident_created id={} incidentNo={} service={} source={}",
        incident.id(),
        incident.incidentNo(),
        incident.serviceName(),
        incident.source()
    );
    return IncidentResponse.from(incident);
  }

  /**
   * 从告警创建故障单，并记录告警携带的指标快照。
   *
   * <p>优先保留上游携带的指标，便于解释故障为何被打开。</p>
   *
   * @param request    故障创建请求
   * @param errorRate  告警时的错误率
   * @param p95Latency 告警时的 P95 延迟
   * @param qps        告警时的 QPS
   * @return 创建的故障单响应
   */
  @Transactional
  public IncidentResponse createFromAlert(
      IncidentCreateRequest request,
      BigDecimal errorRate,
      Integer p95Latency,
      Integer qps
  ) {
    // 告警入站创建故障时优先保留上游携带的指标，便于解释故障为何被打开。
    Incident incident = incidentRepository.create(request);
    incidentMetricsService.recordAlertSnapshot(incident, errorRate, p95Latency, qps);
    log.info(
        "incident_created_from_alert id={} incidentNo={} service={} source={}",
        incident.id(),
        incident.incidentNo(),
        incident.serviceName(),
        incident.source()
    );
    return IncidentResponse.from(incident);
  }

  /**
   * 查询故障单列表，支持筛选和分页。
   *
   * @param status      过滤条件：故障状态（可选）
   * @param serviceName 过滤条件：受影响服务（可选）
   * @param severity    过滤条件：严重等级（可选）
   * @param page        分页页码
   * @param size        每页记录数
   * @return 故障单响应列表
   */
  public List<IncidentResponse> list(
      String status,
      String serviceName,
      String severity,
      int page,
      int size
  ) {
    return incidentRepository.findAll(status, serviceName, severity, page, size).stream()
        .map(IncidentResponse::from)
        .toList();
  }

  /**
   * 根据 ID 获取故障单基本信息。
   *
   * @param id 故障单 ID
   * @return 故障单响应
   * @throws com.example.incidentcopilot.common.ApiException 如果故障单不存在
   */
  public IncidentResponse get(Long id) {
    return IncidentResponse.from(findRequired(id));
  }

  /**
   * 获取故障单详情，包含基本信息和最近的指标快照。
   *
   * @param id 故障单 ID
   * @return 故障单详情响应
   * @throws com.example.incidentcopilot.common.ApiException 如果故障单不存在
   */
  public IncidentDetailResponse getDetail(Long id) {
    Incident incident = findRequired(id);
    return new IncidentDetailResponse(
        IncidentResponse.from(incident),
        incidentMetricsService.findLatestSnapshots(id, 10)
    );
  }

  /**
   * 关闭故障单。
   *
   * <p>关闭前写入 recovered 快照，保证详情页最后一段指标状态能体现恢复完成。</p>
   *
   * @param id      故障单 ID
   * @param request 关闭请求，包含关闭人和关闭原因
   * @return 更新后的故障单响应
   * @throws com.example.incidentcopilot.common.ApiException 如果故障单不存在
   */
  @Transactional
  public IncidentResponse close(Long id, IncidentCloseRequest request) {
    // 关闭故障前写入 recovered 快照，保证详情页最后一段指标状态能体现恢复完成。
    Incident incident = findRequired(id);
    incidentMetricsService.recordRecoveredSnapshot(incident);
    Incident closed = incidentRepository.close(id);
    log.info("incident_closed id={} incidentNo={} closedBy={}", id, closed.incidentNo(), request.closedBy());
    return IncidentResponse.from(closed);
  }

  /**
   * 根据 ID 查找故障单，若不存在则抛出异常。
   *
   * @param id 故障单 ID
   * @return 故障单记录
   * @throws com.example.incidentcopilot.common.ApiException 如果故障单不存在
   */
  public Incident findRequired(Long id) {
    return incidentRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Incident not found: " + id));
  }
}
