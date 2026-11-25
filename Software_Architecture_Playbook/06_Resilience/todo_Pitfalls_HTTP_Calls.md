# 06_Resilience — pitfalls from practice: outgoing HTTP calls (timeouts, retry, circuit breaker, pools)

**Question it answers:** My service calls a REST API. Which defaults are wrong, where do hidden retries and hidden
limits come from, and how do retry and circuit breaker interact?

**Source:** `developer-pitfalls.md` (draft v4) §3, §4, §5.1–5.8, §13.6; login-tracking assessment
(login-processing-service: `TrackLoginClientConfig`, `TrackLoginClient`, `settings/http-client.yaml`,
`settings/timeouts.yaml`, `TrackLoginApiTest`, `TrackLoginClientTest`).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Timeouts

- [ ] **No explicit timeouts** — client defaults are long or infinite; a slow downstream blocks threads (and Kafka
      consumers) for minutes. Do: connect + read timeout per downstream.
      Did: `spring.http.serviceclient.track-login.connect-timeout / read-timeout` (2 s / 3 s).
- [ ] **Choose a timeout from real latency** — high percentile (p99/p99.9) + margin, not "long just in case";
      a long timeout holds a thread and a connection.
- [ ] **Verify both sides of the limit** — WireMock `withFixedDelay` above the timeout → `ResourceAccessException`;
      below → success.
- [ ] **The pool wait is a timeout too** — waiting for a free connection (connection request timeout) is outside
      connect/read timeouts (§5).
- [ ] **Downstream's own budget below the caller's timeout** — DB connection 1 s + statement 1.5 s, below the
      caller's 3 s read timeout; PostgreSQL has no statement timeout by default, Hikari waits 30 s by default.
      Hibernate's query timeout does not cover flush INSERTs → `statement_timeout` server-side.
- [ ] **Frameworks that call HTTP for you use their own client** — your timeouts don't reach it (OAuth2 token
      requests, JWKS download) → `todo_Pitfalls_Auth_Server_Dependency.md`.
- [ ] Time budget / deadline for many calls → `todo_Pitfalls_Kafka_Consumer_Resilience.md` §2.

## 2. Status semantics and exception types

- [ ] **Success semantics are explicit** — 204 or 3xx treated as success when the contract says "only 200".
      Did: a status handler for non-error, non-200 statuses; 4xx/5xx keep Spring's exceptions.
- [ ] **Know the exception tree** — `HttpClientErrorException` (4xx), `HttpServerErrorException` (5xx),
      `ResourceAccessException` (I/O, timeout, reset, no pooled connection) — all `RestClientException`.
- [ ] **Connection reset mid-request** — transient (`ResourceAccessException`); test with
      `Fault.CONNECTION_RESET_BY_PEER`.
- [ ] **`CallNotPermittedException` is not a `RestClientException`** — a `catch (RestClientException)` misses it.
- [ ] **OAuth2 failure is not a `RestClientException`** — token failure throws `OAuth2AuthorizationException`
      before the API request.

## 3. Retry

- [ ] **Hidden retries in the HTTP library** — Apache HttpClient 5 retries some requests itself (503); attempts
      multiply. Do: retries in **one** layer. Did: `HttpClientBuilder::disableAutomaticRetries` via
      `ClientHttpRequestFactoryBuilder`, shared with the token client.
- [ ] **Annotation silently does nothing / self-invocation** → `02_Application_Architecture/todo_Pitfalls_Application.md` §2.
- [ ] **Transient vs permanent (the hard part)** — retry 5xx and I/O; never plain 4xx. Debatable: 500 (may be a
      bug) vs 502/503/504. Configure `retry-exceptions` explicitly, document the decision.
- [ ] **429 is a transient 4xx** — retry honouring `Retry-After` if a gateway rate-limits.
- [ ] **401 with a cached token** — "retriable after refresh", not a plain retry →
      `todo_Pitfalls_Auth_Server_Dependency.md`.
- [ ] **`max-attempts` counts the first call** — "retry 3 times" = `max-attempts: 4`.
- [ ] **Backoff + jitter** — exponential backoff; jitter when many instances retry the same downstream.
      Did: 500 ms × 2ⁿ, `randomized-wait-factor: 0.5`.
- [ ] **Jitter raises the worst case** — max pause = `wait × multiplierⁿ × (1 + factor)`; recompute the chain
      (3.5 s → 5.25 s of backoff per event).
- [ ] **`@Retry` repeats the whole method** — every step inside, including calls that already succeeded;
      non-idempotent steps run twice. Do: one external action per retried method, or all of them idempotent.
      Did: token (cached) + one idempotent call → safe; no separate retry config for Keycloak needed.
