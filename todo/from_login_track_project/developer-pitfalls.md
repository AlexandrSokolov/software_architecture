# Developer Pitfalls & Corner Cases

Living checklist of traps met in real projects. Per item: **Pitfall** → **Why** → **Do** → **Verify**
(**Why** only where it is not obvious).

Tiers inside each section: **Tier 1** — every project, **Tier 2** — common / depends on the stack, **Tier 3** — rare but painful.

_Status: draft v4, 2026-11-22 (grown from the login-tracking assessment). Added: review findings (code review by Claude and Gemini) across sections 1-15._

---

## 1. Build & Parent POM

**Main motive:** one place for everything shared — Spring Boot version, plugin versions, code quality rules,
build rules. Children stay small; an upgrade or a new rule is a one-line change for all projects.

**Design rule:** every feature of the parent must be **overridable or switchable per child**
(a property or a profile), so the parent also fits a legacy project that needs only part of it
(e.g. only quality gates, only the IT profile, an older Java).

### Tier 1 — every project

#### 1.1 Code quality gates live in the parent
- **Do:** JaCoCo (min coverage), SpotBugs + FindSecBugs, PMD — bound to `verify`, so `install` cannot skip them silently.
- **Do:** thresholds and switches as properties (`jacoco.minimum.coverage`, `jacoco.skip`, `spotbugs.skip`, `pmd.skip`); a child overrides, never copies plugin config.
- **Verify:** a child without any plugin section still fails on low coverage / a SpotBugs finding.

#### 1.2 Static analysis false positive
- **Pitfall:** disabling a rule globally to silence one false positive (e.g. SpotBugs `EI_EXPOSE_REP2` on a constructor-injected bean).
- **Why:** a global exclude hides future real issues; a suppression on a method hides new code added there.
- **Do:** `@SuppressFBWarnings` on the **field**, with a `justification`; `spotbugs-annotations` as `provided` in the parent.
- **Verify:** the rule still fires on other code.

#### 1.3 Integration tests silently never run
- **Pitfall:** `*IT` classes exist, the build is green, nobody runs them (a red IT went unnoticed).
- **Why:** Surefire includes only `*Test`, `*Tests`, `Test*`, `*TestCase`.
- **Do:** Failsafe in a profile (`-Pintegration-tests`); run the profile in CI.
- **Verify:** the Failsafe section in the build output lists the IT classes.

#### 1.4 Overriding a vulnerable transitive version
- **Pitfall:** pinning a fix by declaring the dependency directly breaks BOM alignment (half of Jackson upgraded).
- **Do:** override the Boot property (`jackson-bom.version`, `logback.version`) with a comment: CVE id + "remove once Boot manages X".
- **Verify:** `./mvnw dependency:tree`; the scanner warning disappears.

#### 1.5 Java version: enforced but flexible
- **Pitfall:** the build runs on a wrong JDK (IDE terminal `JAVA_HOME`, system Maven) and fails cryptically.
- **Why:** Maven Wrapper pins Maven, not the JDK.
- **Do:** Enforcer `requireJavaVersion [${java.version},)` driven by the **property**: a legacy child sets its own `java.version`. Always build via `./mvnw`.
- **Verify:** a wrong JDK fails at the first step with a clear message.

### Tier 2 — common, depends on the stack

#### 1.6 `@{argLine}` breaks when JaCoCo is skipped
- **Pitfall:** Surefire/Failsafe fail with an unresolved `@{argLine}` once JaCoCo is skipped.
- **Do:** empty `<argLine/>` default in properties.
- **Verify:** `./mvnw verify -Djacoco.skip=true` passes.

#### 1.7 Mockito self-attach warning (JDK 21+)
- **Why:** JDK restricts agents loaded at runtime (JEP 451); a future JDK blocks them.
- **Do:** `maven-dependency-plugin:properties` + `-javaagent:${org.mockito:mockito-core:jar}` in Surefire **and** Failsafe.
- **Verify:** no warning in Maven and in the IDE.

#### 1.7a Null-safety with Spring Framework 7 (JSpecify)
- **Know:** Spring 7 packages are `@NullMarked` (non-null unless `@Nullable`); overriding a Spring method in an unmarked
  package makes IntelliJ warn "not annotated parameter overrides @NullMarked parameter".
- **Do:** `package-info.java` with `@org.jspecify.annotations.NullMarked` (jspecify comes with spring-core) instead of
  `@NonNull` on every parameter; it also highlights `@Nullable` Spring results you must handle.

#### 1.8 Team-wide banned dependencies
- **Do:** Enforcer `bannedDependencies` (e.g. Lombok), with a message explaining why.
- **Verify:** adding it fails the build.

### Tier 3 — rare, but painful when it happens

#### 1.9 Library ships `application.properties`
- **Pitfall:** a generated `application.properties` stays in a library jar and leaks into every consumer (wrong app name in logs).
- **Why:** Spring Boot loads `application.*` from the whole classpath.
- **Do:** libraries never contain `application.*`.
- **Verify:** check the jar contents.

#### 1.10 Parent tied to one Spring Boot version
- **Pitfall:** a legacy project on another Boot version cannot use the parent at all — it inherits `spring-boot-starter-parent`.
- **Do:** if needed, split in two: a Boot-independent **quality parent** (plugins and rules only) and a Boot parent on top of it.
- **Verify:** a non-Boot project builds with the quality parent alone.

#### 1.11 Independent builds: `verify` tests against the last installed contract
- **Know:** without a reactor, children resolve the parent and `login-api` from `~/.m2` — the version of the last
  `install`, not the sources next door. `verify` on a service after a contract change tests the **old** contract.
- **Do:** after changing the parent or the contract, `install` the whole chain (`build.sh` defaults to `clean install`);
  `install` costs only a copy to `~/.m2`.
- **Know:** quality gates bound to `verify` do not run on `package` — `clean package` builds a jar that may fail the gates.

---

## 2. API Contract, DTOs & JSON

**Main motive:** the contract (HTTP interface + DTOs) is the most expensive thing to change —
every consumer depends on it. It must be explicit, tested as JSON, and tolerant to evolution.

### Tier 1 — every project

#### 2.1 Contract tested as JSON, not as Java objects
- **Pitfall:** a renamed field or changed date format passes all Java tests but breaks every consumer.
- **Why:** an object round-trip (serialize → deserialize) is symmetric and hides the change.
- **Do:** test against a fixed contract JSON string: serialization with strict JSONAssert, deserialization with `equals`.
- **Verify:** renaming a record component fails the contract test.

#### 2.2 Unknown fields must not break the reader (forward compatibility)
- **Pitfall:** the producer adds a field — the consumer fails on every message / request.
- **Do:** `FAIL_ON_UNKNOWN_PROPERTIES = false`. Spring Boot's auto-configured mapper sets this; a hand-made `JsonMapper` may not — check.
- **Verify:** a test deserializing JSON with an extra field.

#### 2.3 Unknown enum values
- **Pitfall:** a new client sends `"client": "DESKTOP"` — deserialization fails.
- **Do:** decide explicitly: fallback (`@JsonEnumDefaultValue UNKNOWN` + `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE`) or deliberate rejection (400 / DLT). Never by accident.
- **Verify:** a test with an unknown enum value.

#### 2.4 Personal data leaks through `toString()`
- **Pitfall:** logging a whole DTO writes username and IP to the logs (GDPR).
- **Why:** a record's generated `toString()` contains every field.
- **Do:** log only technical ids (`messageId`); override `toString()` in DTOs with personal data if they may reach logs.
- **Verify:** code review rule: never log a whole DTO.
- **Know:** without any of the below, protection is only a **convention in the code** (assessment: every log line
  writes `messageId` only) — nothing in the logger filters it; one `log.debug("Event: " + event)` leaks it.
- **Idea — annotation + one shared `toString`:** mark personal fields in the contract, mask them generically, no
  hand-written `toString` per DTO:
  ```java
  @Retention(RUNTIME) @Target(RECORD_COMPONENT)
  public @interface Sensitive { }

  public record LoginEvent(UUID customerId, @Sensitive String username, ..., @Sensitive String customerIp, ...) {
    @Override
    public String toString() {
      return SafeToString.of(this);   // one line per DTO
    }
  }

  // Shared utility: record components via reflection, @Sensitive values replaced by "***"
  public static String of(Record r) {
    return Arrays.stream(r.getClass().getRecordComponents())
        .map(c -> c.getName() + "=" + (c.isAnnotationPresent(Sensitive.class) ? "***" : value(c, r)))
        .collect(joining(", ", r.getClass().getSimpleName() + "[", "]"));
  }
  ```
  A record cannot inherit `toString` from a base class, so the one-line override stays; the rules live in the
  annotation. Fits the shared contract: the personal fields are declared where the DTO is defined.
- **Alternative — in the logger:** masking patterns (e.g. IP addresses) in the log encoder before writing; catches
  free text too, but regex-based and costs CPU per line. Defense in depth: both.
- **Pitfall:** exception messages (`e.getMessage()`) can carry payload too — e.g. a deserialization error quoting the
  raw JSON. Do not echo rejected values in error responses (2.16).

#### 2.5 Validation at the boundary
- **Do:** Bean Validation (`@Valid`, `@NotNull`) on incoming DTOs; invalid input → 400, not 500.
- **Pitfall:** validating an IP with `InetAddress.getByName()` — for a hostname it does a **DNS lookup** (latency, SSRF).
- **Do:** `InetAddress.ofLiteral()` (JDK 22+): parses literals only, no network access.
- **Verify:** tests for an invalid IP and for a hostname.

#### 2.6 Timestamps
- **Do:** `Instant`, serialized as ISO-8601 UTC (`2026-11-19T12:43:00Z`); not epoch numbers, not local time without offset.
- **Verify:** the contract test fixes the exact string.

### Tier 2 — common

#### 2.7 Read-modify-write loses fields the client does not know
- **Pitfall:** a client on an older contract reads an object, changes one field, sends it back — the server's newer fields are wiped.
- **Do:** keep unknown fields in a `Map` via `@JsonAnySetter` / `@JsonAnyGetter` and send them back unchanged, or use PATCH with only the changed fields.
- **Verify:** test: JSON with an extra field → DTO → JSON still contains it.

#### 2.8 Shared contract module
- **Do:** HTTP interface (`@HttpExchange`) + DTOs in a separate jar; both sides compile against it. Framework deps `provided`, no `application.*` (see 1.9).
- **Pitfall:** the contract jar is a hidden coupling — a breaking change breaks all consumers at once.
- **Do:** semantic versioning; only additive changes within a major version.

#### 2.9 Minimal HTTP interface
- **Do:** `void` + exceptions for "succeeded or not"; no `ResponseEntity` / `boolean` in the contract. `@RequestBody` is required (Spring does not infer it).
- **Do:** define the success status explicitly (200 vs 201 vs 204) and what the client treats as failure.

#### 2.10 API is explorable locally
- **Do:** OpenAPI + Swagger UI (springdoc), with a way to obtain a local test token, so the API can be tried without writing a client.

#### 2.12 Where validation rules live: contract or server
- **Decide explicitly:** constraints on the **shared contract DTO** vs a **server-only request DTO**.
- **Contract (chosen when client and server share one model):** one set of rules for both sides; a client can
  pre-validate before sending (e.g. a Kafka consumer sends an invalid event to the DLT instead of getting a 400);
  the rules are visible in the contract; springdoc puts them into the OpenAPI schema (`@NotNull` → required,
  `@Size` → maxLength, `@Pattern` → pattern). Dependency: `jakarta.validation-api` as `provided` — annotations only,
  the provider (Hibernate Validator) stays with the server.
- **Server-only DTO:** when the server accepts a different shape than the shared model; costs a duplicated class + mapping
  that can drift apart.
- **Rule:** a constraint goes into the contract only if client and server agree on it; side-specific rules stay on that side.
- **Rule:** validate at every boundary where data is created or changed — the input you receive and the
  output you build (derived DTOs, enriched data). If the same DTO passes through unchanged, the input check is enough.
- **How:** see 2.18 — with the rules in the contract, each boundary needs only annotations, no validation code.

#### 2.13 `@Valid` belongs to the interface, not to the implementation
- **Pitfall:** `@Valid` only on the controller method that implements a shared interface — Bean Validation forbids
  adding parameter constraints / cascading in an implementing method (may fail with method validation).
- **Do:** declare `@Valid` on the interface parameter; HTTP interface clients simply ignore it.

#### 2.14 Three lines of defense for incoming data
- **Request:** `@Valid` → `MethodArgumentNotValidException` → **400** (unknown enum / malformed JSON → `HttpMessageNotReadableException` → 400).
- **Entity:** Bean Validation runs on **pre-insert**, i.e. at flush — the violation surfaces at commit, wrapped in
  `TransactionSystemException` (root cause `jakarta.validation.ConstraintViolationException`); no SQL is sent.
- **Database:** NOT NULL / CHECK / keys → `DataIntegrityViolationException`.
- **Know:** `@Column(nullable = false)` is DDL metadata only — the check before the INSERT comes from `@NotNull`.
- **Pitfall:** two classes named `ConstraintViolationException`: `jakarta.validation` (validation stopped it)
  vs `org.hibernate.exception` (the database rejected it). A wrong auto-import tests the opposite.

#### 2.15 Custom constraints in the contract
- **Do:** `@Constraint(validatedBy = ...)` annotation + `ConstraintValidator` live in the contract module — both are part of
  `jakarta.validation-api`; the provider on whichever side validates finds the validator via `validatedBy`, no registration.
- **Do:** a validator returns `true` for `null` — one constraint, one rule; `@NotNull` decides about null.
- **Example:** `@IpAddress` with `InetAddress.ofLiteral` — host names are rejected, never resolved (no DNS during validation).

#### 2.16 Error responses that say what is wrong
- **Pitfall:** Problem Details out of the box say only "Invalid request content." — the client cannot see which field.
- **Do:** extend `ResponseEntityExceptionHandler#handleMethodArgumentNotValid`: add an `errors` list (field + message) to the
  `ProblemDetail` (RFC 9457 allows extension members). Bean Validation already collects **all** violations (fail slow).
