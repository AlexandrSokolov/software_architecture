# 09_Operability / 1_Monitoring — pitfalls from practice: reading metrics

**Question it answers:** The metrics are there — why do they sometimes show nothing, the wrong thing, or hide a
failure, and what should be alerted on in a consume → call → publish service?

**Source:** `developer-pitfalls.md` (draft v4) §6.7, §6.10, §6.23, §9.1, §14.2, §14.6; login-tracking assessment
(README "Observability → Where to look": PromQL for HTTP, Resilience4j, Kafka).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Reading metrics

- [ ] **A timer becomes `_count`, `_sum`, `_max`** — average = sum / count over a window.
- [ ] **Counters as a rate** — `rate(x_total[1m])` for load, `increase(...[1h])` for counting in a demo;
      `rate` handles counter resets after restarts; fractional values at the start of a series are
      extrapolation by `increase`.
- [ ] **Metrics appear only after first use** — `http.client.requests`, breaker gauges: empty right after start is
      not a broken scrape.
- [ ] **State gauges** — `resilience4j_circuitbreaker_state` has one series per state (1 = current); `== 1` keeps
      only the current one.
- [ ] **`job` vs `name` labels** — `job` from the scrape config (which service), `name` from the annotation
      (unique only within one service).

## 2. What the metrics do NOT show

- [ ] **Server vs client metrics see different things** — requests that never reached a stopped server exist only
      in the client's `http_client_requests_seconds` (`status="CLIENT_ERROR"` = no HTTP response, reason in
      `exception`).
- [ ] **Calls rejected by an open breaker appear in neither** — only in `not_permitted_calls`.
- [ ] **"No errors in the metrics" can mean "not measured"** — hand-built clients without `ObservationRegistry`
      (OAuth2 token requests) → `3_Instrumentation/todo_Pitfalls_Instrumentation.md`.
- [ ] **Unbounded label values** — `customerId` as a tag explodes cardinality; never personal or per-entity ids.

## 3. Free metrics worth knowing

- [ ] `spring_kafka_listener_seconds` — per record, tagged success/failure + exception (failure = went to the
      error handler / DLT).
- [ ] `kafka_consumer_fetch_manager_records_lag` — lag per partition; `records_consumed_total` per topic.
- [ ] `kafka_producer_topic_record_send_total` / `_error_total` — results and DLT publishes, failed publishes.
- [ ] `resilience4j_retry_calls_total` by `kind` (`successful_without_retry`, `successful_with_retry`,
      `failed_with_retry`, `failed_without_retry`).
- [ ] `logback_events_total` — log lines per level.

## 4. What to alert on (this kind of service)

- [ ] **Consumer lag per partition** — not only total; long before retention (data loss) and to catch a hot
      partition.
- [ ] **DLT growth** — a DLT nobody watches is a slower "log and skip".
- [ ] **Breaker open / not-permitted calls**, `failed_with_retry` rate, unsuccessful results.
- [ ] **Counts and rates as metrics, not log lines** — an INFO line per event is cost and noise.
- [ ] **Business metrics as a next step** — results by outcome, duplicates, DLT records. Did: not added, built-in
      metrics already show outcomes, statuses, breaker.

---

## Covered elsewhere

- Metric types, cardinality, PromQL, alerting on symptoms → `todo_Monitoring.md`.
