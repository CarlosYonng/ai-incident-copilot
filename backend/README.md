# Backend

Spring Boot backend for AI Incident Copilot.

## Modules

- `incident`: Incident lifecycle APIs.
- `workflow`: Fixed incident workflow engine and node execution audit.
- `diagnosis`: Diagnosis MCP JSON-RPC client.
- `runbook`: Local Markdown Runbook retrieval.
- `action`: Action proposals and human approval records.
- `metrics`: Incident metric snapshots.
- `report`: Postmortem report generation.
- `audit`: Tool call audit.
- `demo`: Demo incident creation endpoints.
- `system`: Health checks.
- `common`: Shared API response, exceptions, JSON, and CORS.

## Commands

```bash
mvn test
mvn spring-boot:run
```

## Configuration

See root `.env.example` and `src/main/resources/application.yml`.

For one-command local or Docker startup, see root `docs/RUNNING.md`:

```bash
scripts/start-local.sh
scripts/start-docker.sh
```