- **Pitfall:** echoing rejected values — they may be personal data (IP, username) and end up in gateway/client logs.
- **Do:** `spring.mvc.problemdetails.enabled: true` — framework-neutral `application/problem+json` instead of Spring Boot's own error JSON.

#### 2.17 Enum values: JSON form vs Java name vs database value
- **Pitfall:** the spec says `web` / `successful`, the code says `WEB` / `SUCCESSFUL` — a reviewer sending the spec's
  value gets a deserialization error (event in the DLT).
- **Pitfall:** renaming the constants to lowercase also changes what `@Enumerated(STRING)` writes to the database —
  a CHECK constraint on `'WEB', ...` then rejects every insert (500).
- **Know:** `name()` and `ordinal()` are final; Jackson uses `name()`, not `toString()`, unless a global feature is set.
- **Do:** decouple the JSON form: `@JsonValue` on a method returning `name().toLowerCase(Locale.ROOT)` (used for writing
  **and** reading) or `@JsonProperty` per constant. Java names and DB values stay as they are.
- **Know:** `Locale.ROOT` matters — with a Turkish default locale `"IOS".toLowerCase()` is `"ıos"`.

#### 2.18 Validating outside the server: listener and client
- **Pitfall:** the client sends data it could have rejected itself — an invalid event costs a token request, a network
  call and a 400 (assessment: no retries, nothing stored, result `unsuccessful`; listed in Known limitations).
- **Pitfall:** `@Valid` on the shared HTTP interface works on the server only (Spring MVC). The generated client proxy
  (`@ImportHttpServices` / `HttpServiceProxyFactory`) ignores it; there is no property to turn it on.
- **Pitfall:** the contract has `jakarta.validation-api` as `provided` (not transitive) — the calling service needs
  `spring-boot-starter-validation`, otherwise there is no provider and nothing is validated.
