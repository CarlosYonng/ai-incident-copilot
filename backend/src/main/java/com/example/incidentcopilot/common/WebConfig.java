package com.example.incidentcopilot.common;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 网络层配置。
 *
 * <p>当前只配置 API 跨域来源，默认允许本地 Vite 前端访问后端接口。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  /** 允许的跨域来源列表。 */
  private final String[] allowedOrigins;

  /**
   * 构造 Web 配置，从配置文件中读取允许的跨域来源。
   *
   * @param allowedOrigins 逗号分隔的允许跨域来源字符串
   */
  public WebConfig(@Value("${incident-copilot.cors-allowed-origins}") String allowedOrigins) {
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .toArray(String[]::new);
  }

  /**
   * 配置 CORS 跨域映射。
   * <p>只对 {@code /api/**} 路径开启跨域，避免将静态资源或未来管理端点
   * 无意暴露给前端域名。</p>
   *
   * @param registry CORS 注册表
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
