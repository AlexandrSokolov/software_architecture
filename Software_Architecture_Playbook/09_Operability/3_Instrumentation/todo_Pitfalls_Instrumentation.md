# 09_Operability / 3_Instrumentation — pitfalls from practice: how telemetry gets out

**Question it answers:** Which layer (Micrometer, Actuator, Prometheus, Grafana, Alloy, Loki) does what — and which
calls silently never get measured?

**Source:** `developer-pitfalls.md` (draft v4) §14.1–14.3, §15.15a (metrics part); login-tracking assessment
(`micrometer-registry-prometheus`, `infrastructure/prometheus`, `infrastructure/alloy/config.alloy`, Grafana
provisioning).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. The layers

- [ ] **Micrometer counts** — current values in memory, reset on restart, no history.
- [ ] **Actuator `/metrics`** shows them as JSON ("now" only, for humans); `micrometer-registry-prometheus` renders
      the same values as text at `/actuator/prometheus`.
- [ ] **Prometheus collects** — scrapes every N seconds (Did: 15 s), stores time series, adds `job` / `instance`.
- [ ] **Grafana stores nothing** — queries Prometheus / Loki; data sources provisioned at startup, no clicking.
- [ ] **"Micrometer counts, Prometheus collects"** — not the other way round.
- [ ] **Prometheus doesn't read `.env`** — target port in `prometheus.yml` must be kept in sync by hand.

## 2. Logs without coupling the app

- [ ] **The app writes to stdout; a collector ships it** — Grafana Alloy (successor of Promtail, EOL March 2026)
      reads container logs via the Docker socket → Loki. No dependency, config or test impact; Loki down ≠ app
      affected. Did: Docker metadata → `container` / `service` labels.
- [ ] **Alternative: an appender in the app** (loki4j) — extra dependency, config, test switches, app coupled to
      log infrastructure.
- [ ] **The Docker socket is root-equivalent** — local only.

## 3. Calls that are not measured

- [ ] **A hand-built `RestClient` has no `ObservationRegistry`** — its calls don't appear in
      `http_client_requests_seconds`. Did: token requests to Keycloak missing from the client metrics.
- [ ] **Do:** build it from Boot's `RestClient.Builder` bean or call `.observationRegistry(registry)`; check
      `/actuator/prometheus` for the token endpoint's `uri`.
- [ ] **General rule** — anything built with `new` / `builder()` instead of the Boot bean may lose observation
      (and other auto-configured behaviour). Check after every hand-built client.

---

## Covered elsewhere

- Micrometer, OTel agent / starters, Collector, choosing an approach → `todo_Instrumentation.md`.
- Why the token client is hand-built → `06_Resilience/todo_Pitfalls_Auth_Server_Dependency.md`.
