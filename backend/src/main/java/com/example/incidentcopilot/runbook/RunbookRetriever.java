package com.example.incidentcopilot.runbook;

import com.example.incidentcopilot.incident.Incident;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 应急处置手册检索组件。
 *
 * <p>根据故障单信息和诊断摘要，从本地 Markdown 手册目录中检索最相关的 Runbook 文档。
 * MVP 阶段使用轻量关键词打分算法进行排序，后续可替换为向量检索。</p>
 */
@Component
public class RunbookRetriever {

  /** Runbook 文档存放的目录路径 */
  private final Path runbookDir;

  /**
   * 构造方法，注入 Runbook 目录路径配置。
   *
   * @param runbookDir 手册文件目录路径（通过配置 {@code incident-copilot.runbook-dir} 注入）
   */
  public RunbookRetriever(@Value("${incident-copilot.runbook-dir}") String runbookDir) {
    this.runbookDir = Path.of(runbookDir);
  }

  /**
   * 根据 Incident 基础信息和诊断摘要检索本地 Markdown Runbook。
   *
   * <p>MVP 阶段使用轻量关键词打分，便于演示和排查；后续接向量库时可以保持该方法签名不变。</p>
   *
   * @param incident         故障单实体
   * @param diagnosisSummary 诊断摘要文本
   * @return 按匹配度降序排列的 Runbook 文档列表，最多 3 条；无匹配时返回默认 Fallback
   */
  public List<RunbookDocument> search(Incident incident, String diagnosisSummary) {
    if (!Files.isDirectory(runbookDir)) {
      return fallbackRunbooks(incident);
    }
    try (var stream = Files.list(runbookDir)) {
      List<String> terms = queryTerms(incident, diagnosisSummary);
      // 轻量打分规则保持透明：文件名、标题、正文命中分别加权，便于面试讲解和线上排障。
      List<RunbookDocument> documents = stream
          .filter(path -> path.getFileName().toString().endsWith(".md"))
          .map(path -> load(path, terms))
          .filter(document -> document.score() > 0)
          .sorted(Comparator.comparingInt(RunbookDocument::score).reversed())
          .limit(3)
          .toList();
      return documents.isEmpty() ? fallbackRunbooks(incident) : documents;
    } catch (IOException exception) {
      return fallbackRunbooks(incident);
    }
  }

  /**
   * 加载单个 Runbook 文件并计算与检索词的相关度评分。
   *
   * @param path   Markdown 文件路径
   * @param terms  检索关键词列表
   * @return 包含评分结果的 RunbookDocument 对象
   */
  private RunbookDocument load(Path path, List<String> terms) {
    try {
      String content = Files.readString(path, StandardCharsets.UTF_8);
      String fileName = path.getFileName().toString();
      String title = Arrays.stream(content.split("\\R"))
          .filter(line -> line.startsWith("#"))
          .findFirst()
          .map(line -> line.replaceFirst("^#+\\s*", ""))
          .orElse(fileName);
      int score = score(fileName, title, content, terms);
      return new RunbookDocument(fileName, title, content, score, excerpt(content));
    } catch (IOException exception) {
      return new RunbookDocument(path.getFileName().toString(), path.getFileName().toString(), "", 0, "");
    }
  }

  /**
   * 计算文档与检索词的相关度得分。
   *
   * <p>权重规则：文件名命中 +5，标题命中 +3，正文内容命中 +1。</p>
   *
   * @param fileName 文档文件名
   * @param title    文档标题
   * @param content  文档正文内容
   * @param terms    检索关键词列表
   * @return 总得分
   */
  private int score(String fileName, String title, String content, List<String> terms) {
    String normalizedFile = normalize(fileName);
    String normalizedTitle = normalize(title);
    String normalizedContent = normalize(content);
    int score = 0;
    for (String term : terms) {
      if (term.isBlank()) {
        continue;
      }
      String normalizedTerm = normalize(term);
      if (normalizedFile.contains(normalizedTerm)) {
        score += 5;
      }
      if (normalizedTitle.contains(normalizedTerm)) {
        score += 3;
      }
      if (normalizedContent.contains(normalizedTerm)) {
        score += 1;
      }
    }
    return score;
  }

  /**
   * 从故障单和诊断摘要中提取检索关键词列表。
   *
   * @param incident         故障单实体
   * @param diagnosisSummary 诊断摘要文本
   * @return 关键词列表
   */
  private List<String> queryTerms(Incident incident, String diagnosisSummary) {
    return List.of(
        incident.serviceName(),
        valueOrEmpty(incident.endpoint()),
        valueOrEmpty(incident.exceptionType()),
        incident.title(),
        valueOrEmpty(diagnosisSummary),
        incident.serviceName().replace("-service", ""),
        incident.title().replace("超时", "timeout").replace("空指针", "npe")
    );
  }

  /**
   * 返回默认的 Fallback Runbook（当目录不可读或无匹配文档时使用）。
   *
   * @param incident 故障单实体
   * @return 包含默认提示的 Runbook 文档列表
   */
  private List<RunbookDocument> fallbackRunbooks(Incident incident) {
    return List.of(new RunbookDocument(
        "fallback-runbook.md",
        incident.serviceName() + " 标准故障处理 Runbook",
        "检查错误率、p95 延迟、下游依赖、最近发布和配置变更。",
        1,
        "检查错误率、p95 延迟、下游依赖、最近发布和配置变更。"
    ));
  }

  /**
   * 截取文档开头部分作为摘要片段，长度不超过 260 字符。
   *
   * @param content 文档原始内容
   * @return 截取后的摘要字符串
   */
  private String excerpt(String content) {
    String compact = content.replaceAll("\\s+", " ").trim();
    return compact.length() <= 260 ? compact : compact.substring(0, 260) + "...";
  }

  /**
   * 将字符串转换为小写，用于不区分大小写的匹配。
   *
   * @param value 原始字符串
   * @return 小写化后的字符串，null 转为空字符串
   */
  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  /**
   * 将 null 转换为空字符串。
   *
   * @param value 原始字符串
   * @return 原值或空字符串
   */
  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
