package com.example.incidentcopilot.report;

import com.example.incidentcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复盘报告接口。
 *
 * <p>支持按故障单读取已生成复盘，以及基于当前审计数据重新生成复盘。</p>
 */
@RestController
@RequestMapping("/api/incidents/{incidentId}")
public class PostmortemController {

  /** 复盘报告业务服务 */
  private final PostmortemService postmortemService;

  /**
   * 构造方法，注入复盘报告服务。
   *
   * @param postmortemService 复盘报告服务
   */
  public PostmortemController(PostmortemService postmortemService) {
    this.postmortemService = postmortemService;
  }

  /**
   * 获取指定故障单的已有复盘报告。
   *
   * @param incidentId 故障单 ID
   * @return 复盘报告响应体
   */
  @GetMapping("/postmortem")
  public ApiResponse<PostmortemResponse> get(@PathVariable Long incidentId) {
    return ApiResponse.ok(postmortemService.get(incidentId));
  }

  /**
   * 基于当前审计数据重新生成指定故障单的复盘报告。
   *
   * @param incidentId 故障单 ID
   * @return 复盘报告响应体
   */
  @PostMapping("/generate-postmortem")
  public ApiResponse<PostmortemResponse> generate(@PathVariable Long incidentId) {
    return ApiResponse.ok(postmortemService.generate(incidentId));
  }
}
