# 06_Resilience — pitfalls from practice: a Kafka consumer that calls a slow or dead dependency

**Question it answers:** The consumer's work is a remote call with retries. How do retries, timeouts, the circuit
breaker and Kafka's own limits (poll interval, rebalance, shutdown) push against each other — and how does the
consumer survive an outage without wrong results?

**Source:** `developer-pitfalls.md` (draft v4) §4.6, §5.3, §5.9, §5.11, §6.5, §6.25a, §6.30, §6.33, §14.8–14.9;
login-tracking assessment (`settings/timeouts.yaml`, `LoginTrackingService`, README "Timeouts and graceful
shutdown", "Trade-offs").

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. The poll budget

- [ ] **`max.poll.interval.ms` is a budget for the whole batch** — it covers all records of one poll
      (`max.poll.records`, default 500), not one record. 500 × 15.5 s worst case ≈ 2 h vs a 5 min limit →
      consumer kicked out, rebalance, the batch processed again.
- [ ] **Do:** `max.poll.records × worst case per record` below `max.poll.interval.ms` with margin; set both
      explicitly. Did: 5 × 37.25 s ≈ 3.1 min, below 5 min.
- [ ] **Liveness vs progress** — `session.timeout.ms` / heartbeats (background thread) check "process alive";
      `max.poll.interval.ms` checks "process makes progress". Link:
      `07_Distributed_Data/3_Trouble_with_Distributed_Systems/todo_Trouble_with_Distributed_Systems.md` §8.

## 2. Worst case, per-call timeouts and time budgets

- [ ] **Worst case per record includes every blocking step** — token request, the call, retries, backoff, the
      Kafka publish, database calls. Any step without its own timeout breaks the chain.
      Did: `attempt = token 1+2 s + call 2+3 s = 8 s`; `record = 4 × 8 + 5.25 = 37.25 s`, formula on top of
      `timeouts.yaml`.
- [ ] **Producer timeouts belong to the chain** — `send().join()` can wait `max.block.ms` (60 s) +
      `delivery.timeout.ms` (120 s) with a hanging broker; no data lost, but graceful shutdown ends in SIGKILL.
      Do: set both explicitly (also for the DLT template) and add them. Did: listed in Known limitations, values
      prepared but commented out.
- [ ] **Three different things** — per-call timeout (from real latency) vs hard limits (`max.poll.interval.ms`,
      shutdown chain: exceeding them = another failure mode) vs time budget for the whole operation.
- [ ] **Compute hard limits against the worst case** — failures are correlated: in an incident everything is
      slow at once, exactly when the limit is tested.
- [ ] **Many dependencies → the sum of maxima explodes** — a deadline for the whole operation: each call gets
      `min(own timeout, remaining budget)`, budget used up → fail at once (gRPC: deadline propagation).
      Did: one call per record → honest sum is small; a budget pays off with several calls.
- [ ] Graceful shutdown chain itself → `09_Operability/4_Infrastructure/todo_Pitfalls_Infrastructure.md`.

## 3. Open circuit breaker with a consumer

- [ ] **Marking every message failed while the breaker is open** — a downstream outage turns into thousands of
      wrong results. "Isn't it data loss?" — no Kafka message is lost and consumers learn about the failure,
      but the event is never really processed and never repeated.
      Did: simplified for the assessment (`unsuccessful`), production design written as a TODO.
- [ ] **Production design: backpressure** — breaker event publisher → `pause()` the listener container on OPEN,
      `resume()` on HALF_OPEN / CLOSED (`KafkaListenerEndpointRegistry` by listener id).
- [ ] **Don't commit the event that hit the open breaker** — let `CallNotPermittedException` leave the listener;
      the error handler seeks back, records before it are committed.
- [ ] **With "no retries, straight to DLT" that exception would go to the DLT** — classify it separately: never
      recover, retry without limit (per-exception backoff — check the API of the version used).
- [ ] **Pausing needs the automatic transition** — R4j leaves OPEN only on the next call after the wait; a paused
      listener makes no calls → never resumed (deadlock). Do:
      `automatic-transition-from-open-to-half-open-enabled: true`.
- [ ] **After resume Kafka hands out a whole poll, not one message** — the first event's attempts use the
      HALF_OPEN slots, the others get `CallNotPermittedException` and go back the same way; finer control:
      `pausePartition(...)`.
- [ ] **A paused consumer still polls and heartbeats** — stays in the group, no rebalance.
- [ ] **Safe on crash** — the event is either processed and committed or still in Kafka.
- [ ] **The breaker starts CLOSED** — until it opens, the first events still go through full retries and end as
      failed. Closing that gap ("back to Kafka" after exhausted retries) contradicts "unsuccessful after the 4th
      attempt" — a business decision, not a technical one.

## 4. Dependencies at startup and readiness

- [ ] **Compose waits, Kubernetes doesn't** — `depends_on: service_healthy` is a local convenience; in Kubernetes
      the service starts with dependencies possibly down and must survive it.
      Did: processing service starts without Keycloak (`token-uri`, no discovery) and consumes at once → token
      fails → retry → `unsuccessful` → breaker opens; track service starts without Keycloak but not without
      PostgreSQL (Liquibase at startup).
- [ ] **Decide what to do at startup with a dependency down** — process and fail, keep the listener stopped
      (`auto-startup: false`) until it answers, or pause it via the breaker.
- [ ] **Readiness does not stop a Kafka consumer** — "not ready" removes the pod from the Service (incoming HTTP);
      the consumer pulls itself and keeps consuming. Stop consumption explicitly (pause the container).

## 5. Load grows 10× — what breaks first

- [ ] **Measure first** — consumer lag per partition, latencies, pool usage; then scale the bottleneck.
- [ ] **Walk the chain:**
  - [ ] consumer: useful threads across the group ≤ partitions; adding partitions changes key → partition;
  - [ ] downstream: stateless → more instances behind a load balancer;
  - [ ] database: connections first, then write volume; partitioning / sharding only when measured;
  - [ ] publishing: waiting for the ack per event limits throughput → batching / async send with a later check.
- [ ] **Many callers of one service** — server queues fill (Tomcat `maxThreads`, `maxConnections`, `acceptCount`;
      beyond them TCP refuses, Tomcat itself doesn't answer 503), thread starvation, retry storms, metastable
      failure. Protection: jitter, breaker, load shedding / bulkhead on the server, rate limiting and retry
      budget on the client → `todo_Resilience.md` §1, §4.

---

## Covered elsewhere

- Consumer lag as load leveling and its limit → `todo_Resilience.md` §4.
- Offsets, ack modes, DLT → `05_Consistency/3_Cross-System_Consistency/todo_Pitfalls_Cross-System.md`.
- Rebalance as a failure detector → `todo_Trouble_with_Distributed_Systems.md` §8.
