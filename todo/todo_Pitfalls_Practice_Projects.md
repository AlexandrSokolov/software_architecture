# Practice projects — one feature, one test you can watch

**Goal:** every pitfall from the `todo_Pitfalls_*` files gets a small runnable project where I can **see** it:
which requests go out, how many, when, and what the logs and metrics show. One feature per project — not one
project per layer. Features are combined only where they really affect each other.

**Same frame for every project:**
- A test that fails without the fix and passes with it (IT with Testcontainers / WireMock).
- `docker compose up` to run it by hand; `docker compose pause` / `stop` to break the dependency.
- What to observe: exact request counts (WireMock `verify(n, ...)`), log lines by `messageId`, one Grafana panel.
- Before → after: first the broken default, then the fix.

**Three kinds:**
- **S** — single feature.
- **C** — comparison: two tools or two options for the same job, same test against both.
- **X** — combination: mechanisms that change each other's behaviour; the point is the interaction.

---

## A. HTTP client and retry → `06_Resilience/todo_Pitfalls_HTTP_Calls.md`

- [ ] **A1 (S) Timeouts.** WireMock with `withFixedDelay` just below and just above the read timeout; then
      `docker compose pause` on a real stub. Observe: the call fails at the timeout, not minutes later.
- [ ] **A2 (S) Hidden library retries.** Apache HttpClient 5 default vs `disableAutomaticRetries`; stub answers
      503. Observe: request count doubles with the default.
- [ ] **A3 (S) Status semantics.** 200 / 204 / 3xx / 4xx / 5xx / connection reset → which exception, which outcome.
- [ ] **A4 (C) Retry: Resilience4j vs Spring Framework 7 `@Retryable`.** Same scenario (500 → 503 → reset → 200)
      and "gives up after N". Observe: off-by-one (`max-attempts` vs `maxRetries`), jitter units, what metrics each
      exposes without code.
- [ ] **A5 (S) Retry repeats the whole method.** A retried method with two calls (create + notify), the second
      fails. Observe: the first call is repeated; then split into one action per method.
- [ ] **A6 (S) Annotation does nothing.** Remove `spring-boot-starter-aspectj`; call through `this`. Observe: one
      request, no retry.
- [ ] **A7 (S) Connection pool limit.** 20 concurrent callers, default pool 5 per route, slow stub. Observe: callers
      waiting for a connection outside connect/read timeouts; then size the pool and set the pool wait.
- [ ] **A8 (S) 429 with `Retry-After`.** Stub rate-limits; retry honouring the header vs "never retry 4xx".

## B. Circuit breaker → `06_Resilience/todo_Pitfalls_HTTP_Calls.md` §4

- [ ] **B1 (S) Breaker states.** Count-based window, minimum calls, failure rate. Observe: exact number of
      requests before OPEN; HALF_OPEN trial calls; state gauge in Grafana.
- [ ] **B2 (X) Retry outside vs inside the breaker.** Same failing stub, both orders. Observe: how fast the window
      fills, who uses the HALF_OPEN slots, whether retries keep hitting a dead service.
- [ ] **B3 (S) Only transient errors open it.** A stream of 400s must not open the breaker.

## C. Kafka consumer under a failing dependency → `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md`

- [ ] **C1 (X) Poll budget.** `max.poll.records` × worst case per record vs `max.poll.interval.ms`; make the stub
      slow. Observe: rebalance and reprocessing when the budget is exceeded; the fix by lowering records per poll.
- [ ] **C2 (X) Breaker pauses the listener.** Breaker events → `pause()` / `resume()`, `CallNotPermittedException`
      not committed. Observe: events wait in Kafka (lag grows), no `unsuccessful` results; without
      `automatic-transition` the listener never resumes.
- [ ] **C3 (S) Startup with a dependency down.** Start the consumer before the stub / Keycloak. Compare: process
      and fail vs `auto-startup: false` until the dependency answers.
- [ ] **C4 (X) Time budget for several calls.** Three downstream calls per record, each with its timeout vs one
      deadline for the operation. Observe: worst case with and without the budget.

## D. Consume → call → publish consistency → `05_Consistency/3_Cross-System_Consistency/todo_Pitfalls_Cross-System.md`

- [ ] **D1 (S) Poison pill.** Without `ErrorHandlingDeserializer` (partition blocked forever) → with it + DLT.
      Observe: the next event with the same key is processed; DLT value = original bytes.
- [ ] **D2 (S) Default error handler = log and skip.** No own handler; handler throws. Observe: 10 attempts, then
      the message is gone.
- [ ] **D3 (C) Ack modes.** `BATCH` vs `RECORD`; kill the process (`docker kill`) in the middle of a poll.
      Observe: how many records come again.
- [ ] **D4 (S) Crash between call and publish.** Kill after the REST call. Observe: redelivery → duplicate insert →
      200 → `successful`; then a crash after publish → a second result.
- [ ] **D5 (S) Fire-and-forget publish.** `send()` without `join()` with the broker stopped. Observe: offset
      committed, result lost; then with `join()`.
- [ ] **D6 (C) Offsets in Kafka vs in your own DB.** Same crash test. Observe: duplicates vs none, and what Kafka
      UI / lag metrics show in each case.

