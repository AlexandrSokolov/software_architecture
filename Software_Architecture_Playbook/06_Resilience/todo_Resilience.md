# 05_Reliability / 3_Resilience

**Question it answers:** A dependency is slow, down or overloaded — or I am. How does the caller protect itself,
and how does the server protect itself so one failure doesn't take everything down?

**Sources:**
- Nygard, *Release It!* 2nd ed. — stability antipatterns and patterns (origin of circuit breaker).
- Schneider, *SRE with Java Microservices* — ch. 7 (Traffic Management).
- Google, *Site Reliability Engineering* — ch. "Handling Overload", "Addressing Cascading Failures".
- AWS Builders' Library — "Timeouts, retries, and backoff with jitter", "Using load shedding to avoid overload",
  "Avoiding insurmountable queue backlogs", "Making retries safe with idempotent APIs".

---

## 1. How failures spread (the problems)

- [ ] Integration points: every remote call is a place to fail (Nygard).
- [ ] Slow responses are worse than failures: blocked threads, exhausted pools.
- [ ] Cascading failure: one slow dependency → caller's threads stuck → caller fails → its callers fail.
- [ ] Chain reactions: one instance dies, load moves to the rest, they die too.
- [ ] Retry storms / retry amplification: retries at 3 layers × 3 attempts = 27× load.
- [ ] Thundering herd: everyone retries or reconnects at the same moment; cold cache after restart.
- [ ] Unbalanced capacities: a big front end overwhelms a small back end.
- [ ] Unbounded result sets and unbounded queues (latency grows without limit).
- [ ] Self-denial: your own marketing campaign / batch job overloads you.
- [ ] Metastable failure: the system stays broken after the trigger is gone (retries keep it overloaded).

## 2. Client side — protecting the caller

- [ ] **Timeouts:** connect vs read vs total; choose from the dependency's p99; every call needs one.
- [ ] **Deadline propagation:** pass the remaining time budget down the call chain; don't work on requests the
      caller has already abandoned.
- [ ] **Retries:** which errors are retryable (5xx, timeout, 429 vs 4xx); retry only idempotent operations
      (link `2_Cross-System_Consistency` §4).
- [ ] **Exponential backoff + jitter:** full, equal, decorrelated jitter — why jitter matters.
- [ ] **Retry budgets:** cap retries as a % of traffic; retry at one layer only; honour `Retry-After`.
- [ ] **Circuit breaker:** closed / open / half-open; failure-rate and slow-call thresholds; one breaker per
      dependency; what to return while open.
- [ ] **Fallbacks / graceful degradation:** cached or stale data, defaults, feature off — and when a fallback
      hides a real outage.
- [ ] **Bulkheads:** separate thread pools or semaphores per dependency; separate connection pools.
- [ ] **Hedge requests:** send a second request after p95 to cut tail latency; cost vs gain (link tail latency
      amplification in `03_System_Foundations`).
- [ ] **Fail fast:** check preconditions (breaker open, no capacity) before doing work.

## 3. Load balancing

- [ ] Platform vs gateway vs client-side load balancing.
- [ ] Algorithms: round robin, least outstanding requests, join the shortest queue, power of two choices.
- [ ] Health checks and instance probation (take a bad instance out, bring it back slowly).
- [ ] Knock-on effects of smarter balancing (SRE with Java Microservices ch. 7).

## 4. Server side — protecting yourself from overload

- [ ] **Rate limiting:** token bucket, leaky bucket, fixed vs sliding window; per-client quotas; `429` +
      `Retry-After`; distributed rate limiting (Redis) vs per-instance.
- [ ] **Load shedding:** reject early and cheaply when over capacity; prioritise critical over sheddable
      traffic; drop requests whose deadline already passed.
- [ ] **Adaptive concurrency limits:** limit in-flight requests instead of requests/second; Little's law; AIMD /
      Vegas-style limits (Netflix concurrency-limits).
- [ ] **Backpressure:** bounded queues, push back to the caller instead of buffering; reactive streams.
- [ ] Queue choice under overload: bounded, LIFO vs FIFO, queue timeouts.
- [ ] **Queue-based load leveling:** put a broker between caller and worker; consumer lag as a buffer — and its
      limit (backlog you can never catch up on).
- [ ] **Pool sizing:** Tomcat max threads, DB connection pool, HTTP client pool; how they interact.
- [ ] **Virtual threads:** no natural limit from the thread pool any more → explicit semaphores / concurrency
      limits needed.
- [ ] **Health probes under load:** readiness drops traffic when overloaded; liveness must not depend on
      downstream services (cascading restarts). Link `08_Operability/4_Infrastructure`.
- [ ] **Autoscaling:** helps with slow growth, not spikes; JVM cold start; scale-out can overload the shared DB.
- [ ] Gateway-level protection: rate limits, auth before work, request size limits.

## 5. Verifying resilience (practice)

- [ ] Fault injection: Toxiproxy (latency, drops), WireMock faults, killing containers.
- [ ] Load testing: find the breaking point; coordinated omission in load tools (link `1_Monitoring`).
- [ ] Chaos engineering: principles, small blast radius.
- [ ] Practice project: each pattern **without → with**, watched in Grafana (pool usage, latency, error rate,
      breaker state).

## 6. Tools (Java / Spring)

- [ ] Resilience4j: Retry, CircuitBreaker, Bulkhead, RateLimiter, TimeLimiter; order of decorators.
- [ ] Spring: Spring Retry, Spring Cloud Circuit Breaker; built-in resilience in Spring Framework 7
      (`@Retryable`, `@ConcurrencyLimit`) — check current status before writing cards.
- [ ] Resilience in a service mesh (Envoy/Istio): retries and breakers outside the code — pros and cons.

---

## Existing files to move here

- `05_Integration_and_Event_Driven/6_Resilience/todo.md`.

## Not here

- Idempotency keys, outbox, unknown-outcome handling → `2_Cross-System_Consistency`.
- Metrics and alerts for these patterns → `08_Operability/1_Monitoring`.
- Graceful shutdown, deployment strategies → `08_Operability/4_Infrastructure`.
- Cache stampede as a design challenge → `09_Architecture_Traps` (links here).
