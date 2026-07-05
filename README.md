# Payment Ledger + SRE Observability Stack

A double-entry **payment ledger service** (Java 25, Spring Boot 3, Postgres, Liquibase) wired into a
production-shaped observability stack: metrics via Prometheus, a provisioned RED dashboard in Grafana, and
distributed tracing through OpenTelemetry into Tempo. Includes a chaos endpoint so you can stage an incident and
practice the full detect → localize → verify-recovery loop.

```
  ledger-service (Spring Boot, :8080)
    │
    ├── /actuator/prometheus ──────────────► Prometheus ──┐
    │        (Micrometer metrics, scraped)                │
    │                                                     ▼
    └── OTLP spans ──► OTel Collector ──► Tempo ──────► Grafana
             (Micrometer Tracing bridge)     (traces)   (single pane)
```

Metrics are scraped directly by Prometheus (simple, bulletproof). Traces flow through the OpenTelemetry Collector —
the same pattern you'd run in prod, where the collector does batching, sampling, and fan-out so the app never talks
to a vendor backend directly.

## The payment domain

`ledger-service/` is a minimal but honest core of a wallet/fintech system:

- **Accounts** (treasury, merchant, customer wallets) hold balances in minor units (cents). Schema and seed data
  live in Liquibase changesets (`ledger-service/src/main/resources/db/changelog/`), applied on startup; seed wallets
  are funded through real journal entries, so every balance is backed by postings from the first request.
- **A payment posts a balanced journal entry** — a debit leg and a credit leg that sum to zero. Money is never
  created or destroyed; the `postings` table sums to zero at all times (double-entry bookkeeping).
- **Idempotency keys** dedupe retried payments. A replay returns the original entry with HTTP 200 (vs 201) and
  increments `ledger_idempotency_hits_total` — observability tied directly to correctness.
- **Errors are RFC-9457 ProblemDetail** everywhere — a custom `ApiExceptionHandler` plus
  `spring.mvc.problemdetails.enabled=true` so framework errors match the same shape.
- **A chaos endpoint** injects latency / error rate into `/api/payments` at runtime, so you can stage an incident
  and diagnose it on the dashboards.

### API

| Method   | Path                 | Notes                                                                         |
|----------|----------------------|-------------------------------------------------------------------------------|
| POST     | `/api/payments`      | `Idempotency-Key` required. 201 created, 200 replayed, 422 insufficient funds |
| GET      | `/api/payments/{id}` | Entry + postings                                                              |
| GET      | `/api/accounts`      | All accounts + balances                                                       |
| GET      | `/api/accounts/{id}` | Balance + 10 most recent postings                                             |
| GET/POST | `/api/chaos`         | `{"latencyMs": 800, "errorRate": 0.3}` to break things; zeros to heal         |

## Quickstart

1. **Bring up Postgres + the observability stack:**
   ```bash
   docker compose up -d
   ```

2. **Run the ledger service** (Maven wrapper bootstraps everything; needs JDK 25):
   ```powershell
   cd ledger-service
   .\mvnw.cmd spring-boot:run
   ```
   In IntelliJ you can instead just run `LedgerApplication`.

   **Or run everything in Docker** (multi-stage build, non-root runtime image):
   ```bash
   docker compose --profile app up -d --build
   ```

3. **Generate traffic:**
   ```powershell
   .\scripts\loadgen.ps1 -DurationSeconds 300
   ```

4. **Open the tools:**
    - Grafana → http://localhost:3000 (anonymous admin, no login). The **Ledger — RED + Domain** dashboard is
      provisioned automatically: http://localhost:3000/d/ledger-red
    - Prometheus → http://localhost:9090
    - Traces: Grafana → **Explore → Tempo**.

Verify the app side is live: http://localhost:8080/actuator/prometheus should return a wall of metrics, and
Prometheus → Status → Targets should show `ledger-service` as UP.

## Dashboards as code

Grafana is fully provisioned from this repo — no clicking:

- `grafana/provisioning/datasources/` registers Prometheus and Tempo (with stable UIDs so dashboard JSON can
  reference them).
- `grafana/provisioning/dashboards/` + `grafana/dashboards/ledger-red.json` load the RED dashboard on startup:
  request rate, error rate, and p95 per endpoint on the top row; postings throughput, idempotency replays, and
  posting p99 (domain metrics from `LedgerMetrics`) below.

Delete the Grafana container and bring it back — the dashboard is still there. That's the point.

## SRE exercise: stage an incident

