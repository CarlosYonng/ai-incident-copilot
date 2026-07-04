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

@Component
public class RunbookRetriever {
  private final Path runbookDir;

  public RunbookRetriever(@Value("${incident-copilot.runbook-dir}") String runbookDir) {
    this.runbookDir = Path.of(runbookDir);
  }

  public List<RunbookDocument> search(Incident incident, String diagnosisSummary) {
    if (!Files.isDirectory(runbookDir)) {
      return fallbackRunbooks(incident);
    }
    try (var stream = Files.list(runbookDir)) {
      List<String> terms = queryTerms(incident, diagnosisSummary);
      // This lightweight scorer is intentionally transparent: filename, title,
      // and body matches are easy to explain during an interview.
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

  private List<RunbookDocument> fallbackRunbooks(Incident incident) {
    return List.of(new RunbookDocument(
        "fallback-runbook.md",
        incident.serviceName() + " 标准故障处理 Runbook",
        "检查错误率、p95 延迟、下游依赖、最近发布和配置变更。",
        1,
        "检查错误率、p95 延迟、下游依赖、最近发布和配置变更。"
    ));
  }

  private String excerpt(String content) {
    String compact = content.replaceAll("\\s+", " ").trim();
    return compact.length() <= 260 ? compact : compact.substring(0, 260) + "...";
  }

  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