- [ ] **Total time budget** — attempts × timeout + backoffs must fit the outer limit (caller timeout,
      `max.poll.interval.ms`) → `todo_Pitfalls_Kafka_Consumer_Resilience.md`.
- [ ] **A green test for the wrong reason** — passed because the library retried; always test both boundaries:
      succeeds on the last attempt **and** gives up after N, plus **no** retry on 400.
- [ ] **Choosing the tool: Resilience4j vs Spring Framework 7 core**
  - [ ] Resilience4j: retry + circuit breaker + bulkhead + rate limiter, YAML config, ready Micrometer metrics.
  - [ ] Spring 7 `@Retryable` / `@ConcurrencyLimit` / `RetryTemplate`: no extra dependency, **no circuit breaker**,
        ready retry metrics — verify for the version used.
  - [ ] Off-by-one when switching: Spring `maxRetries = 3` excludes the first call; R4j `max-attempts: 4` includes it.
  - [ ] Jitter units: Spring `jitter` = absolute ms; R4j `randomized-wait-factor` = fraction.
  - [ ] Did: "Spring 7 has retry in core, but no circuit breaker — I need both, plus metrics" → Resilience4j.

## 4. Circuit breaker

- [ ] **Only transient errors open the breaker** — a stream of 400s (client bug) must not block valid requests;
      `record-exceptions` = the same transient set as retry.
- [ ] **Aspect order Retry ↔ CircuitBreaker** — R4j default: Retry outside, breaker inside → every attempt
      recorded, the window fills `max-attempts` times faster; once open, the retry stops
      (`CallNotPermittedException` not retried).
- [ ] **Window and minimum calls** — no evaluation before `minimum-number-of-calls`; window 10 with 4 attempts per
      call opens after ~3 failing calls. Did: test asserts exactly 10 requests, not 12.
- [ ] **Retry outside meets HALF_OPEN** — one failing event can use all trial slots and reopen; parallel threads
      get `CallNotPermittedException`. Acceptable: still down → reopen is right; back → first attempt closes it.
      Alternative: breaker outside retry (counts events, but retries keep hitting a dead service).
- [ ] **One breaker per downstream** — a dead A must not block B.
- [ ] **How the breaker tests recovery** — no separate probe: in HALF_OPEN the next N **real** calls go through.
- [ ] **Shared breaker state across tests** — singleton in the cached context; `reset()` in `@BeforeEach`.
- [ ] **Open breaker with a message consumer = wrong results** → `todo_Pitfalls_Kafka_Consumer_Resilience.md`.

## 5. Connection and thread pools

- [ ] **Default connection pool is a hidden limit** — Apache HttpClient 5: 5 per route, 25 total; more concurrent
      callers wait for a connection outside your timeout chain. Do: size `maxConnPerRoute`, set the pool wait,
      add it to the chain. Did: enough for 3 listener threads, listed in Known limitations.
- [ ] **Threads vs connections — two formulas** — threads: roughly `cores × (1 + wait / compute)`;
      connections: as many as requests in flight at the same time.
- [ ] **Synchronous calls** — one connection per thread at a time; pool ≈ concurrency + small margin
      (50 threads → ~50 connections, a pool of 100 leaves 50 unused).
- [ ] **Asynchronous calls** (`WebClient`, `CompletableFuture`, fan-out) — few threads, many in flight: size by max
      concurrent requests and cap it (semaphore / bulkhead), or the client floods the downstream.
- [ ] **Database pools** — sum over instances: `instances × pool size` must stay below `max_connections`
      (PostgreSQL default 100); more connections ≠ more throughput; often 10–20, PgBouncer for many instances.
- [ ] **Chain for a synchronous Kafka consumer** — partitions owned ≈ `concurrency` ≈ HTTP pool per route.
- [ ] **Bulkhead adds nothing with one caller and one call per record** — concurrency already caps the
      downstream; pays off with several callers, several downstreams or heavy non-call work.

## 6. Where resilience lives

- [ ] **Resilience inside the contract interface** — `@Retry` on the shared `@HttpExchange` couples the contract to
      Resilience4j. Did: thin wrapper `@Service` holds the annotations.
- [ ] **Configuration next to each other** — what is retried (`http-client.yaml`) and how long
      (`timeouts.yaml`, with the worst-case formula).

---

## Covered elsewhere

- Patterns in general (bulkheads, hedging, retry budgets, load shedding) → `todo_Resilience.md` §2, §4.
- Resilience4j vs Spring tools → `todo_Resilience.md` §6 (this file has the practical traps).
- Breaker and retry metrics → `09_Operability/1_Monitoring/todo_Pitfalls_Monitoring.md`.