1. Start the load generator and watch baseline traffic on the RED dashboard.
2. Break the service:
   ```powershell
   Invoke-RestMethod -Method Post http://localhost:8080/api/chaos `
     -ContentType application/json -Body '{"latencyMs": 800, "errorRate": 0.3}'
   ```
3. Watch the dashboard degrade: 5xx rate climbs, p95/p99 latency jumps.
4. Drill down: in **Explore → Tempo**, find a slow `POST /api/payments` trace and see where the time went.
5. "Fix" it and watch recovery:
   ```powershell
   Invoke-RestMethod -Method Post http://localhost:8080/api/chaos `
     -ContentType application/json -Body '{"latencyMs": 0, "errorRate": 0}'
   ```

This is the full incident loop in miniature: detect on metrics, localize with traces, verify recovery on the same
dashboards.

## Tests & CI

The suite is primarily **controller-level integration tests** (`*IT`, run by Failsafe) that boot the full app on a
random port against a **Testcontainers Postgres** (Liquibase applies schema + seed data), send raw JSON over HTTP,
and assert on the wire response — including the RFC-9457 ProblemDetail error shape. Unit tests (`*Test`, Surefire)
exist only for pure domain logic that tests without mocks.

```powershell
cd ledger-service
.\mvnw.cmd test     # unit tests only (no Docker needed)
.\mvnw.cmd verify   # + integration tests (needs Docker for Testcontainers)
```

Reusable scaffolding: `TestContainerConfig` (singleton container, `@DynamicPropertySource`), `BaseControllerIT`
(RestClient that never throws on 4xx/5xx, `assertSuccess`/`assertProblem` helpers), and `testutil/`
(`TestConstants`, `TestAssertions`).

GitHub Actions (`.github/workflows/ci.yml`) runs the full `verify` — including the Testcontainers ITs — on every
push and pull request.

## Design decisions

- **Prometheus / Micrometer** — metrics are pull-based: Prometheus scrapes `/actuator/prometheus`. Micrometer gives
  HTTP RED metrics for free; on top of that `LedgerMetrics` adds domain counters and a latency timer with
  percentile buckets so p95/p99 can be computed server-side with `histogram_quantile`.
- **Custom metrics tied to correctness** — the idempotency-hit counter shows how often the ledger deduplicated a
  retried payment instead of double-posting. It's a business-correctness signal, not just plumbing.
- **Idempotency under race** — the read-check in `PaymentService` is an optimization; the unique constraint on
  `idempotency_key` is the guarantee. Two concurrent retries → one insert wins, the loser catches the constraint
  violation and returns the winner's entry.
- **Liquibase owns the schema** — `ddl-auto: validate`, so Hibernate only checks. Migrations are versioned,
  reviewable, and identical across dev, Testcontainers, and any future environment.
- **Chaos as a HandlerInterceptor, not a servlet filter** — deliberately, so chaos-induced 503s keep their proper
  `uri` tag in metrics instead of collapsing into an untagged bucket.
- **OpenTelemetry via a collector** — the app is instrumented with the Micrometer Tracing OTel bridge and exports
  OTLP. It doesn't know about Tempo; it talks to an OTel Collector, which decouples instrumentation from the
  backend. Swapping Tempo for Datadog or Jaeger is a collector config change, not a code change. The collector is
  also where batching, tail sampling, resource attributes, and fan-out belong in prod.
- **Code-level instrumentation over the zero-code Java agent** — the OTel Java agent
  (`-javaagent:opentelemetry-javaagent.jar`) could auto-instrument everything, but the explicit setup keeps every
  layer visible and understandable.

## Starter PromQL

The provisioned dashboard is built from these; paste them into Grafana Explore to go deeper.

- **Request rate (RED — Rate):**
  `sum(rate(http_server_requests_seconds_count[1m])) by (uri)`
- **Error rate (RED — Errors):**
  `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))`
- **p95 latency (RED — Duration):**
  `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))`
- **Postings throughput:**
  `rate(ledger_postings_total[1m])`
- **Idempotency hit rate:**
  `rate(ledger_idempotency_hits_total[1m])`
- **Posting latency p99:**
  `histogram_quantile(0.99, sum(rate(ledger_posting_latency_seconds_bucket[5m])) by (le))`

(Naming gotcha worth knowing: Micrometer's `ledger.postings.created` becomes `ledger_postings_total`, not
`ledger_postings_created_total` — the Prometheus Java client 1.x strips a trailing `created` because `_created` is
reserved by the OpenMetrics spec for counter-creation timestamps. `ledger.idempotency.hits` maps normally to
`ledger_idempotency_hits_total`.)

## Operational notes

- **Skip the collector** (if it misbehaves): point the app straight at Tempo with
  `management.otlp.tracing.endpoint: http://localhost:4318/v1/traces` — Tempo accepts OTLP directly. You lose the
  batching/sampling/fan-out layer.
- **macOS/Windows** — `host.docker.internal` resolves automatically; the `extra_hosts` line in docker-compose is
  only strictly needed on Linux.
- **Running the app in compose** (`--profile app`) publishes :8080, so the existing Prometheus scrape target
  (`host.docker.internal:8080`) keeps working unchanged.
- **Postgres state persists** in the `pgdata` volume across restarts; Liquibase changesets are tracked in
  `databasechangelog` and only run once. To reset the world completely:
  `docker compose down -v && docker compose up -d`.
