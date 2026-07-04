package com.example.incidentcopilot.runbook;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.incidentcopilot.incident.Incident;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunbookRetrieverTest {

  @TempDir
  Path tempDir;

  @Test
  void searchRanksPaymentRunbookAboveUnrelatedRunbook() throws Exception {
    Files.writeString(tempDir.resolve("payment-callback-timeout.md"), """
        # 支付回调超时 Runbook

        payment-service /api/payment/callback TimeoutError p95 latency increased.
        """);
    Files.writeString(tempDir.resolve("redis-cache-failure.md"), """
        # Redis 缓存故障 Runbook

        redis cache miss and connection refused.
        """);
    RunbookRetriever retriever = new RunbookRetriever(tempDir.toString());

    List<RunbookDocument> results = retriever.search(paymentIncident(), "TimeoutError near payment callback");

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst().fileName()).isEqualTo("payment-callback-timeout.md");
    assertThat(results.getFirst().score()).isPositive();
    assertThat(results.getFirst().excerpt()).contains("payment-service");
  }

  @Test
  void searchUsesFallbackWhenDirectoryDoesNotExist() {
    RunbookRetriever retriever = new RunbookRetriever(tempDir.resolve("missing").toString());

    List<RunbookDocument> results = retriever.search(paymentIncident(), "anything");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().fileName()).isEqualTo("fallback-runbook.md");
    assertThat(results.getFirst().title()).contains("payment-service");
  }

  @Test
  void searchMatchesPortfolioRagRunbook() throws Exception {
    Files.writeString(tempDir.resolve("portfolio-rag-retrieval-empty.md"), """
        # AI Agent Portfolio RAG 检索无结果 Runbook

        ai-agent-portfolio RAG retrieval empty no chunks found low similarity score.
        """);
    Files.writeString(tempDir.resolve("payment-callback-timeout.md"), """
        # 支付回调超时 Runbook

        payment-service callback TimeoutError.
        """);
    RunbookRetriever retriever = new RunbookRetriever(tempDir.toString());

    List<RunbookDocument> results = retriever.search(portfolioRagIncident(), "RAG retrieval empty no chunks found");

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst().fileName()).isEqualTo("portfolio-rag-retrieval-empty.md");
    assertThat(results.getFirst().title()).contains("RAG");
  }

  private Incident paymentIncident() {
    return new Incident(
        1L,
        "INC-20260616-0001",
        "payment-service 支付回调超时",
        "payment-service",
        "/api/payment/callback",
        "P2",
        "OPEN",
        "DEMO",
        "trace-payment-timeout-001",
        "TimeoutError",
        "5 分钟内 500 错误率升高，p95 延迟升至 3200ms",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }

  private Incident portfolioRagIncident() {
    return new Incident(
        2L,
        "INC-20260704-0002",
        "ai-agent-portfolio RAG 检索无结果",
        "ai-agent-portfolio",
        "/api/chat/stream",
        "P2",
        "OPEN",
        "GRAFANA",
        "trace-rag-empty-001",
        "RetrievalEmptyException",
        "知识库问答召回 chunk 数量为 0，用户反馈答案没有引用资料",
        LocalDateTime.now(),
        LocalDateTime.now(),
        null
    );
  }
}
