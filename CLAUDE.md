# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A double-entry payment ledger (`ledger-service/`, Java 25 + Spring Boot 3) wired into an observability stack for SRE practice. Two halves:

- **docker-compose (repo root)** runs the infrastructure: Postgres, Prometheus, Tempo, OTel Collector, Grafana. Config files (`prometheus.yml`, `tempo.yaml`, `otel-collector-config.yaml`, `grafana/provisioning/`) live at the root.
- **ledger-service runs on the host** (not in compose), on :8080. Prometheus scrapes it via `host.docker.internal:8080`; the app exports OTLP spans to the collector, which forwards to Tempo. Grafana (:3000, anonymous admin) queries both.

## Commands

There is no `mvn` on PATH — use the wrapper (or IntelliJ's bundled Maven). All Maven commands run from `ledger-service/`. Requires JDK 25.

```powershell
docker compose up -d              # infra first (Postgres is the app's datasource)
cd ledger-service
.\mvnw.cmd spring-boot:run        # run the app (or run LedgerApplication in IntelliJ)

.\mvnw.cmd test                   # unit tests only (*Test, Surefire) — no Docker needed
.\mvnw.cmd verify                 # + integration tests (*IT, Failsafe) — needs Docker
.\mvnw.cmd test -Dtest=LedgerDomainTest                          # single unit test
.\mvnw.cmd verify -Dit.test=PaymentControllerIT -Dtest=skip      # single IT
```

- `scripts\loadgen.ps1 -DurationSeconds 300` generates traffic for dashboard work.
- Full reset (Postgres state persists in the `pgdata` volume): `docker compose down -v && docker compose up -d`.
- **Docker Engine 29+ gotcha:** Testcontainers needs `api.version=1.44` in `src/test/resources/docker-java.properties` (already there). Without it the daemon rejects the handshake with misleading empty 400s.

## Architecture

Package layout under `com.ledger` is strictly by role: `controller/`, `service/<feature>/`, `dto/<feature>/request|response/`, `entity/`, `repository/`, `exception/`, `config/`, `enums/`, `observability/`. Keep new code in this shape.

**Ledger invariants (the core of the domain):**
- Money moves only via balanced journal entries: a `JournalEntry` holds two `Posting`s that sum to zero. `PaymentService.post()` throws if the sum is nonzero; the `postings` table sums to zero at all times.
- Liquibase owns the schema (`ddl-auto: validate` — Hibernate only checks). Changesets in `src/main/resources/db/changelog/`; seed wallets are funded through real journal entries, not raw balance inserts.
- Idempotency: the read-check in `PaymentService.createPayment` is an optimization; the **unique constraint on `idempotency_key` is the guarantee**. On a concurrent race, the loser catches `DataIntegrityViolationException` and returns the winner's entry. Replays return 200 (vs 201 for new) and increment the idempotency-hit counter.

**Observability:**
- `LedgerMetrics` holds the domain metrics (posting counter/timer/summary, idempotency hits). Actuator provides `http_server_requests_*` for free.
- Metric-name gotcha: Micrometer's `ledger.postings.created` surfaces in Prometheus as `ledger_postings_total`, **not** `ledger_postings_created_total` — the Prometheus client strips a trailing `created` (reserved by OpenMetrics).
- Chaos injection (`/api/chaos` sets latency/error rate on `/api/payments`) runs as a `HandlerInterceptor` registered in `WebConfig`, deliberately not a servlet filter, so chaos-induced 503s keep their proper `uri` tag in metrics.
- Errors are RFC-9457 ProblemDetail everywhere: `ApiExceptionHandler` plus `spring.mvc.problemdetails.enabled=true` so framework errors match.

## Tests

IT-first strategy: controller-level integration tests (`*IT`) are the primary suite — they boot the full app on a random port against a singleton Testcontainers Postgres (Liquibase applies schema + seed data), send raw JSON over HTTP, and assert on the wire response including ProblemDetail shape. Unit tests (`*Test`) exist only for pure domain logic that needs no mocks.

Reusable scaffolding: `TestContainerConfig` (singleton container, `@DynamicPropertySource`, `withReuse(true)`), `BaseControllerIT` (RestClient that never throws on 4xx/5xx; `assertSuccess`/`assertProblem` helpers; subclasses supply `controllerBasePath()`), and `testutil/` (`TestConstants`, `TestAssertions`). ITs disable trace sampling via a test property.
