package com.example.incidentcopilot.report;

import com.example.incidentcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}")
public class PostmortemController {
  private final PostmortemService postmortemService;

  public PostmortemController(PostmortemService postmortemService) {
    this.postmortemService = postmortemService;
  }

  @GetMapping("/postmortem")
  public ApiResponse<PostmortemResponse> get(@PathVariable Long incidentId) {
    return ApiResponse.ok(postmortemService.get(incidentId));
  }

  @PostMapping("/generate-postmortem")
  public ApiResponse<PostmortemResponse> generate(@PathVariable Long incidentId) {
    return ApiResponse.ok(postmortemService.generate(incidentId));
  }
}