## E. Idempotent storage → `05_Consistency/1_DB_Consistency/todo_Pitfalls_Idempotent_Insert.md`

- [ ] **E1 (S) `save()` with an assigned id merges.** Same id twice with different data. Observe: SELECT + UPDATE
      in the SQL log, row overwritten; then `Persistable` → INSERT fails, row unchanged.
- [ ] **E2 (C) Catch the PK violation vs `INSERT ... ON CONFLICT DO NOTHING`.** Observe: exceptions and error lines
      in the logs vs a 0/1 row count.
- [ ] **E3 (S) Outer transaction breaks the catch.** Wrap the call in `@Transactional`. Observe: 500 instead of 200.
- [ ] **E4 (S) Concurrent duplicates.** Two inserts with the same key at once. Observe: the second waits, then 23505.

## F. Database budget and migrations → `04_Data_Layer/1_Data_Modeling/todo_Pitfalls_Schema_&_Migrations.md`

- [ ] **F1 (X) Caller timeout vs DB budget.** Hikari connection timeout + `statement_timeout` vs the caller's read
      timeout; `pg_sleep`. Observe: the server stops working on requests nobody waits for.
- [ ] **F2 (S) Silent migration.** `checkConstraint` attribute vs plain SQL. Observe: insert of an invalid value
      passes / fails.
- [ ] **F3 (S) Pool timeout hits migrations.** A slow migration on the pooled DataSource vs its own connection.

## G. Authorization server as a dependency → `06_Resilience/todo_Pitfalls_Auth_Server_Dependency.md`

- [ ] **G1 (S) Token client without timeouts and with hidden retries.** Default Spring Security client vs own
      client from the same request factory. Observe: 8 vs 4 token requests; a paused Keycloak blocks the thread
      vs fails at the timeout.
- [ ] **G2 (S) Token cache and clock skew.** Token lifetime 2 min, stop Keycloak. Observe: calls succeed until
      `lifetime − skew`, then fail.
- [ ] **G3 (S) Stale token after key change.** Restart Keycloak in `start-dev`. Observe: 401 until expiry; then the
      failure handler + one retry.
- [ ] **G4 (S) JWKS download without timeouts** on the resource server; then own `JwtDecoder`.
- [ ] **G5 (X) Keycloak outage in the same breaker as the API.** Observe: the breaker opens on token failures too.

## H. Security → `11_Security/todo_Pitfalls_Security.md`

- [ ] **H1 (S) Audience check.** Token for another API of the same realm: accepted without `audiences`, 401 with it.
- [ ] **H2 (C) Local validation vs introspection.** Revoke the client in Keycloak. Observe: 200 until `exp` vs 401
      at once; latency per request; Keycloak down → all requests fail with introspection.
- [ ] **H3 (S) Starter locks the app.** Add the OAuth2 client starter without a `SecurityFilterChain`. Observe:
      healthcheck red, Prometheus target down.
- [ ] **H4 (C) Attaching the token.** `@ClientRegistrationId` vs group interceptor. Observe: what the contract jar
      now depends on.
- [ ] **H5 (C) Service-to-service alternatives.** OAuth2 client credentials vs mTLS (optional, later).

## I. Observability and runtime → `09_Operability/*/todo_Pitfalls_*.md`

- [ ] **I1 (S) Unmeasured client.** Hand-built `RestClient` vs Boot's builder. Observe: token endpoint missing /
      present in `http_client_requests_seconds`.
- [ ] **I2 (S) Server vs client metrics.** Stop the downstream. Observe: requests visible only on the client side
      (`CLIENT_ERROR`), breaker rejections in neither.
- [ ] **I3 (X) Graceful shutdown chain.** Slow stub, stop during processing; then shorten one layer. Observe: exit
      code 143 vs 137, log order, lag after restart.
- [ ] **I4 (S) Management port isolation.** Exhaust Tomcat's threads; health on the shared port hangs, on a
      separate port it answers.
- [ ] **I5 (S) Readiness doesn't stop a consumer.** Mark the pod not ready; the listener keeps consuming.

## J. Contract and tests → `08_Integration/1_Encoding_and_Evolution/todo_Pitfalls_Contracts_&_JSON.md`,
`02_Application_Architecture/todo_Pitfalls_Application.md`

- [ ] **J1 (S) Contract test as JSON.** Rename a record component: object round-trip stays green, JSON test fails.
- [ ] **J2 (S) Unknown fields and enum values.** Producer adds a field / a new enum value; consumer with a
      hand-made `JsonMapper` vs Boot's.
- [ ] **J3 (S) Validation on the client side.** `@Valid` on the shared interface ignored by the proxy; then the
      wrapper with `@Validated`. Observe: no token request, no HTTP for an invalid event.
- [ ] **J4 (S) Order-dependent tests.** Shared breaker / token store in the cached context; change the test order.

---

## Suggested order

Start where the interview topics are strongest and the projects reuse each other:
A1 → A2 → A4 → B1 → B2 → C1 → D1 → D3 → E1 → G1 → I3 → C2. The rest as needed.