- **Know:** `@Valid` alone accepts `null` — use `@NotNull @Valid`.
- **Options**, from the earliest boundary:
  - **Kafka listener** (best: rejected before any work): `onLoginEvent(@Payload @NotNull @Valid LoginEvent event)` plus
    either `registrar.setValidator(...)` in a `KafkaListenerConfigurer` (without a validator Spring Kafka ignores `@Valid`)
    or `@Validated` on the listener class (method validation via AOP). Violation → error handler → DLT.
  - **Client wrapper** (simplest and most explicit): the bean that already wraps the generated proxy with `@Retry` and
    `@CircuitBreaker` also gets `@Validated` on the class and `@NotNull @Valid` on the parameter — every rule of the call
    visible in one signature. `ConstraintViolationException` is in neither `retry-exceptions` nor `record-exceptions`:
    no retry, breaker untouched, not caught as a failed call → listener error handler → DLT. No token, no HTTP.
  - **HTTP service group hook** (Spring Framework 7): `groups.forEachProxyFactory((group, factory) ->
    factory.httpRequestValuesProcessor((method, params, args, values) -> ...))` sees the method, the parameter
    annotations and the unserialized arguments — validate there for every interface of the group, contract untouched.
    Own code (~12 lines); worth it only for many interfaces.
  - **`@Validated` on the shared interface** — avoid: it also hits the server. `MethodValidationPostProcessor` looks for
    `@Validated` in the whole type hierarchy (interfaces included), and the controller implements the interface →
    the controller gets an AOP proxy. Result: Spring MVC validates the body while reading the request (validation #1,
    failure → 400), then the proxy validates the same object again when the controller method is called (validation #2).
    Worse: Spring MVC then stops validating `@RequestParam`/`@PathVariable` itself → 500 instead of 400.
    And the contract gets a `spring-context` dependency.
- **Not here:** a `ClientHttpRequestInterceptor` sees serialized bytes, not the object.

### Tier 3 — rare

#### 2.11 Jackson 3 mixed packages
- **Pitfall:** Jackson 3 lives in `tools.jackson.*`, annotations stay in `com.fasterxml.jackson.annotation`; Jackson 2 classes on the classpath compile but are silently ignored.
- **Do:** check imports during a migration; `ObjectMapper` → `JsonMapper`.

---

## 3. HTTP Client

**Main motive:** every remote call can hang, fail half-way or answer something unexpected.
Timeouts, status semantics and *who* retries must be explicit — defaults are rarely right.

### Tier 1 — every project

#### 3.1 No explicit timeouts
- **Pitfall:** a slow downstream blocks threads (and Kafka consumers) for minutes.
- **Why:** client defaults are long or infinite.
- **Do:** set connect and read timeouts explicitly per downstream (`spring.http.serviceclient.<group>.connect-timeout / read-timeout`).
- **Verify:** WireMock with `withFixedDelay` above the timeout → `ResourceAccessException`; below → success.

#### 3.2 Hidden retries in the HTTP library
- **Pitfall:** Apache HttpClient 5 retries some requests itself (e.g. on 503) — attempts multiply with the app's retry; a test passed only by coincidence.
- **Do:** retries live in **one** layer: `HttpClientBuilder::disableAutomaticRetries` via `ClientHttpRequestFactoryBuilder`.
- **Verify:** "gives up after N attempts" test counts exactly N requests in WireMock.
- **Pitfall:** frameworks that make HTTP calls **for you** (e.g. Spring Security's OAuth2 token requests) use their own
  HTTP client — your settings do not reach it. See 15.15a.

#### 3.3 Success semantics are explicit
- **Pitfall:** 204 or 3xx treated as success when the contract says "only 200".
- **Do:** a status handler for non-error, non-expected statuses; 4xx/5xx keep Spring's default exceptions.
- **Know:** `HttpClientErrorException` (4xx), `HttpServerErrorException` (5xx), `ResourceAccessException` (I/O, timeout) — all `RestClientException`.
- **Verify:** tests for 200, 204, 4xx, 5xx, timeout.

### Tier 2 — common

#### 3.4 Missing module silently ignores config
- **Pitfall:** `spring.http.serviceclient.*` properties are ignored → "Target host is not specified".
- **Why:** the auto-configuration lives in `spring-boot-starter-restclient`.
- **Do:** add the starter when using HTTP Service Clients (`@ImportHttpServices`).
- **Verify:** a test against WireMock with the base URL from properties.

#### 3.5 Resilience inside the contract interface
- **Pitfall:** `@Retry` / `@CircuitBreaker` on the shared `@HttpExchange` interface couples the contract to Resilience4j.
- **Do:** a thin wrapper `@Service` in the consumer holds the resilience annotations; the contract stays clean.

### Tier 3 — rare

#### 3.6 Connection reset mid-request
- **Do:** treat as transient (`ResourceAccessException`); test it with WireMock `Fault.CONNECTION_RESET_BY_PEER`.

#### 3.7 Default connection pool is a hidden limit
- **Know:** Apache HttpClient 5 pools 5 connections per route (25 total) by default. More concurrent callers than
  that wait for a connection — and that wait (connection request timeout) is not in your timeout chain.
- **Do:** size `maxConnPerRoute` to the concurrency per instance, set the pool wait explicitly, include it in the chain.

#### 3.8 429 is transient
- **Know:** `429 Too Many Requests` is a 4xx but transient; "never retry 4xx" is wrong for it.
- **Do:** if the downstream (or a gateway) rate-limits, retry 429 honouring `Retry-After`.

#### 3.9 Sizing pools: threads vs connections
- **Know:** two different pools, two different formulas.
  - **Threads** (CPU vs I/O): roughly `cores × (1 + wait / compute)` — I/O-bound work needs many more threads than cores.
    For a Kafka listener the thread count is the `concurrency`, bounded by the partitions it owns.
  - **Connections:** as many as requests can be **in flight at the same time**.
- **Synchronous calls:** a thread sends a request and blocks until the answer — one connection per thread at a time.
  HTTP pool ≈ concurrency + small margin. 50 threads → ~50 connections; a pool of 100 leaves 50 never used.
  The I/O-bound formula is already applied by having 50 threads on a few cores.
- **Chain (synchronous Kafka consumer):** partitions owned by the instance ≈ `concurrency` (6.13) ≈ HTTP pool per route.
  Default Apache pool is 5 per route (3.7): with more threads they wait for a connection, outside connect/read timeouts —
  set the pool size and an explicit connection request timeout, add it to the timeout chain.
- **Asynchronous calls** (`WebClient`, `CompletableFuture`, fan-out): few threads keep many requests in flight → size the
  pool by the maximum concurrent requests, and cap that number explicitly (semaphore / bulkhead), or the client floods
  the downstream.
- **Database:** sum over instances: `instances × pool size < max_connections` (PostgreSQL default 100). More connections
  ≠ more throughput — the database is bound by CPU and disk; keep pools small (often 10–20), PgBouncer for many instances.
  Each service sizes its DB pool by its own parallel requests, not by the caller's partitions.

---

## 4. Retry

**Main motive:** adding `@Retry` takes one line; making it correct is the hard part —
**what** to retry, **how long** in total, and proving it actually runs.

### Tier 1 — every project

#### 4.1 Annotation silently does nothing
- **Pitfall:** `@Retry` compiles, but exactly one request is sent.
- **Why:** annotations work via an AOP proxy; without `spring-boot-starter-aspectj` there is no proxy.
- **Do:** add the starter; **prove** it with a test (and an experiment: remove the dependency → the test must fail).
- **Verify:** WireMock counts attempts.

#### 4.2 Self-invocation bypasses the proxy
- **Pitfall:** calling a `@Retry` method from another method of the same class — no retry.
- **Why:** the call does not go through the proxy.
- **Do:** resilience annotations on a separate bean (the wrapper from 3.5).

#### 4.3 Transient vs permanent classification (the hard part)
- **Do:** retry 5xx and I/O errors (timeouts, resets) only; never 4xx (the request is wrong, a retry won't fix it).
- **Debatable:** 500 (may be a bug → permanent) vs 503/502/504 (clearly transient); 429 — retry respecting `Retry-After`.
- **Do:** configure `retry-exceptions` explicitly; document the decision.
- **Exception — 401 with a cached token:** normally 401 = bad credentials, permanent; repeating with the **same** token
  fails again. But after a key rotation / authorization server restart the token is only **stale**: drop it from the
  cache, get a new one, repeat **once**. 401 again → real problem, stop. "Retriable after refresh", not plain retry (15.23).
- **Verify:** tests: succeeds on the last attempt; gives up after N; **no** retry on 400.

#### 4.4 Retries require idempotency on the receiver
- **Pitfall:** a timeout after the server already stored the data → the retry stores it twice.
- **Do:** the receiver deduplicates by a business id (`messageId`); a duplicate answers success (200), not an error.

#### 4.5 `max-attempts` counts the first call
- **Know:** "retry 3 times" = `max-attempts: 4`.

### Tier 2 — common

#### 4.6 Total time budget
- **Pitfall:** attempts × timeout + backoffs exceed an outer limit (HTTP caller timeout, Kafka `max.poll.interval.ms` — default 5 min → rebalance, duplicate processing).
- **Do:** compute the worst case (e.g. 4 × 3 s + 0.5 + 1 + 2 ≈ 15.5 s) and compare with the outer limit; with Kafka also × `max.poll.records`.

#### 4.7 Backoff
- **Do:** exponential backoff; add jitter (`randomized-wait-factor`) when many instances retry the same downstream (thundering herd).

#### 4.7a Jitter in Resilience4j
- **Do:** `enable-exponential-backoff` + `enable-randomized-wait` + `randomized-wait-factor: 0.5` → exponential random
  backoff; each pause varies ±50%.
- **Pitfall:** jitter raises the worst case: the maximum pause is `wait × multiplierⁿ × (1 + factor)` — recompute the
  timeout chain (here 3.5 s → 5.25 s of backoff per event).

#### 4.7b `@Retry` repeats the whole method, not one network call
- **Know:** the proxy catches the exception leaving the method and calls the **whole method** again — every step inside,
  including calls that already succeeded. `retry-exceptions` apply to whatever the method throws.
- **Pitfall:** three calls in a retried method, the third fails → all three are repeated; non-idempotent steps (create,
  charge, send) run twice.
- **Do:** one external action per retried method, or every action inside idempotent.
- **Assessment:** `track()` = token (cached after the first success) + one idempotent API call → safe. The token request
  is inside the same retry: no separate retry configuration for Keycloak needed, only its own timeouts.

#### 4.7c Choosing the retry tool in a Spring application: Resilience4j vs Spring core
- **Resilience4j** (`resilience4j-spring-boot4` + `spring-boot-starter-aspectj`): `@Retry`, `@CircuitBreaker`, bulkhead,
  rate limiter, time limiter; configured in YAML; ready Micrometer metrics (`resilience4j_retry_calls_total` by `kind`,
  breaker state, failure rate) on `/actuator/prometheus` without code. Added with a dependency.
- **Spring Framework 7 core** (`@EnableResilientMethods`): `@Retryable` (`includes`, `maxRetries`, `delay`,
  `multiplier`, `jitter`, `maxDelay`), `@ConcurrencyLimit` (bulkhead-like), programmatic `RetryTemplate` / `RetryPolicy`.
  No extra dependency. **No circuit breaker.** Ready retry metrics — as far as known none; add via `RetryListener` +
  Micrometer (verify in the docs of the version used). Replaces the separate Spring Retry project (maintenance mode).
- **Pitfall — counting:** Spring `maxRetries = 3` = retries **without** the first call; Resilience4j `max-attempts: 4`
  includes it (4.5). Off-by-one when switching.
- **Pitfall — jitter units:** Spring `jitter` is an absolute value (ms); Resilience4j `randomized-wait-factor` is a
  fraction (0.5 = ±50%).
- **Decide:** retry only → Spring core is enough. Retry + circuit breaker + metrics → Resilience4j (assessment:
  "Spring 7 has retry in core, but no circuit breaker — I need both, plus metrics").

#### 4.8 A green test for the wrong reason
- **Pitfall:** a retry test passed because the HTTP library retried, not the app (see 3.2).
- **Do:** always test both boundaries (success on the last attempt **and** give-up after N).

---

## 5. Circuit Breaker

**Main motive:** stop hammering a dead downstream and fail fast —
but "fail fast" must not mean "lose data".

### Tier 1 — every project

#### 5.1 Only transient errors open the breaker
- **Pitfall:** a stream of 400s (client bug) opens the breaker and blocks valid requests too.
- **Do:** `record-exceptions` = the same transient set as retry; everything else counts as success for the breaker.

#### 5.2 `CallNotPermittedException` is a separate type
- **Pitfall:** it is not a `RestClientException` — a `catch (RestClientException)` misses it and it escapes as a "bug".
- **Do:** handle it explicitly; cover it with a test.

#### 5.3 Open breaker with a message consumer = data loss risk
- **Pitfall:** marking every message as failed while the breaker is open — a downstream outage turns into thousands of wrong results.
- **Do (production):** backpressure — `pause()` the listener container on OPEN, `resume()` on HALF_OPEN/CLOSED (`KafkaListenerEndpointRegistry` + breaker event publisher); re-deliver the in-flight message (seek back, don't skip).

### Tier 2 — common

#### 5.4 Aspect order Retry ↔ Circuit Breaker
- **Know:** Resilience4j default: Retry **outside**, CircuitBreaker **inside** → every attempt is recorded by the breaker; once it opens, the retry stops (`CallNotPermittedException` is not retried).
- **Do:** be aware the window fills `max-attempts` times faster.

#### 5.5 Window and minimum calls
- **Know:** no evaluation before `minimum-number-of-calls`; a COUNT_BASED window of 10 with 4 attempts per call opens after ~3 failing calls.

#### 5.6 Shared state across tests
- **Pitfall:** the breaker is a singleton in the cached Spring context — failures from one test leak into the next; results depend on test order.
- **Do:** `circuitBreakerRegistry.circuitBreaker(name).reset()` in `@BeforeEach` of tests touching the breaker.

#### 5.7 One breaker per downstream
- **Do:** a separate instance per remote dependency; a dead service A must not block calls to B.

#### 5.8 Retry outside the breaker meets HALF_OPEN
- **Know:** with 3 permitted calls in HALF_OPEN and every retry attempt recorded, one failing event alone can use all
  trial slots and send the breaker back to OPEN; parallel threads take one slot each, their retries get
  `CallNotPermittedException`.
- **Why acceptable:** if the service is still down, reopening is right; if it is back, the first attempt succeeds and
  the breaker closes. Alternative: breaker outside retry (counts events, but retries keep hitting a dead service).

#### 5.9 Pausing the consumer needs the automatic transition
- **Pitfall:** "pause the listener on OPEN, resume on HALF_OPEN" deadlocks with Resilience4j defaults: the breaker leaves
  OPEN only on the next call after the wait — a paused listener makes no calls, no transition event, never resumed.
- **Do:** `automatic-transition-from-open-to-half-open-enabled: true` (a scheduler moves it to HALF_OPEN).
- **Flow:** OPEN → `pause()`; the in-flight event is not published as failed but thrown, the error handler seeks
  back (redelivered after resume); HALF_OPEN → `resume()`, trial calls; success → CLOSED, failure → OPEN → pause again.
- **Pitfall:** the breaker starts CLOSED and knows nothing about a dependency that is already down. Until it opens
  (minimum number of calls + failure rate) the first events still go through full retries and end as failed.
- **Decide:** to cover those too, a dependency outage (no token, 5xx, timeout) must return the event to Kafka instead of
  publishing a failed result. That contradicts a requirement like "unsuccessful after the 4th failed attempt" —
  a business decision, not a technical one.

#### 5.10 One breaker for "the call", token included
- **Decide:** recording authorization-server failures in the same breaker means a Keycloak outage opens it like a
  downstream outage — one mechanism (and one future pause) covers both. Short hiccups do not open it: retries absorb
  them, opening needs the minimum number of calls and the failure rate.

#### 5.11 Open breaker without losing events — production design
- **Know ("isn't it data loss?"):** publishing `unsuccessful` while the breaker is open loses no Kafka message and tells
  result consumers about the failure, but the event is never really processed (nothing stored) and not repeated.
- **Know (how the breaker tests recovery):** no separate health probe. In HALF_OPEN it lets the next N **real** calls
  through (`permitted-number-of-calls-in-half-open-state`); success → CLOSED, failure → OPEN for another wait period.
- **Do — connect breaker and listener** with the breaker's event publisher:
  ```java
  circuitBreaker.getEventPublisher().onStateTransition(event -> {
    switch (event.getStateTransition().getToState()) {
      case OPEN -> container.pause();                 // container from KafkaListenerEndpointRegistry by listener id
      case HALF_OPEN, CLOSED -> container.resume();
      default -> { }
    }
  });
  ```
- **Do — do not commit the event that hit the open breaker:** do not catch `CallNotPermittedException`, let it leave the
  listener. `DefaultErrorHandler` seeks back: this offset is not committed, the record (and the rest of the poll) is
  fetched again after resume; records before it are committed.
- **Pitfall:** with "no retries, straight to DLT" (`FixedBackOff(0, 0)`) that exception would go to the DLT. Classify it
  separately: never recover (no DLT), retry without limit; other exceptions keep the DLT path.
  (Spring Kafka supports a backoff per exception — check the exact API of the version in use.)
- **Know:** Kafka cannot hand out "exactly one message" after resume — a poll returns up to `max-poll-records`. With retry
  outside the breaker the first event's attempts use the HALF_OPEN slots anyway (5.8); the other records of the poll get
  `CallNotPermittedException` and go back the same way. Finer control: `pausePartition(...)`.
- **Know:** a paused consumer still polls (returns nothing) and heartbeats — it stays in the group, no rebalance.
- **Safe on crash:** the event is either processed and committed or still in Kafka; after a restart it is read again
  from the last committed offset.
- **Decide:** the same "back to Kafka" for dependency failures after exhausted retries (5xx, timeout, no token) closes the
  gap before the breaker opens (5.9) — but conflicts with "unsuccessful after the 4th attempt": a business decision.

---

## 6. Kafka Consumer

**Main motive:** Kafka is a log, not a queue — reading deletes nothing, offsets decide what is "done".
Most bugs come from forgetting this.

### Tier 1 — every project

#### 6.1 Delivery is at-least-once
- **Do:** design every handler as idempotent; duplicates happen (rebalance, retry, redeploy).

#### 6.2 Exception handling decides the offset
- **Know:** handler returns → offset committed; handler throws → the container's error handler decides (retry / skip / DLT).
- **Do:** business outcomes (e.g. UNSUCCESSFUL) are returned, not thrown; only unexpected errors propagate.

#### 6.3 Poison pill
- **Pitfall:** one malformed message fails deserialization forever and blocks the partition.
- **Do:** `ErrorHandlingDeserializer` + `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` (→ `<topic>.DLT`).
- **Verify:** IT: send broken JSON → it lands in the DLT, the next valid message is processed.

#### 6.4 Don't re-queue into Kafka
- **Pitfall:** publishing a failed message back to the same topic (JMS habit) — breaks per-key ordering, creates loops.
- **Do:** retry in place, then DLT; or pause the consumer (5.3).

#### 6.5 `max.poll.interval.ms` is a budget for the whole batch
- **Pitfall:** slow processing (retries!) exceeds it → the consumer is kicked out, rebalance, the batch is processed again.
- **Why:** the limit covers **all records of one poll** (`max.poll.records`, default 500), not one record. 500 × 15.5 s worst case ≈ 2 h vs a 5 min limit.
- **Do:** `max.poll.records × worst case per record` < `max.poll.interval.ms` with margin; set both explicitly (see 4.6).
- **Know:** `session.timeout.ms` / heartbeats (background thread) check "process alive"; `max.poll.interval.ms` checks "process makes progress".

#### 6.6 Failure in the middle of a batch — two different cases
- **Know:** a commit is a **position** per partition ("done up to here"), not a list of processed messages.
  Spring Kafka default `AckMode.BATCH` commits once, after **all** records of a poll are processed.
- **Case 1 — the process dies** (kill, OOM, crash) after 5 of 10 records: nothing committed → all 10 come again → 5 duplicates.
- **Case 2 — the handler throws** on record 3, the process lives: the error handler commits the position after records 1–2,
  record 3 goes to the recoverer (DLT), the rest are re-polled. If the DLT publish fails too (broker down), nothing is skipped:
  record 3 is re-polled later.
- **Do:** `AckMode.RECORD` commits per record (fewer duplicates, more commit traffic). Zero duplicates is impossible anyway
  (crash between processing and commit) → idempotent handler (6.1).
- **Know (it is a choice):** `spring.kafka.listener.ack-mode` — `BATCH` (default: one commit after the whole poll),
  `RECORD` (after every record), `TIME` / `COUNT` (periodically), `MANUAL` / `MANUAL_IMMEDIATE` (the code acknowledges).
  Trade-off: BATCH = fewer commit requests to the broker, up to `max-poll-records` repeats after a crash;
  RECORD = at most one repeat, a commit request per record.
- **Know:** idempotency covers only the side effects that are idempotent. Assessment: the database is (PK), the
  published result is not — after a crash between publish and commit a second result for the same `messageId` follows,
  in **any** ack mode (RECORD only narrows the window). Result consumers deduplicate.

#### 6.6a Producer already retries — don't add app retries on publish
- **Know:** a failed `send(...).join()` means the producer has **already** retried internally for up to `delivery.timeout.ms`
  (default 2 min). One more app-level retry a second later changes nothing.

#### 6.6b What goes to the DLT (design decision)
- **DLT = "could not process"**: deserialization errors and unexpected handler errors (bugs, `IllegalStateException`, failed publish).
- **Not DLT**: business outcomes (`UNSUCCESSFUL` after REST retries) — they are processed results in their own topic;
  mixing them in floods the DLT during an outage and makes DLT alerts useless.
- **No retries before DLT** for state-related errors (bug, bad data, wrong config, expired certificate): state doesn't fix itself in a second.
  Retries belong only to inherently unreliable calls (network), and those are already retried at their own layer.
- **Why DLT over "log and skip":** a log line needs no reaction and loses the payload; a DLT record is an explicit, monitored
  problem that keeps the original message (plus error headers) for analysis and replay.

#### 6.6c What a commit is, and where offsets can live
- **Know:** the broker stores the committed offset per **group + partition** in the internal topic `__consumer_offsets`.
- **Know:** a partition is read by exactly one consumer of the group, but one consumer can own **several** partitions —
  then **one poll returns records from several partitions**, and one commit stores a position **for each** of them.
- **Know:** the committed value is the **position of the next record to read** (last processed offset + 1). After 10
  processed records the position moves past all 10; a restart starts exactly there.
- **Option — offsets in your own database:** store the offset in the **same transaction** as the processing result;
  on partition assignment `seek` to the stored position (`ConsumerSeekAware`). Result and offset are atomic → no
  duplicates for Kafka → own database.
  - **Cost:** own code for assignment/seek; Kafka no longer knows the real position → consumer lag metrics, Kafka UI,
    lag alerts and offset reset tools show nothing useful — a serious operational loss (6.7 relies on lag).
  - Works only if the side effect is in **your** database; an external REST call is still outside the transaction.
- **Default is reasonable:** Kafka-stored offsets + at-least-once + idempotent processing (6.1). Duplicates are rare and
  handled; whoever consumes **our** output must handle them too (dedup by `messageId`).

#### 6.7 Consumer slower than retention = data loss
- **Know:** messages are deleted by `log.retention.*` (default 7 days / unlimited size, **per partition**), whether read or not. Lag older than retention is lost.
- **Do:** alert on consumer lag **per partition** (not only total), long before retention.

#### 6.8 Group offsets expire too
- **Pitfall:** a group without members for `offsets.retention.minutes` (default 7 days) loses its committed offsets → on restart `auto-offset-reset` applies: `earliest` = mass reprocessing, `latest` = skipped messages.
- **Do:** know both retentions; keep `offsets.retention` ≥ `log.retention`; treat long downtimes as an incident with a deliberate offset reset.

### Tier 2 — common

#### 6.9 `auto-offset-reset`
- **Know:** applies only when the group has **no** committed offset. `latest` (default) skips messages sent before the first start; `earliest` reads everything retained.

#### 6.10 Hot partition
- **Pitfall:** one key (a bot, a huge customer) produces most traffic → one partition overloaded, one consumer lags, others idle. More instances don't help.
- **Do:** monitor lag per partition; rate-limit at the source; a finer key only if per-entity ordering can be given up.

#### 6.11 Client defaults are invisible
- **Know:** consumer/producer defaults live in the `kafka-clients` code, not in any file (and not in the broker config).
- **Do:** read the `ConsumerConfig values:` / `ProducerConfig values:` block in the startup log; override via `spring.kafka.consumer.*` or `spring.kafka.consumer.properties.*`.

#### 6.12 Type headers couple services
- **Pitfall:** Spring's JSON serializer adds `__TypeId__` with the Java class name — the consumer must know the producer's class names.
- **Do:** `add.type.headers=false` (producer), `use.type.headers=false` + `value.default.type` (consumer); test with a raw JSON producer.

#### 6.13 Concurrency vs partitions
- **Know:** listener `concurrency` > number of partitions = idle threads. With `concurrency = 1` a bulkhead adds nothing.

#### 6.14 Listener in tests connects to a local broker
- **Pitfall:** every `@SpringBootTest` starts the real listener against `localhost:9092`, consumes leftover events from a developer's Kafka — flaky tests.
- **Do:** `spring.kafka.listener.auto-startup: false` in the test config; enable it explicitly only in ITs that need it (they use Testcontainers).
- **Why this default:** a forgotten `true` fails loudly and always; a forgotten `false` fails sometimes (flaky — the most expensive kind).

### DLT — design and implementation traps

Design decisions to make explicitly (see also 6.6b): **what** goes to the DLT, **retries** before it, **topic name**,
**partitioning**, **who watches it** and **how to replay**.

#### 6.16 Default behaviour loses or blocks messages
- **Know:** without `ErrorHandlingDeserializer` a malformed value fails **inside `poll()`** — before any error handler — forever (partition blocked).
- **Know:** without your own error handler Spring uses `DefaultErrorHandler` with 10 attempts, no pause, then **log and skip** — the message is gone, only a log line remains.
- **Do:** `ErrorHandlingDeserializer` wrapping the real one (`spring.deserializer.value.delegate.class`; the delegate's own settings still apply) + `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`.
- **Know:** Spring Boot plugs any `CommonErrorHandler` bean into the listener container automatically.

#### 6.17 Two kinds of values arrive in the DLT
- **Pitfall:** sending DLT records with the main JSON template — the raw bytes of a malformed message become a base64 string inside JSON; the original is destroyed.
- **Why:** a failed deserialization carries the **original `byte[]`**; a failed handler carries the **deserialized object**.
- **Do:** a dedicated DLT template with `DelegatingByTypeSerializer`: `byte[]` → `ByteArraySerializer`, `Object` → JSON (ordered map, `assignable = true`).
- **Verify:** IT: the DLT value of a malformed message equals the bytes sent.

#### 6.18 A second `KafkaTemplate` bean silently removes Boot's one
- **Pitfall:** declaring the DLT template as `@Bean` — Boot's auto-configured `KafkaTemplate` disappears; the publisher gets the wrong template or the app fails to start.
- **Why:** auto-configuration conditions check the bean **type** (`@ConditionalOnMissingBean(KafkaTemplate.class)`), not the name.
- **Do:** create the DLT template privately inside the error handler bean method.

#### 6.19 Serializer configured twice
- **Pitfall:** `new JacksonJsonSerializer<>().noTypeInfo()` + the same option in producer properties → `IllegalStateException` (setters and config properties must not be mixed).
- **Do:** let the serializer take its settings from the copied producer configuration only.

#### 6.20 DLT partitioning
- **Pitfall:** by default the record goes to the **same partition number** in the DLT — fails if the DLT has fewer partitions.
- **Do:** destination resolver returns partition `-1` → the producer chooses by key.

#### 6.21 Reading the DLT headers
- **Know:** Spring adds original topic / partition / offset / timestamp and exception headers.
  `kafka_dlt-exception-fqcn` is usually the **wrapper** `ListenerExecutionFailedException`; the real reason is in `kafka_dlt-exception-cause-fqcn` and the message headers.

#### 6.22 The DLT lives on the same Kafka
- **Know:** if the broker is down, the DLT publish fails too → the record is **not** skipped, it is re-polled later. No loss, but no progress either until Kafka is back.

#### 6.23 Operating the DLT
- **Do:** alert on DLT growth (a DLT nobody watches = a slower "log and skip"); define how to replay (back to the source topic after a fix).
- **Do:** treat the DLT as personal data storage — it holds full payloads (username, IP): restricted access, explicit retention (GDPR).
- **Do:** topic name from configuration / environment, like every other topic, not an implicit framework suffix.

#### 6.24 Testing the DLT
- **Pitfall:** several tests write to one shared DLT — "read the single record" depends on test order.
- **Do:** a unique key per test, read the DLT filtered by key (Awaitility polling).
- **Do:** to prove the partition is not blocked, send the broken message and a valid one with the **same key** (same partition), then assert the valid one is processed.

### Graceful shutdown

**Main difficulty:** the shutdown logic is **spread over three layers** that know nothing about each other —
the listener container, the Spring lifecycle and the runtime (Docker / Kubernetes). Each must wait longer than the one inside it.

#### 6.25 Timeout chain
- **Pitfall:** one layer gives up before the inner one is done — the runtime kills the process mid-record.
- **Know:** Docker sends SIGTERM, waits `stop_grace_period` (default **10s**), then SIGKILL. Kubernetes: `terminationGracePeriodSeconds` (default 30s).
- **Do:** worst case per record < listener `shutdownTimeout` < `spring.lifecycle.timeout-per-shutdown-phase` < runtime grace period
  (here: 37.25s < 40s < 45s < 50s; was 23.5 < 30 < 35 < 40 before the token request joined each attempt).
  Document the chain in one place (README table).
- **Know (analogy):** like an `ExecutorService` — `shutdown()` = stop polling, finish only the current task
  (`immediate-stop`), `awaitTermination(...)` = listener shutdown timeout, `shutdownNow()` = the runtime's SIGKILL.
  Unprocessed records of the poll are not lost: not committed → fetched again after the restart.
- **Pitfall:** the worst case per record is easy to compute from the REST call and its retries only. It includes
  **every blocking step**: token request, the call, retries and backoff — and also the Kafka publish
  (`send().join()`: up to `max.block.ms` + `delivery.timeout.ms`, minutes by default; the DLT publish too) and
  database calls. Any step without its own timeout breaks the chain (6.30).

#### 6.25a Per-call timeouts, time budgets and hard limits — three different things
- **Per-call timeout:** derived from the dependency's real latency (high percentile, p99/p99.9, plus a small margin) —
  not "long just in case". A long timeout holds a thread and a connection and makes one slow system hurt everything.
- **Hard limits** (`max.poll.interval.ms`, the graceful shutdown chain): exceeding them is not "a bit slower" but another
  failure mode (rebalance + duplicates, SIGKILL mid-record) → compute against the **worst case**, not the median.
  Failures are correlated (network degraded, overloaded node, long GC): in incidents everything is slow at once —
  exactly when the limit is tested.
- **Pitfall:** many dependencies → the sum of maxima explodes (10 systems × their timeouts).
- **Do:** a **time budget (deadline)** for the whole operation: each call gets `min(own timeout, remaining budget)`;
  budget used up → fail at once, the next calls do not start. Hard limits are then computed against the budget, not
  the sum. gRPC calls it **deadline propagation** (the deadline travels down the call chain). Background: tail latency
  ("The Tail at Scale", Dean & Barroso) — with many calls the median says little; budgets, hedged requests.
- **Assessment:** one business call (+ a usually cached token) and backoff → the honest sum of maxima is small;
  a budget would pay off with several downstream calls per record.

#### 6.26 Default stop processes the whole poll
- **Know:** by default the container finishes **all** records of the last poll before stopping (up to `max.poll.records` × worst case).
- **Do:** `spring.kafka.listener.immediate-stop: true` — finish only the current record; the rest is re-polled after the restart.

#### 6.27 Listener shutdown timeout has no Boot property
- **Know:** `ContainerProperties.shutdownTimeout` defaults to 10s — shorter than a record with retries.
- **Do:** set it via a `ContainerCustomizer` bean (Boot applies it automatically); keep the value in YAML next to the other timeouts.

#### 6.28 Proving it works
- **Do:** make processing slow (stub with delay + 503), send an event, `time docker compose stop`.
- **Verify:** the log shows the record finished and published **before** `partitions revoked` / `Consumer stopped`;
  exit code **143** (SIGTERM, clean) not **137** (SIGKILL); after restart no reprocessing (lag 0).
- **Pitfall:** a changed `stop_grace_period` applies only to a **recreated** container — check `docker inspect <c> --format '{{.Config.StopTimeout}}'`.

### Failure between steps (review findings)

#### 6.29 Crash between the REST call and the publish
- **Know:** track stored the event, the consumer dies before publishing → offset not committed → redelivered →
  the insert hits the primary key → server answers 200 (duplicate = success) → `SUCCESSFUL` is published.
  This is what idempotency by business id buys.
- **Know:** if track is down on redelivery, the result is `UNSUCCESSFUL` although the row exists; after a crash
  **after** publishing, a second result for the same `messageId` follows. Results are at-least-once: consumers dedupe.

#### 6.30 Producer timeouts belong to the chain
- **Pitfall:** `send(...).join()` inside the listener can wait `max.block.ms` (60 s, metadata) + `delivery.timeout.ms`
  (120 s) with a hanging broker — far beyond the listener shutdown timeout and the poll budget.
- **Know:** no data is lost (offset not committed, redelivery), but graceful shutdown ends in SIGKILL.
- **Do:** set both explicitly and add them to the worst-case formula (also for the DLT template).

#### 6.31 Comments must match the error handler
- **Pitfall:** "join() → exception → offset not committed → reprocessed" is false with a DLT recoverer: the failed
  publish goes to the DLT and the offset **is** committed. Only if the DLT publish fails too (Kafka down) is it redelivered.
- **Know:** a brief broker problem never reaches the handler — the producer already retried for `delivery.timeout.ms`.

#### 6.32 No validation on the consumer side
- **Pitfall:** valid JSON with invalid fields is sent to the downstream anyway (400 → UNSUCCESSFUL); a missing
  `customerId` makes the publish fail on `toString()` of the record key → DLT through an NPE; a missing `messageId`
  publishes a result with nulls. The outcome is accidental, not designed.
- **Do:** validate in the listener with the contract's constraints (`Validator`), invalid events to the DLT explicitly.

#### 6.33 Load grows 10x — what breaks first
- **Do:** first measure (consumer lag per partition, latencies, pool usage), then scale the bottleneck — walk the chain:
  1. **Consumer:** useful threads across the whole group ≤ partitions. More partitions + instances; each partition is
     processed by exactly one consumer of the group. Adding partitions changes key → partition (7.6).
  2. **Downstream service:** stateless → more instances behind a load balancer.
  3. **Database:** connections first (3.9), then write volume. One PostgreSQL handles thousands of simple inserts per
     second; time partitioning (retention) or sharding only when measured — a distributed database is a 100x step, not 10x.
  4. **Publishing:** waiting for the broker ack per event limits throughput → batching / async send with a later check.
- **At scale (many callers of one service):** server queues fill (Tomcat: `maxThreads`, `maxConnections`,
  `acceptCount`; beyond them connections are refused at TCP level — Tomcat itself does not answer 503, a load balancer
  or explicit load shedding does), thread starvation, retry storms, metastable failure. Protection: jitter, circuit
  breaker, load shedding / bulkhead on the server, rate limiting and retry budget on the client.

### Tier 3 — rare

#### 6.15 Kafka UI hides partitions of an empty group
- **Know:** without members the UI shows only partitions with committed offsets (from `__consumer_offsets`); a partition that never got a message is not listed. Not a bug.

### Explain it — interview questions

Questions to answer out loud, with the key points to hit.

1. **Kafka vs a JMS queue / webhooks: what happens to messages while a consumer is offline?**
   Kafka is an append-only log; messages stay regardless of readers. The consumer pulls from its own offset and catches up (lag), nothing is pushed or lost — within retention.
2. **What is an offset, who owns it, where is it stored?**
   Position in a partition's log. Owned by the consumer **group** per partition; committed to the internal topic `__consumer_offsets`; fetched by the consumer on assignment.
3. **Two different services read the same topic — do they interfere? Two instances of the same service?**
   Different groups = independent offsets, both get everything. Same group = partitions are split between instances, each message is processed by one of them.
4. **Why partitions?**
   Unit of parallelism (max useful consumers in a group = partitions), ordering scope (only within a partition), load spread across brokers. Reliability comes from replication, not partitions.
5. **Who chooses the partition? What changes with a key?**
   The producer. Key → `hash(key) % partitions`, same key → same partition → per-key order. No key → spread (sticky partitioner), no order per entity.
6. **One customer generates most of the traffic — what happens?**
   Hot partition: one consumer overloaded and lagging, others idle; scaling out doesn't help (6.10).
7. **How long are messages kept?**
   `log.retention.hours` (default 168 = 7 days) and/or `log.retention.bytes` (default unlimited, per partition) — whichever first. Deleted by whole segments, read or not.
8. **The consumer is slower than the producer — what happens, step by step?**
   Lag grows → still fine, data waits → lag older than retention → data deleted unread = loss. Detect via lag per partition; fix by more partitions/instances, faster processing, or backpressure upstream.
9. **`offsets.retention` vs `log.retention`. The service was down for two weeks — what happens on restart?**
   Messages older than 7 days are gone anyway (loss); the group's offsets expired → `earliest`: reprocesses everything retained (duplicates), `latest`: skips the backlog. Either way an incident.
10. **A poll returns 10 records; the service crashes after processing 5. What is processed again? And if instead the handler throws on record 3?**
    Crash: with `AckMode.BATCH` (default) nothing was committed → all 10 again (5 duplicates). Exception: position after 1–2 committed,
    record 3 → DLT, the rest re-polled; if the DLT is unreachable, record 3 comes again later (6.6). Hence at-least-once and idempotent handlers.
11. **What is `max.poll.interval.ms`, and how does it relate to retries and timeouts?**
    Max time between polls = budget for the whole batch. `max.poll.records × (attempts × timeout + backoffs)` must fit; otherwise rebalance + duplicates. Differs from `session.timeout.ms` (liveness via heartbeats).
12. **A message can never be processed (malformed JSON, permanent business error) — what do you do?**
    Don't block the partition and don't re-queue into the same topic: `ErrorHandlingDeserializer` + error handler with limited retries → DLT, with alerting; reprocess from the DLT after a fix.
13. **The downstream REST service is down for 30 seconds — what should the consumer do?**
    Retry only transient errors; circuit breaker to fail fast; better than marking everything failed: pause the listener container while the breaker is open, resume after, re-deliver the in-flight record.
14. **When does a rebalance happen and what does it cost?**
    A member joins/leaves/crashes or exceeds `max.poll.interval.ms`. Partitions move; uncommitted records are processed again; processing pauses during the rebalance.
15. **At-least-once vs exactly-once — how do you get "effectively once"?**
    At-least-once + idempotent consumer (dedup by `messageId`). Kafka transactions give exactly-once only for Kafka → Kafka; an external REST/DB call needs idempotency anyway.
16. **Why no type headers in the messages?**
    `__TypeId__` couples consumers to the producer's Java class names; plain JSON with a default target type keeps services independent (6.12).
17. **What is a DLT, what goes there and what doesn't? Why not just log and skip?**
    See 6.6b: unprocessable messages only, no retries for state errors, business outcomes stay in their topic; DLT keeps the payload and demands a reaction.
18. **What happens by default in Spring Kafka with a malformed message, and with an exception in the listener?**
    Malformed without `ErrorHandlingDeserializer`: fails in `poll()` forever. Listener exception: 10 attempts, then log and skip (6.16).
19. **Which design decisions does a DLT need?**
    What goes there, retries before it, topic name, partitioning (`-1`), raw bytes vs JSON values, monitoring/alerting, replay, access and retention of personal data (6.17–6.23).
20. **Where do you see why a record ended up in the DLT?**
    In its headers: original topic/partition/offset, and the exception — look at the **cause** header, the main one is usually the listener wrapper (6.21).
21. **The service gets SIGTERM while processing a record. What happens, step by step?**
    Container stops polling → finishes the current record (with `immediate-stop`) → commits → revokes partitions, leaves the group →
    Spring closes the rest (web server, producer flush) → process exits with 143. Without it: killed mid-record.
22. **Which timeouts must be aligned for a graceful shutdown, in which order, and why?**
    Worst case per record < listener shutdown timeout < Spring shutdown phase < Docker/K8s grace period; each layer must outwait the inner one (6.25).
23. **Why is graceful shutdown hard to get right in practice?**
    The logic is spread across code/config of three independent layers with different defaults (10s, 30s, 10s); nothing fails at build time — only a live test shows it.
24. **What is lost if the shutdown is not graceful?**
    No data (offset not committed → at-least-once redelivery), but duplicates and half-done side effects (REST call done, result not published) → idempotency.
25. **How do you prove graceful shutdown works?**
    Slow stub + stop during processing; log order, exit code 143 vs 137, lag 0 after restart (6.28).
26. **One consumer reads all partitions of a topic. Is the order of events guaranteed?**
    Only within each partition; records from different partitions arrive interleaved. Per-entity order needs a key (7.2).
27. **Who sets the key on the input topic and on the output topic? What if the input comes without a key?**
    Input: the upstream producer (a contract we must state); output: we. Unkeyed input → per-customer order already lost, we cannot restore it (7.5).
28. **What happens to key-based ordering when partitions are added to a topic?**
    `hash(key) % N` changes → a key may move to another partition; order across the change is lost. Choose partitions with headroom (7.6).
29. **How do you choose the listener concurrency, and why is there no bulkhead in this service?**
    Bounded by the partition count and by downstream capacity ÷ instances, from the environment. One listener, one sync call per record → concurrency already caps the downstream; a bulkhead pays off with several callers of one downstream, heavy non-call work, or several downstreams.
30. **Replication, ISR, under-replicated partitions** — _to be studied._
31. **The service crashes after the REST call succeeded but before the result is published. What happens?**
    Redelivery → duplicate insert → 200 → SUCCESSFUL; idempotency by `messageId` makes the retry safe (6.29).
32. **Which timeouts does a blocking `send().join()` add to the chain?** `max.block.ms` + `delivery.timeout.ms` (6.30).
33. **A publish fails after the producer's own retries — where does the event end up?**
    In the DLT, offset committed; if the DLT is unreachable too, redelivered (6.31).
34. **What happens to valid JSON with a missing customerId?** Today: accidental DLT via NPE; better: validate on input (6.32).
35. **One consumer owns three partitions. What does one poll return, and what exactly does a commit store?**
    Records from any of its partitions in one batch; the commit stores, per partition, the position of the next record
    to read — in `__consumer_offsets`, per group (6.6c).
36. **Could you store offsets yourself instead of in Kafka? Why would you, and why usually not?**
    In your own database, in the same transaction as the result, `seek` on assignment → no duplicates for Kafka → DB.
    But own code, and lag metrics / Kafka UI / offset tools lose the real position. Default + idempotency is usually the
    better trade (6.6c).
37. **`spring.kafka.consumer` vs `spring.kafka.listener` — what is the difference?**
    `consumer` = settings of the Kafka client itself (`KafkaConsumer` from `kafka-clients`): records per poll, protocol
    timeouts, deserializers — passed through to the client. `listener` = settings of Spring's **container** around it:
    threads (`concurrency`), when to commit (`ack-mode`), how to stop (`immediate-stop`, shutdown timeout). The container
    runs the `poll()` loop and calls your method; the client inside knows nothing about these settings.
38. **How do you choose a timeout for one call?**
    From the dependency's real latency: a high percentile plus a margin — not "long just in case" (6.25a).
39. **Your worst case per record sums the maximum timeouts. Isn't that too pessimistic?**
    For hard limits (max poll interval, shutdown chain) no: exceeding them is a different failure mode, and failures
    are correlated — in an incident everything is slow at once. For per-call timeouts yes: those follow real latency.
40. **The operation calls ten systems. Do you add up ten maximum timeouts?**
    No: a time budget / deadline for the whole operation, each call gets `min(own timeout, remaining budget)`,
    hard limits are computed against the budget (deadline propagation, as in gRPC).

---

## 7. Kafka Producer

### Tier 1 — every project

#### 7.1 Fire-and-forget loses messages
- **Pitfall:** `send()` is async; the offset of the input message is committed even if the publish fails.
- **Do:** wait for the result (`send(...).join()`) inside the consumer flow → a failure propagates, the input is redelivered (at-least-once).

#### 7.2 Message key = ordering
- **Do:** key by the business entity (`customerId`) → all events of one customer go to one partition, in order.
- **Pitfall:** a `null` key → spread over partitions, no ordering guarantee.
- **Know:** the **producer client** chooses the partition (`hash(key) % partitions`), not the broker.
- **Know:** order is guaranteed only **within** a partition — even a single consumer reading several partitions
  gets their records **interleaved**. A key is needed whenever per-entity order matters, not only with several consumers.

#### 7.5 Input key is the upstream producer's contract
- **Pitfall:** assuming input events are keyed because our tests send them with a key — the real upstream producer decides.
  If it sends without a key, per-customer order is lost **before** our service and cannot be restored.
- **Do:** state it explicitly in the topic contract ("events must be keyed by `customerId`"); our own output topic we key ourselves.

#### 7.6 Adding partitions breaks the key → partition mapping
- **Pitfall:** increasing the partition count changes `hash(key) % N` — new events of a customer may land in another
  partition than the old ones; ordering across the change is lost.
- **Do:** for key-ordered topics choose the partition count with headroom up front; treat a change as a migration.

### Tier 2 — common

#### 7.7 Topics are created explicitly, not by the first message
- **Know:** with `auto.create.topics.enable=true` (broker default) a topic appears on the first produce/fetch — with
  the broker defaults: partition count, replication factor, retention. Locally: the DLT appears only with its first
  record.
- **Pitfall:** production topics created by accident — wrong partition count (fixes parallelism and key → partition,
  hard to change later, 7.6), replication factor 1 (no availability), default retention (7 days, also for personal
  data in a DLT), no access rules. A typo in a topic name silently creates a new topic.
- **Do (production):** `auto.create.topics.enable=false`; create every topic up front (infrastructure as code: Terraform,
  Strimzi `KafkaTopic`, a script, or Spring `NewTopic` beans for dev only) with explicit partitions, replication factor
  (usually 3, `min.insync.replicas` 2), retention and ACLs. The DLT too.

#### 7.3 Producer is lazy
- **Know:** `KafkaTemplate` creates the producer at the first `send()`; no broker connection until then (relevant for tests).

#### 7.4 Console producer sends without a key
- **Pitfall:** manual test events from `kafka-console-producer` have a `null` key → spread over partitions, not like the real producer.
- **Do:** `--property parse.key=true --property "key.separator=|"` and `key|json` per line (not `:` — JSON is full of colons).

---

## 8. Configuration

### Tier 1 — every project

#### 8.1 Environment-specific values without defaults
- **Pitfall:** `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` — a forgotten env var in production silently points to the wrong place.
- **Do:** env-specific values only from env vars, **no default** → the app fails fast at startup.

#### 8.2 Test config overrides, not replaces
- **Do:** test values in `src/test/resources/config/application.yaml` — loaded **on top of** the main config; only differences go there.

#### 8.3 Duplicate YAML keys
- **Pitfall:** a second `spring:` block in the same file — an error or the first block silently overwritten.
- **Do:** add new keys inside the existing block.

### Tier 2 — common

#### 8.4 Names from config, not constants
- **Do:** topic names etc. via `@Value("${app.kafka.topics...}")` in code **and** tests — a test with a hardcoded name tests a different setup.

---

## 9. Logging

### Tier 1 — every project

#### 9.1 Log volume per event
- **Pitfall:** an INFO line per incoming event (every login!) — millions of lines a day, storage cost, noise.
- **Do:** per-event logs at DEBUG; counts and rates as **metrics**, not logs.

#### 9.2 No personal data
- **Do:** only technical ids (see 2.4); no username, IP, tokens.

#### 9.3 Level = what the reader must do
- **Do:** ERROR — someone must act; WARN — a handled failure / degraded outcome (e.g. tracking UNSUCCESSFUL); INFO — lifecycle; DEBUG — per-event detail.

### Tier 2 — common

#### 9.4 Lazy message construction
- **Do:** Log4j API with supplier lambdas: `log.debug(() -> "...%s".formatted(id))` — nothing is formatted when the level is off. `LogManager.getLogger()` without arguments.

#### 9.5 Logging API vs implementation
- **Know:** Spring Boot logs through Logback; the Log4j2 **API** works on top via the `log4j-to-slf4j` bridge that Boot
  brings. Used here for lazy lambdas (`log.debug(() -> ...)`); SLF4J 2's fluent API offers the same. A choice, not a defect.

---

## 10. Testing

### Tier 1 — every project

#### 10.1 Tests must not depend on the developer machine
- **Pitfall:** a test reads from a locally running Kafka / DB (see 6.14).
- **Do:** external systems only via Testcontainers / WireMock; nothing connects to `localhost` defaults.

#### 10.2 Test the boundaries, not just the happy path
- **Do:** for every rule, the case just inside and just outside (succeeds on attempt N, gives up after N; timeout just below and above).

#### 10.3 Async assertions: poll, don't sleep
- **Do:** `verify(mock, timeout(10_000))`, `KafkaTestUtils.getSingleRecord`, Awaitility — return as soon as the condition holds; the timeout is only the upper bound. A latch needs a timeout too and adds code.
- **Do:** in E2E, wait for the final output first, then check side effects (e.g. WireMock requests) — otherwise flaky.

#### 10.4 Prove infrastructure with an experiment
- **Do:** when a test relies on framework magic (AOP, auto-config), break it once on purpose (remove the dependency) and see the test fail.

### Tier 2 — common

#### 10.5 Explicit construction in unit tests
- **Do:** `mock(...)` + `new Service(mock)` instead of `@InjectMocks`: a new constructor parameter breaks compilation instead of leaving a `null` field.

#### 10.6 Layer ITs + one E2E
- **Do:** an IT per external boundary (Kafka in, Kafka out, HTTP out) for precise failure location; one E2E for the whole flow.
- **Know:** `@MockitoBean` creates a separate Spring context (no cache reuse) — a few seconds extra.

#### 10.7 Shared fixtures
- **Do:** one `TestData` class with constants + the contract JSON built from the same constants.

#### 10.8 Strict JSON comparison catches real bugs
- **Know:** strict JSONAssert failed on an unexpected extra field — that was a real mismatch, not noise.

#### 10.8a Contract test for free
- **Do:** in the server's E2E test build the client from the **shared HTTP interface**
  (`HttpServiceProxyFactory` over `RestClient.create(...)`) — proves client and server agree on path, method, JSON, status codes.
- **Know:** `RestClient.create()` picks the HTTP engine by classpath (Apache → Jetty → Reactor Netty → JDK HttpClient);
  production concerns (timeouts, engine tuning, resilience) live around the interface, never in it.
- **Know:** on 4xx the client throws `HttpClientErrorException` with the response headers and body — assert the error format there.

#### 10.8b Reading results with JdbcTemplate
- **Know:** `JdbcTemplate` uses the same DataSource pool as Hibernate but not its session or transactions —
  it sees only what is really committed. Good for asserting from the outside.

#### 10.8c Debugging aids, off by default
- **Do:** keep verbose helpers commented: `andDo(print())` (MockMvc request/response), PostgreSQL `log_statement` +
  `Slf4jLogConsumer`, `liquibase.executor: debug`. Useful while writing a test, noise when tests run regularly —
  a failure must be visible in the test result, not hidden in logs.
- **Do:** `spring.jackson.serialization.indent-output: true` in the **test** config only — readable JSON in `print()`.

#### 10.9a Per-class containers + cached Spring contexts = orphaned listeners
- **Pitfall:** a `@Container static` Kafka per IT class is stopped after the class, but Spring keeps the class's context cached —
  its listener keeps reconnecting to a dead broker (floods of "Connection to node -1 could not be established").
- **Do:** one **singleton container** for all ITs (started once in a static block, removed by Ryuk at JVM exit) — also faster;
  or `@DirtiesContext` on IT classes (slower).

#### 10.10 Hidden order dependency through the cached context
- **Pitfall:** a test class that never stubs the token endpoint passed only because another class left a token in the
  shared, cached Spring context. Surefire's default `runOrder` is the **file system** order: on another machine the
  order changes and the build fails.
- **Do:** every test class sets up the shared state it needs (stub + reset) in `@BeforeEach`; never rely on leftovers.

#### 10.11 Identical payloads on a shared topic = false positives
- **Pitfall:** two tests wait for the same JSON (same `messageId`) on one topic, read from the beginning: the second
  finds the first one's record immediately and then verifies side effects of its own event that is still in progress.
  It passed only because the consumer's group join happened to take a few seconds.
- **Do:** a unique id per test (`messageId`), test data builders with that parameter.

#### 10.12 Property overrides create a new context
- **Know:** `@SpringBootTest(properties = ...)` is a different context key — a separate, slower context.
- **Do:** put such a test in its own small class (e.g. a lowered timeout), not into a class shared by many tests.
- **Know:** a lowered timeout in a test proves the timeout is **wired**, not its production value; make the test fail
  if it is not applied (the delayed answer would otherwise succeed).

#### 10.13 Read what the tool really counts
- **Example:** "expected 4 token requests, got 8" exposed Apache retries inside Spring Security's token client
  (15.15a). Exact counts in WireMock catch hidden layers that "it failed" assertions never see.

### Tier 3 — rare

#### 10.9 Simulating network faults
- **Do:** WireMock scenarios for sequences (500 → 503 → reset → 200), `withFault(...)`, `withFixedDelay(...)`.

---

## 11. Dependencies & CVEs

### Tier 1 — every project

#### 11.1 Triage, don't panic
- **Do:** for each CVE check: is the vulnerable code path used? (e.g. a Kafka **broker** SASL CVE does not affect a client app); scope (test-only → lower priority).
- **Do:** SCA runs externally (Dependabot, OWASP Dependency-Check) in CI, not ad hoc in the IDE.

#### 11.2 Pin images
- **Pitfall:** `:latest` images (e.g. Kafka UI) — a different version on every pull.
- **Do:** pin versions in `.env` / compose.

---

## 12. Docker & Local Environment

### Tier 2 — common

#### 12.1 Kafka in Docker: two listeners
- **Pitfall:** clients on the host and in containers need different addresses; one advertised listener breaks one side.
- **Do:** an internal listener (`kafka:29092`) and an external one (`localhost:9092`), both in `ADVERTISED_LISTENERS`; host port from `.env` used in both the mapping and the advertised address.

#### 12.2 Env-specific values in `.env`
- **Do:** images, ports, topic names in `.env`; `docker-compose.yml` stays the same for everyone.

#### 12.3 Fixed `container_name` across compose projects
- **Pitfall:** the same compose file started from two folders (two compose projects) collides: "container name is
  already in use".
- **Do:** `docker compose down` in the other folder, or `docker rm -f <names>`; or drop `container_name` and let
  compose prefix names with the project.

#### 12.4 Healthcheck timing must cover a cold start
- **Pitfall:** `start_period` 30 s + 3 retries fit a warm Keycloak (~35 s) but not a fresh container (augmentation
  ~45 s + start ~35 s): compose gives up with "dependency failed to start" — although the service becomes healthy later.
- **Do:** override `start_period` for slow starters; test with a fresh clone and fresh containers.

#### 12.5 Cold-start test from Git
- **Do:** `git clone` into a new folder, build, `compose up` — only committed files count. Check that `.env` is in Git
  (or `.env.example` + README step), and that uncommitted fixes (healthcheck timings) are not masking problems.

#### 12.6 Compose details
- **Know:** `depends_on` short form (list) means `service_started`; it cannot be mixed with the long form — write all
  entries long once one needs `condition: service_healthy`.
- **Know:** `docker compose pause <svc>` freezes a container: TCP connects, nothing answers — the perfect "hanging
  service" for read-timeout and graceful-shutdown demos.

---

## 13. Persistence: JPA, Liquibase, Connection Pool

**Main motive:** the database is the last line of defense for data quality and the place where idempotency is cheapest —
but the ORM and the migration tool hide what really happens. Prove behaviour with tests against the real database.

### Tier 1 — every project

#### 13.1 Schema ownership
- **Do:** Liquibase (or Flyway) owns the schema; `spring.jpa.hibernate.ddl-auto: validate` — Hibernate only checks
  that entities match it and fails at startup on a mismatch.
- **Know:** an applied changeset is never edited (checksums in `databasechangelog`); every change is a new file.

#### 13.2 Migration features silently ignored
- **Pitfall:** open-source Liquibase **silently ignores** the column attribute `checkConstraint`;
  `addCheckConstraint` is a paid (Secure/Pro) change type. The changelog looks right, the table has no constraint.
- **Do:** plain `sql` step (or a formatted-SQL changelog) for CHECK; CHECK is standard SQL-92, so no `dbms`
  restriction — on an unsupported database the migration must fail, not be skipped.
- **Verify:** test the **behaviour** (insert an invalid value → must fail), not the changelog text.

#### 13.3 `save()` with an assigned id merges
- **Pitfall:** with an id set in code (e.g. `messageId` from the event), Spring Data treats the entity as existing →
  `merge` → SELECT + **UPDATE**: a duplicate silently overwrites the stored row.
- **Know:** `isNew` is decided by `Persistable#isNew()`, else by a wrapper-typed `@Version` (null = new), else by a null id.
- **Do:** implement `Persistable` (transient flag, false after `@PostLoad`/`@PostPersist`) → plain INSERT, a duplicate
  fails on the key. Alternatives `@Version` or a surrogate key change the schema because of the framework — avoid.
- **Verify:** IT: save the same id twice with different data → exception **and** the stored row unchanged.

#### 13.4 Idempotent insert via the key
- **Do:** catch `DataIntegrityViolationException` **outside** any transaction (a failed constraint marks it rollback-only);
  treat it as a duplicate only if the violated constraint is the primary key (Hibernate's
  `ConstraintViolationException#getConstraintName()` — name the key in the migration).
- **Know:** Hibernate logs every duplicate as an SQL ERROR before you catch it — don't silence that logger globally.
- **Alternative:** native `INSERT ... ON CONFLICT DO NOTHING` — atomic, no exception, but database-specific SQL in the repository.

#### 13.5 Write-behind and flush
- **Know:** `persist` queues the INSERT; it is flushed at commit or before a query that needs it (`FlushMode.AUTO`),
  which enables JDBC batching.
- **Know:** a repository call without an outer transaction commits at its end — errors surface from that call;
  `saveAndFlush` matters only inside an outer transaction.

#### 13.6 Connection pool and statement timeouts
- **Pitfall:** defaults — HikariCP waits **30s** for a connection, PostgreSQL has **no** statement timeout —
  far beyond the caller's timeout: threads stay busy for answers nobody waits for.
- **Do:** per-request budget below the caller's timeout (here 1s connection + 1.5s statement < 3s read timeout);
  `statement_timeout` server-side (`hikari.data-source-properties.options: -c statement_timeout=1500`) — the Hibernate
  query timeout does not cover flush INSERTs; Hikari values are milliseconds (`1s` does not bind).
- **Verify:** `show statement_timeout` for the setting, `select pg_sleep(2)` → `QueryTimeoutException` for the behaviour.

### Tier 2 — common

#### 13.7 Entity basics
- **Do:** `timestamp with time zone` for `Instant` (Hibernate 6 maps it so; `validate` fails on plain `timestamp`).
- **Do:** `spring.jpa.open-in-view: false` (Spring Boot enables it: session open during response rendering, hidden lazy queries).
- **Know:** an entity cannot be a record (no-arg constructor, mutable fields for proxies); a `protected` constructor
  signals "for JPA only"; field access needs no setters.
- **Know:** SpotBugs `URF_UNREAD_FIELD` on entity fields (read by Hibernate via reflection) — add getters rather than
  suppressing every field of every entity.
- **Do:** repository on the marker `Repository<T, ID>` with only the needed methods (signatures copied from
  `CrudRepository` / `JpaRepository`).

#### 13.8 Testing persistence
- **Pitfall:** `@DataJpaTest` wraps every test in a rolled-back transaction — repository calls join it, INSERTs are
  deferred, a duplicate never fails in the test.
- **Do:** `@Transactional(propagation = NOT_SUPPORTED)` + clean the table in `@BeforeEach`;
  `@AutoConfigureTestDatabase(replace = NONE)` to keep the Testcontainers database.
- **Know:** `@ServiceConnection` → `JdbcConnectionDetails` bean → DataSource → Liquibase and Hibernate: no URL in test config.
- **Do (debugging):** PostgreSQL `-c log_statement=all` + `Slf4jLogConsumer` to see what the database receives;
  `logging.level.liquibase.executor: debug` to see what Liquibase sends.

#### 13.9 Concurrent duplicates
- **Know:** two inserts with the same key at once: the second waits for the first transaction, then fails with 23505
  (unique violation) — caught, answered 200. Correct; costs a dead tuple and error lines in PostgreSQL and Hibernate logs.
- **Alternative:** `INSERT ... ON CONFLICT DO NOTHING` — no error, returns 0/1, but database-specific SQL in the repository.
- **Pitfall:** wrap the call in an outer transaction and the INSERT moves to that commit — the catch no longer works (500).

#### 13.10 Pool settings also hit the migrations
- **Pitfall:** `statement_timeout` set on the Hikari pool applies to Liquibase too (same DataSource): a future long
  migration (`CREATE INDEX` on a big table) fails the startup.
- **Do:** own connection for Liquibase (`spring.liquibase.url/user/password`): it does not use the pool's options.
- **Pitfall (tests):** Boot reads `spring.liquibase.url` while evaluating auto-configuration conditions — an unresolvable
  `${DB_URL}` fails there, unlike the datasource placeholders. Fix: any literal URL in the test config; the real one
  comes from `@ServiceConnection` (`LiquibaseConnectionDetails` take precedence over properties).
- **Production:** migrations as a separate deploy step under a DDL user; the application user has DML only, the
  statement timeout is set per role in the database (`ALTER ROLE ... SET statement_timeout`). Single long change:
  `SET LOCAL statement_timeout = 0` inside its transaction (not for `CREATE INDEX CONCURRENTLY`).

#### 13.11 Random UUID keys and future reads
- **Know:** a random UUIDv4 primary key spreads inserts over the whole B-tree (page splits, cache misses, WAL volume);
  the key is often chosen upstream (time-ordered UUIDv7 is the producer's decision).
- **Know:** indexes, retention and partitioning follow the read queries (e.g. `(customer_id, login_timestamp DESC)`)
  and the retention rule (time partitions → drop instead of a huge DELETE that hits the statement timeout).

#### 13.12 Enum storage
- **Know:** `@Enumerated(STRING)` stores `name()`; changing the Java names changes the stored values and breaks
  CHECK constraints. Keep the JSON form separate (2.17).

#### 13.13 The schema follows the reads, not only the writes
- **Pitfall:** a table designed for inserting only (primary key + one index) — the first real queries become full scans
  on a large table.
- **Do:** ask early how the data will be read and kept, then design for it:
  - **Indexes** for the real queries, column order = filter first, then sort: `(customer_id, login_timestamp DESC)` for
    "last logins of a customer". Every index costs write speed and space — only for queries that exist.
  - **Retention:** partition the table by time (e.g. monthly); expired data = drop a whole partition, instead of a huge
    `DELETE` (long locks, bloat, hits the statement timeout).
  - **Verify:** `EXPLAIN ANALYZE` on the real queries with realistic data volume.
- **Assessment:** the reads were not specified → only the PK and an index on `customer_id`; listed in Known limitations.

### Explain it — interview questions

1. **Why can `save()` overwrite a row instead of inserting, and how do you prevent it without changing the schema?** (13.3)
2. **How do you make storing an event idempotent, and why must the duplicate not be caught inside a transaction?** (13.4)
3. **Your migration declares a CHECK constraint, but invalid data gets in. What happened and how would you have noticed?** (13.2)
4. **Which timeouts does a database call have, and how do they relate to the caller's timeout?** (13.6)
5. **Where does input validation happen, and which exception tells you which line of defense stopped the data?** (2.14)
6. **Why do your repository tests disable the test transaction?** (13.8)
7. **Two requests with the same messageId arrive at the same moment. What happens in the database and in your code?** (13.9)
8. **Your pool has a statement timeout. What does it do to your migrations, and how would you run migrations in production?** (13.10)
9. **Why would renaming an enum constant break inserts?** (13.12, 2.17)

---

## 14. Observability & Container Health

**Main motive:** each tool adds one layer — know what each gives and what it does not, then add them one by one
and look at the result after every step.

### Tier 1 — every project

#### 14.1 The layers
- **Micrometer** (inside the app) **counts**: current values in memory, reset on every restart, no history.
- **Actuator `/metrics`** shows them (JSON, for humans, "now" only); **`micrometer-registry-prometheus`** renders the same
  values as text at `/actuator/prometheus`.
- **Prometheus** **collects**: pulls (scrapes) the page every N seconds, stores time series, PromQL, adds `job`/`instance` labels.
- **Grafana** stores nothing: queries Prometheus/Loki and draws dashboards.
- **Know:** "Micrometer counts, Prometheus collects" — not the other way round.

#### 14.2 Reading metrics
- **Know:** a timer becomes `_count`, `_sum` (summary) and `_max` (gauge); averages = sum / count over a window.
- **Do:** counters are interesting as a **rate**: `rate(x_total[1m])`; `rate` handles counter resets after restarts.
- **Know:** free metrics without code: `spring.kafka.listener` (per record, tagged success/failure + exception),
  `kafka.consumer.*` (lag, time between polls, rebalances), `resilience4j.*`, `logback.events` (log lines per level).
- **Pitfall:** labels with unbounded values (customerId) explode cardinality — never use personal or per-entity ids as tags.
- **Pitfall:** HTTP calls of hand-built clients are not measured (no `ObservationRegistry`) — e.g. OAuth2 token requests
  (15.15a). "No errors in the metrics" can mean "not measured".
- **Know:** server and client metrics see different things — requests that never reached a stopped server exist only
  in the **client's** metrics (`http_client_requests_seconds`, `status="CLIENT_ERROR"` = no HTTP response at all, the
  reason in `exception`), and calls rejected by an open breaker appear in neither (only `not_permitted_calls`).

#### 14.3 Logs without coupling the app
- **Do:** the app writes to stdout; a collector (**Grafana Alloy**, successor of Promtail — EOL March 2026) reads container
  logs via Docker and ships them to Loki. No dependency, config or test impact in the app; Loki down ≠ app affected.
- **Alternative:** an appender in the app (e.g. loki4j) — extra dependency, config, test switches, app coupled to log infrastructure.

#### 14.4 Container healthcheck
- **Know:** without a healthcheck Docker knows only "process alive" (`Up`), not "application ready".
- **Know:** a healthcheck is always a **command inside the container** — Docker has no built-in HTTP check; the tool must be in the image.
- **Pitfall:** `eclipse-temurin` has neither curl nor wget → `curl: not found`, container `unhealthy`.
- **Do:** a shared script (bash `/dev/tcp`, no HTTP client needed) mounted into containers + an `x-` YAML anchor for timing;
  check the readiness probe, not the full health (no external dependencies).
- **Know:** plain Docker never restarts an `unhealthy` container; the status is for `docker compose ps` and `depends_on: service_healthy`.
- **Verify:** `docker inspect <c> --format '{{json .State.Health}}'` — last runs with exit code and output.

#### 14.5 Liveness vs readiness
- **Pitfall:** liveness depending on external systems (Kafka, DB) — an outage restarts every instance, fixes nothing.
- **Do:** liveness = process alive only; external dependencies at most in readiness.

#### 14.6 Metrics appear only after first use
- **Know:** timers like `http.client.requests` or breaker gauges show up in Prometheus only after the first call;
  an empty query right after start is not a broken scrape. Check names in Grafana's metrics browser.

#### 14.7 Health details are public by default here
- **Know:** `show-details: always` on an unauthenticated health endpoint reveals components (database, Kafka).
- **Do (production):** `when-authorized` or a separate management port.
- **Pitfall:** actuator on the application port — `/actuator/prometheus` (and health details) are reachable wherever the
  API is reachable; they reveal which systems stand behind the service and which of them are down.
- **Do:** `management.server.port` (e.g. 9090) reachable only inside the cluster — Prometheus and probes use it, it is
  never published. Without auth: the network protects it. Main reason: **security** (exposure), not load.
- **Know — isolation:** a separate management port runs on its **own embedded server with its own thread pool**. On the
  shared port, business requests that exhaust Tomcat's threads make health checks hang too → the orchestrator thinks
  the pod is dead and restarts an overloaded but healthy pod; metrics vanish exactly when needed. A scrape itself is
  light (every ~15 s) — the load argument is about isolation, not the scrape cost.
- **Know — who reads what:** metrics are **pulled by Prometheus** (people look at them in Grafana); health is read by
  Docker / Kubernetes probes. No human user → "authorized" means network isolation or a token for Prometheus.
- **Know:** a service whose only web endpoints are actuator (assessment: login-processing-service) gains nothing from a
  second port — just do not publish it. A service with a business API (track-login-service) should split.
- **Know ("authorized" without people):** if authentication is needed, Prometheus can send a bearer token or basic auth
  in its scrape config — e.g. its own client in the authorization server with a scope like `actuator:read`.
- **Assessment:** public health details and metrics on the API port — on purpose for the local demo, listed in Known
  limitations. See also 15.9 (deny by default, metrics/env/heapdump never public).

#### 14.8 Start order: compose waits, the application does not
- **Know:** compose `depends_on` with `condition: service_healthy` keeps the container from starting until the
  dependency is healthy — no JVM, no listener; events wait in Kafka. A local convenience only.
- **Pitfall:** Kubernetes has no start order. The service starts at once: web server up, Kafka listener consuming,
  dependencies maybe still down. It must survive that by itself.
- **Assessment:** login-processing-service starts without Keycloak (`token-uri`, no discovery) and consumes right away
  → token fails → retry → `unsuccessful` → breaker opens, same as an outage. track-login-service starts without
  Keycloak (keys fetched lazily) but **not** without PostgreSQL (Liquibase migrates at startup → startup fails → restart).
- **Decide:** what to do at startup with a dependency down — process and fail (today), or keep the listener stopped
  (`auto-startup: false`) and start it once the dependency answers, or pause it via the breaker (5.3, 5.9).

#### 14.9 Readiness does not stop a Kafka consumer
- **Pitfall:** "not ready" in Kubernetes only removes the pod from the Service — it stops **incoming HTTP** traffic.
  A Kafka consumer pulls messages itself (`poll()`), so it keeps consuming while the pod is not ready.
- **Know:** Spring Boot's readiness group does not include external systems by default anyway.
- **Do:** stop consumption explicitly when the service cannot work: pause the listener container
  (`KafkaListenerEndpointRegistry`) or do not start it until the dependencies answer.

### Explain it — interview questions

1. **What does each of Micrometer, Actuator, Prometheus, Grafana add, and what does each not do?** (14.1)
2. **Why is a counter shown with `rate()`, and what happens to it on a restart?** (14.2)
3. **How do the logs get to Loki, and why doesn't the application know about Loki?** (14.3)
4. **Why does a container show `Up` while the application is not ready, and how does a Docker healthcheck work?** (14.4)
5. **Why must liveness not depend on the database or Kafka?** (14.5)
6. **Why put actuator on a separate management port? Name two reasons.**
   Security: the port stays inside the cluster, metrics and health details are never public. Isolation: own embedded
   server and thread pool — health and metrics answer even when business requests exhaust the main pool; otherwise
   an overloaded pod looks dead and gets restarted. Property: `management.server.port` (14.7).
7. **Health details and metrics need protection, but there are no users. Who is "authorized"?**
   Machines: probes and Prometheus. Usually network isolation (internal port); if auth is required, Prometheus sends a
   bearer token or basic auth in its scrape config (14.7).
8. **Does every service need a separate management port?**
   Only where business API and actuator share a port. A service with actuator as its only web part just keeps that port
   unpublished (14.7).

---

## 15. Security: OAuth2 Service-to-Service

**Main motive:** security is set up once per project and then rarely touched, so the details fade.
Keep the **roles**, the **checks** and the **options with their tradeoffs** written down; most bugs come from
a forgotten check (`aud`) or from a default that silently changes behaviour (the starter locks everything).

### Basics

#### 15.1 The three roles
| Role                 | What it is                                   | What it does                                                   |
|----------------------|----------------------------------------------|----------------------------------------------------------------|
| Authorization server | Keycloak, Okta, Entra ID, Spring Auth Server | Authenticates clients, issues signed tokens                    |
| Client               | A service that calls a protected API         | Gets a token, sends `Authorization: Bearer <token>`            |
| Resource server      | The protected API                            | Validates the token, checks scope and audience                 |
- **Know:** one service can be both: resource server for its callers, client for its downstreams.
- **Know:** the authorization server is the only one that knows client secrets; the resource server never sees them.

#### 15.2 Grant types: which flow for which caller
| Flow                         | Caller                         | Use                                                           |
|------------------------------|--------------------------------|---------------------------------------------------------------|
| `client_credentials`         | A service, no user             | Service-to-service (backend jobs, Kafka consumers)            |
| `authorization_code` + PKCE  | A user via browser / app       | Login of people; the token carries the user                   |
| `password`                   | User credentials sent directly | Deprecated (OAuth 2.1 removes it) — only for admin tooling    |
| Token exchange (RFC 8693)    | Service acting for a user      | Pass the user's identity downstream                           |
- **Pitfall:** enabling more flows than needed on a client (Keycloak enables standard flow by default).
- **Do:** one client per service, only the flow it uses, only the scopes it needs (least privilege).

#### 15.3 Token anatomy (JWT)
- **Know:** `header.payload.signature`, each part base64url; the payload is **readable by anyone** — signed, not encrypted.
- **Claims to know:** `iss` (who issued), `sub` (who it is about), `aud` (for whom it is meant), `exp` / `iat`,
  `scope` (permissions), `azp` (client that requested it, Keycloak).
- **Pitfall:** putting personal or secret data into custom claims — every proxy and log that sees the token sees it.

### Tier 1 — resource server

#### 15.4 Local validation vs introspection
| | Local JWT validation | Introspection (RFC 7662) |
|---|---|---|
| How | Verify signature with public keys (JWKS), fetched once and cached | Ask the authorization server per request: "is this token active?" |
| Latency | None per request | One network call per request (cacheable) |
| Availability | Authorization server down → still works (until keys rotate) | Authorization server down → every request fails |
| Revocation | Not seen until `exp` | Immediate |
| Token type | JWT only | Also opaque tokens |
- **Do (default):** local validation + short token lifetime (minutes) — revocation delay is bounded by `exp`.
- **Do (introspection):** when immediate revocation is a hard requirement (banking, admin actions) or tokens are opaque.
- **Pitfall:** with local validation a **revoked client gets no 401** — the resource server accepts its token (200)
  until `exp`. Key changes do give 401 (15.23); revocation does not. Bound it with a short token lifetime.

#### 15.5 What the resource server must check
- **Do:** signature, `iss`, `exp`/`nbf` (Spring does these by default with `issuer-uri`), **plus `aud`** and the **scope**.
- **Pitfall:** no audience check → a token issued for **another** API of the same authorization server is accepted
  (a compromised low-privilege service can call you). Spring checks `aud` only if configured:
  ```yaml
  spring.security.oauth2.resourceserver.jwt:
    issuer-uri: ${OAUTH2_ISSUER_URI}
    audiences: track-login-service
  ```
- **Pitfall:** Keycloak does not put a custom `aud` into client-credentials tokens by default → add an audience mapper.
- **Know:** `scope` claim `logins:write` → authority `SCOPE_logins:write` → `hasAuthority("SCOPE_logins:write")`.

#### 15.6 `issuer-uri` means startup and runtime dependencies
- **Know:** with `issuer-uri` Spring fetches the discovery document (`/.well-known/openid-configuration`) and the JWKS
  **lazily on the first request** (resource server), then caches the keys; a new `kid` triggers a refetch (key rotation).
- **Alternative:** `jwk-set-uri` (no discovery) or `public-key-location` (static key, no network, no rotation).

#### 15.7 401 vs 403
- **401 Unauthorized:** no token / invalid / expired / wrong `aud` — "who are you?" (`WWW-Authenticate: Bearer ...`).
- **403 Forbidden:** valid token, missing scope — "I know you, you may not".
- **Verify:** a test for each: no token → 401, wrong scope → 403, right scope → 200.

#### 15.8 Stateless API: no session, no CSRF
- **Do:** `SessionCreationPolicy.STATELESS`, CSRF disabled — CSRF exploits cookies sent automatically by a browser;
  a bearer token is sent only by code that has it.
- **Pitfall:** disabling CSRF in an app that **also** uses session cookies (a web UI) — then CSRF is needed there.

#### 15.9 Deny by default
- **Do:** explicit rules for each endpoint, then `anyRequest().denyAll()` — a new endpoint is closed until someone opens it.
- **Do:** open health probes (`/actuator/health/**`) for the runtime; metrics/env/heapdump never public
  (separate management port or authenticated).

### Tier 1 — client

#### 15.10 Adding a Spring Security starter locks the whole app
- **Pitfall:** adding `spring-boot-starter-security-oauth2-client` to a service that only **calls** an API — Spring Boot
  auto-configures security for **all incoming** requests (basic auth, generated password in the log). Actuator answers
  401: the container healthcheck fails, Prometheus cannot scrape.
- **Why:** the starter brings `spring-security-web`; without your own `SecurityFilterChain` Boot applies its default.
- **Do:** own `SecurityFilterChain` in the client service too: open what the runtime needs, deny the rest.
- **Verify:** `docker inspect` health status, Prometheus targets page — both still green after adding the starter.

#### 15.11 Client registration in configuration
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          track-login:                              # registration id, referenced in code
            provider: keycloak
            client-id: ${OAUTH2_CLIENT_ID}
            client-secret: ${OAUTH2_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: logins:write
        provider:
          keycloak:
            token-uri: ${OAUTH2_TOKEN_URI}
```
- **Know:** `provider.issuer-uri` instead of `token-uri` makes the client fetch the discovery document **at startup** →
  the service does not start while the authorization server is down. `token-uri` alone has no startup dependency.
- **Do:** secret only from the environment (no default, fail fast); production: a secret store, rotation.

#### 15.12 Attaching the token to outgoing calls — options
The token must reach every request to the protected API. Where to plug it in:

**A. `@ClientRegistrationId` on the HTTP interface** (Spring Security 7 HTTP Service Client integration)
```java
@HttpExchange("/api/v1/logins")
@ClientRegistrationId("track-login")          // on the interface or on single methods
public interface TrackLoginApi { ... }

@Bean
OAuth2RestClientHttpServiceGroupConfigurer securityConfigurer(OAuth2AuthorizedClientManager manager) {
    return OAuth2RestClientHttpServiceGroupConfigurer.from(manager);
}
```
- **Plus:** least code; per-method control (one interface, different registrations / public methods).
- **Minus:** when the interface is a **shared contract** (also implemented by the server), the contract gets a
  Spring Security dependency and the **client's** registration id — the contract starts to know who calls it and how.
- **Fits:** the interface belongs to the client only (e.g. a client for a third-party API).

**B. Interceptor on the client's HTTP service group** (configured in the calling service)
```java
@Bean
RestClientHttpServiceGroupConfigurer trackLoginOAuth2(OAuth2AuthorizedClientManager manager) {
    var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
    interceptor.setClientRegistrationIdResolver(request -> "track-login");   // fixed: one downstream, one registration
    return groups -> groups.filterByName("track-login")
            .forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
}
```
- **Plus:** the contract stays clean (same reasoning as resilience annotations, 3.5); all security of the call lives
  in the client's configuration next to its timeouts.
- **Minus:** a few lines more; every call of the group gets the token (fine for one protected downstream).
- **Fits:** shared contract modules — our case.

**C. Hand-written interceptor** (call the token endpoint yourself, set the header)
- **Plus:** no Spring Security dependency.
- **Minus:** you re-implement caching, expiry with clock skew, concurrent refresh, error handling — easy to get wrong
  (a token per request, or an expired token sent). Avoid unless the framework is not available.

**D. Infrastructure adds the token** (API gateway / proxy in front of the downstream)
- **Plus:** services contain no OAuth code.
- **Minus:** extra infrastructure; the service-to-service hop itself is not authenticated by the app.

- **Know:** in all Spring variants the actual work is done by `OAuth2ClientHttpRequestInterceptor` +
  `OAuth2AuthorizedClientManager`; A and B differ only in **where** the registration id is decided.

#### 15.13 Authorized client manager outside an HTTP request
- **Pitfall:** `DefaultOAuth2AuthorizedClientManager` (the usual default in servlet apps) works inside an incoming HTTP
  request only — it stores tokens per user session. Calls from a **Kafka listener, scheduler or startup code** have no
  request → failure (`IllegalArgumentException: servletRequest cannot be null`) or no token.
- **Do:** `AuthorizedClientServiceOAuth2AuthorizedClientManager` — made for background threads; tokens kept in an
  `OAuth2AuthorizedClientService` (in-memory by default):
  ```java
  @Bean
  OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                        OAuth2AuthorizedClientService authorizedClients) {
      var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
      manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
      return manager;
  }
  ```
- **Verify:** an IT that triggers the call from the listener thread, not from a web request.

#### 15.14 Token caching
- **Know:** the manager reuses the token until shortly before `exp` (default clock skew 60s), then requests a new one.
- **Pitfall:** a token request per call (hand-written client, or a new manager per call) — load on the authorization
  server, added latency, rate limits.
- **Verify:** WireMock as token endpoint: N API calls → **1** token request.
- **Pitfall:** the clock skew also decides when an authorization server outage starts to hurt. Token valid 5 min,
  skew 60 s → from minute 4 the manager no longer hands out the cached token, it requests a new one; with the
  authorization server down that request fails and the call fails — although the old token is valid for one more
  minute. The outage hits after `lifetime − skew`, not after `lifetime`.

#### 15.15 Token request failures
- **Know:** if the authorization server is down, the call fails **before** the API request — with an OAuth2 exception,
  not a `RestClientException`.
- **Do:** decide whether retry / circuit breaker should cover it (they see only exception types they are configured for);
  test it: token endpoint 503 → expected outcome (here: retried, then UNSUCCESSFUL).
- **Idea (to think through):** a separate retry policy for the token request (its own exceptions and timing), apart from
  the API call's retry. Nuance: the token is requested inside the interceptor, so the retry belongs in the token client,
  not in `@Retry` on the calling method. The 401-after-key-rotation case is different: it comes from the API call (15.23).

#### 15.15a Token requests use their own HTTP client — without your retries and timeouts
- **Pitfall:** retries are disabled and timeouts set for the API client, yet with the authorization server down the
  test counts **twice** as many token requests as Resilience4j attempts (4 attempts → 8 token requests).
  Not covered by any test: a hanging authorization server blocks the calling thread with **no timeout**.
- **Why:** Spring Security sends token requests with its **own** internal `RestClient`, not through your HTTP service
  group or `RestClient` — none of their settings apply. With Apache HttpClient on the classpath it uses its defaults:
  one automatic retry on 503/429 (`DefaultHttpRequestRetryStrategy`) and no read timeout.
- **Do:** give the token client its own `RestClient`, built from the **same** request factory builder as the API client
  (one place for "no library retries") with explicit timeouts. A custom `RestClient` must carry the converters and the
  error handler Spring Security needs, otherwise token responses are not parsed:
  ```java
  var restClient = RestClient.builder()
      .requestFactory(requestFactoryBuilder.build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
      .configureMessageConverters(converters -> converters
          .disableDefaults()
          .addCustomConverter(new FormHttpMessageConverter())
          .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
      .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
      .build();
  var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
  tokenResponseClient.setRestClient(restClient);
  // manager: OAuth2AuthorizedClientProviderBuilder.builder()
  //     .clientCredentials(cc -> cc.accessTokenResponseClient(tokenResponseClient)).build()
  ```
- **Know (Spring 7 / Boot 4):** `HttpComponentsClientHttpRequestFactory#setConnectTimeout` is gone → `HttpClientSettings`;
  `RestClient.Builder#messageConverters(...)` is deprecated → `configureMessageConverters(...)` (`disableDefaults()` = old `clear()`).
- **Know:** the time budget per attempt grows: token request + API call. Recheck outer limits
  (`max.poll.interval.ms`, graceful shutdown chain) when adding authentication.
- **Verify:** token endpoint stub returns 503 → exactly N token requests for N attempts, 0 API requests.
- **Pitfall — invisible in metrics:** a `RestClient` built by hand (`RestClient.builder()`) has no `ObservationRegistry`
  → its calls do **not** appear in `http_client_requests_seconds`. The token requests to Keycloak are then missing from
  the client metrics (assessment: only the calls to track-login-service are visible).
- **Do:** build it from Spring Boot's `RestClient.Builder` bean (observation configured) or call
  `.observationRegistry(registry)` on the builder; check `/actuator/prometheus` for the token endpoint's `uri`.

### Tier 2 — common

#### 15.16 The issuer is a URL — and must match exactly
- **Pitfall:** a token requested via `localhost:8180` has `iss = http://localhost:8180/realms/x`; the resource server
  configured with `http://keycloak:8080/realms/x` rejects it (401). Same for http vs https, trailing slash.
- **Do:** all parties use the same authorization server URL (inside compose: the service name);
  in Keycloak a fixed `KC_HOSTNAME` makes `iss` independent of the request URL.

#### 15.17 Token lifetime tradeoff
- **Know:** short (minutes) = small window after revocation, more token requests; long (hours) = stolen token usable for hours.
- **Do:** keep it short for service tokens — caching makes the extra requests cheap.

#### 15.18 Testing security without an authorization server
| Test | Tool | Proves |
|---|---|---|
| Resource server, web slice | MockMvc + `jwt()` post-processor (`spring-security-test`) | Access rules: 401 / 403 / 200 |
| Resource server, E2E | `@MockitoBean JwtDecoder` returning a `Jwt` | Real filter chain, real `Authorization` header |
| Client | WireMock as **token endpoint** + as API; assert the `Authorization: Bearer` header | Token obtained, cached, attached |
| Full stack | Keycloak in Testcontainers (optional) | Real tokens, real realm config — slow, usually local/compose only |
- **Pitfall:** `jwt()` bypasses decoding — it does not test `issuer-uri` / `aud` validation; that needs a real decoder
  (signed test token) or a manual check against the running stack.
- **Pitfall (client):** the token store is a singleton in the cached Spring context, like a circuit breaker — a token
  from one test is reused in the next; "token requested once" then depends on test order.
- **Do:** remove the token in `@BeforeEach`: `authorizedClientService.removeAuthorizedClient(registrationId, "anonymousUser")`
  (the interceptor stores client-credentials tokens under the anonymous principal when no user is authenticated).
- **Do:** token endpoint on the **same** WireMock as the API (`token-uri: ${...base-url}/test-token`) — one stub server,
  no extra port; stub it in `@BeforeEach`, override it in token-failure tests.

#### 15.19 Secrets in local setups
- **Do:** demo secrets only in `.env`, passed into containers as env vars; never in code, YAML defaults or images.
- **Know:** Keycloak realm import resolves `${NAME}` placeholders (see 15.22) — the realm file in the repository holds no secret.
- **Pitfall:** `start-dev`, admin `admin/admin`, no TLS — fine locally, never beyond.

#### 15.22 Keycloak realm placeholders: `${NAME}`, not `${env.NAME}`
- **Pitfall:** `"secret": "${env.PROCESSING_CLIENT_SECRET}"` is not resolved — the import succeeds and stores the text
  literally; every token request then fails with `unauthorized_client` (401).
- **Do:** `${PROCESSING_CLIENT_SECRET}`; verify by requesting a token with the real secret.
- **Know:** without a valid token the call is not even made — the downstream's request log stays empty; look at the
  client's log for the reason.

#### 15.23 Stale cached token after a key change
- **Pitfall:** Keycloak restarted in `start-dev` (keys in memory) or rotated keys → the cached token is still "valid"
  for the client, the resource server rejects it (unknown key) → 401 for minutes. 401 is not retried and not recorded
  by the breaker: silent `UNSUCCESSFUL`.
- **Do:** failure handler on the interceptor (removes the cached client on 401) + one retry on 401.
- **Know:** the failure handler alone fixes only the **next** request; the current one still fails.
- **Know:** this is the classic exception to "never retry 4xx" (4.3): not a plain retry, but refresh the token, then
  repeat once.
- **Pitfall:** adding 401 to `retry-exceptions` alone is useless — every attempt takes the **same** stale token from the
  cache and gets 401 again. Retry permanent failure, not recovery.
- **Do:** 401 in `retry-exceptions` **plus** the failure handler that drops the token: attempt 1 → 401 → token removed →
  attempt 2 fetches a new token from Keycloak → passes. Cost: a real 401 (wrong `aud`, missing rights) uses all attempts,
  each with a fresh token, and is counted by the breaker; "only once for 401" is hard with one shared `@Retry`.
- **Know:** the client cannot check in advance whether the resource server still accepts a token. The manager checks
  only `exp` (minus clock skew). Keys, revocation, audience are known to the resource server only; for the client the
  token is an opaque string (OAuth2). The only reliable signal is the 401.

#### 15.24 The resource server's key download has no timeouts
- **Know:** with `issuer-uri`, Spring downloads discovery + JWKS with its own default client — no timeouts.
  Rare (first request, key rotation), but a hanging Keycloak then blocks request threads.
- **Do:** own `JwtDecoder` with a timed `RestOperations` — same lesson as 15.15a.

#### 15.25 Claims a reviewer may make — and why they do not hold here
- **"Thundering herd on token refresh":** concurrent refreshes are bounded by the listener threads per instance
  (here 3) once per token lifetime — negligible; single-flight would add complexity for nothing.
- **"Token expires in flight":** the client refreshes 60 s before `exp`, the resource server allows 60 s clock skew,
  both containers share the host clock — milliseconds of network latency cannot bridge 2 minutes.
- **"A short Keycloak hiccup opens the breaker":** retries absorb it; opening needs the minimum number of calls and
  the failure rate.

### Tier 3 — rare

#### 15.20 Alternatives to OAuth2 for service-to-service
| Option | Plus | Minus |
|---|---|---|
| OAuth2 client credentials | Standard, scopes, short-lived tokens, central client management | Authorization server to run |
| Self-signed JWT (shared key or own key pair) | No extra server | Key distribution and rotation by hand; no central revocation |
| mTLS (client certificates) | Strong, at transport level, no tokens | Certificate issuing and rotation (PKI); no fine-grained scopes |
| API key / static secret | Simplest | Long-lived, no scopes, leaks are hard to notice |

#### 15.21 Keycloak-specific traps
- **Know:** `include.in.token.scope` decides whether a client scope appears in the `scope` claim.
- **Know:** a client scope is in the token only if assigned to the client (default or requested optional scope).
- **Know:** `--import-realm` skips an **existing** realm — with a persistent database, changes to the file are ignored.
- **Know:** `start-dev` uses an in-memory database → realm re-imported on every start; manual console changes are lost.

### Explain it — interview questions

1. **Name the three OAuth2 roles in a service-to-service call. Can one service have two roles?** (15.1)
2. **Why `client_credentials` here and not `authorization_code`? What is wrong with the password grant?** (15.2)
3. **Is a JWT encrypted? What can anyone read from it?** (15.3)
4. **How does the resource server validate a token without calling the authorization server? What is the cost?** (15.4)
5. **Local validation vs introspection: when would you choose introspection?** (15.4, 15.17)
6. **Which claims must be checked, and what goes wrong if `aud` is not checked?** (15.5)
7. **401 vs 403 — give an example of each.** (15.7)
8. **Why is CSRF disabled, and when would that be a mistake?** (15.8)
9. **You add the OAuth2 client starter and the healthcheck turns red. Why?** (15.10)
10. **How does the token get onto outgoing requests? Which options exist and why did you choose this one?** (15.12)
11. **The call comes from a Kafka listener thread. What is special about getting a token there?** (15.13)
12. **How often is a token requested? How do you prove it?** (15.14)
13. **The authorization server is down for a minute. What happens to the client, and to the resource server?** (15.4, 15.15)
14. **A token works in one environment and gets 401 in another. First thing you check?** (15.16)
15. **How do you test security without running Keycloak?** (15.18)
16. **What are alternatives to OAuth2 for service-to-service calls?** (15.20)
17. **Retries are disabled in your HTTP client, yet the authorization server gets twice as many requests — and a hanging
    authorization server blocks your consumer. Why, and how do you fix it?** (15.15a)
18. **Keycloak imported your realm but every token request fails with invalid client credentials. First suspect?** (15.22)
19. **Keycloak restarts. What happens to calls with the cached token, and how would you recover faster?** (15.23)
20. **Where else does Spring make HTTP calls with its own client, and why does it matter?** (15.15a, 15.24)
21. **A reviewer says token refresh causes a thundering herd and tokens expire in flight. Agree?** (15.25)
22. **Keycloak changed its keys. What happens to the cached token, what does the client see, how does it recover?**
    The client still considers the token valid (`exp` not reached); the resource server cannot verify the signature
    → 401. Plain retry with the cached token fails again; drop the token on 401 (failure handler), fetch a new one,
    repeat. Without that: 401s until the token expires. (15.23)
23. **The client was revoked in Keycloak. What happens to its token?**
    With local validation — nothing: the resource server never asks Keycloak, the token is accepted (200) until `exp`.
    No 401, so nothing to react to on the client side. With introspection — rejected at once. (15.4)
24. **How does the authorized client manager decide whether to reuse a cached token?**
    Only by time: `exp` minus clock skew (60 s). It cannot know about key changes or revocation — only the resource
    server can, and it signals with 401. (15.14, 15.23)
25. **Local validation vs introspection — which options exist and what are the trade-offs?**
    Local JWT validation: keys (JWKS) cached, no call per request, no runtime dependency on Keycloak; revocation seen
    only at `exp`. Introspection (RFC 7662): Keycloak asked per request (or cached for seconds) — revocation at once,
    but latency per call and Keycloak becomes a runtime dependency (down → every request fails). Middle ground: local
    validation + short token lifetime (1–5 min) bounds the revocation delay; introspection only where immediate
    revocation is a hard requirement (admin actions, payments). (15.4, 15.17)
