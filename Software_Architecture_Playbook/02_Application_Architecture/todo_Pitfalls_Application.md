# 02_Application_Architecture — pitfalls from practice

**Question it answers:** Which traps inside ONE service (build, framework magic, validation, configuration,
tests) did I actually meet, and what did I do about them?

**Source:** `developer-pitfalls.md` (draft v4) §1, §2, §4.1–4.2, §6, §8, §10, §13; login-tracking assessment
(login-processing-service, track-login-service, login-api, login-tracking-parent).

**Format:** each item = pitfall → why → what to do. **Did:** = what I did in the project, short enough to say
out loud in an interview.

---

## 1. Build and parent POM

- [ ] **Quality gates live in the parent** — JaCoCo, SpotBugs + FindSecBugs, PMD bound to `verify`; switches and
      thresholds as properties, a child overrides a property, never copies plugin config.
      Did: one parent for all projects, 90% line coverage, a child without any plugin section still fails.
- [ ] **Every parent feature switchable per child** — so a legacy project can take only part of it (only gates,
      only the IT profile, an older Java).
- [ ] **Parent tied to one Spring Boot version** — a legacy project on another Boot can't use it; split into a
      Boot-independent quality parent + a Boot parent on top, if needed.
- [ ] **Silencing a static-analysis false positive** — never a global exclude; `@SuppressFBWarnings` on the field,
      with a justification. Did: `EI_EXPOSE_REP2` on the injected `KafkaTemplate`.
- [ ] **Integration tests silently never run** — Surefire includes only `*Test`; `*IT` needs Failsafe.
      Did: Failsafe in the `integration-tests` profile, run in CI.
- [ ] **`@{argLine}` breaks when JaCoCo is skipped** — empty `<argLine/>` default in properties.
- [ ] **Mockito self-attach warning (JDK 21+, JEP 451)** — load Mockito as `-javaagent` in Surefire and Failsafe.
- [ ] **Java version enforced but flexible** — Enforcer `requireJavaVersion` driven by the `java.version`
      property; always build via `./mvnw` (the wrapper pins Maven, not the JDK).
- [ ] **Team-wide banned dependencies** — Enforcer `bannedDependencies` with a message (Lombok).
- [ ] **Overriding a vulnerable transitive version** — override the Boot property (`jackson-bom.version`), never
      declare the dependency directly (breaks BOM alignment). CVE triage → `11_Security`.
- [ ] **Independent builds test the last installed contract** — without a reactor, children resolve the parent
      and the contract from `~/.m2`; after a contract change `install` the chain. Quality gates don't run on
      `package`.
- [ ] **Library ships `application.properties`** — leaks into every consumer; libraries never contain
      `application.*`.
- [ ] **Null-safety with Spring 7 (JSpecify)** — `@NullMarked` in `package-info.java` instead of `@NonNull` on
      every parameter.
- [ ] Decide: **independent builds vs multi-module** — release on its own vs one reactor build.

## 2. Framework magic: proxies, auto-configuration, starters

- [ ] **Annotation silently does nothing** — `@Retry` compiles, one request goes out: no AOP starter, no proxy.
      Prove it: remove the dependency once, the test must fail.
- [ ] **Self-invocation bypasses the proxy** — same for `@Transactional`, `@Retry`, `@CircuitBreaker`,
      `@Validated`. Did: resilience annotations on a separate wrapper bean (`TrackLoginClient`).
      Link: `05_Consistency/1_DB_Consistency/todo_Transactions_Scenarios_ORM.md` (partial writes stay).
- [ ] **Missing starter silently ignores config** — `spring.http.serviceclient.*` ignored without
      `spring-boot-starter-restclient` ("Target host is not specified").
- [ ] **A second bean of the same type removes Boot's one** — `@ConditionalOnMissingBean` checks the type, not the
      name. Did: the DLT `KafkaTemplate` built privately inside the error-handler bean.
- [ ] **Adding a starter changes behaviour elsewhere** — the OAuth2 client starter locks all incoming requests →
      `11_Security`.
- [ ] **Concerns don't belong in the shared interface** — retry, breaker, authentication on the client's side,
      not on the `@HttpExchange` contract. Did: wrapper bean + group configurers.

## 3. Validation and error handling inside one service

- [ ] **Validate at every boundary where data is created or changed** — input you receive and output you build;
      a DTO passed through unchanged needs only the input check.
- [ ] **Rules in the shared contract vs a server-only DTO** — decide explicitly. Did: rules in the contract,
      `jakarta.validation-api` as `provided`, the provider stays with the side that validates.
- [ ] **`@Valid` belongs to the interface parameter** — an implementing method may not add parameter
      constraints.
- [ ] **`@Valid` on the shared interface works on the server only** — the generated client proxy ignores it;
      no provider on the client without `spring-boot-starter-validation`; `@Valid` alone accepts `null`.
- [ ] **Where to validate outside the server** — Kafka listener (`setValidator` or `@Validated`), client wrapper,
      HTTP service group hook. Avoid `@Validated` on the shared interface: double validation, 500 instead of 400,
      `spring-context` in the contract.
- [ ] **Three lines of defense** — request (`@Valid` → 400), entity (Bean Validation at flush →
      `TransactionSystemException`), database (constraints → `DataIntegrityViolationException`).
      `@Column(nullable = false)` is DDL metadata only.
- [ ] **Two classes called `ConstraintViolationException`** — `jakarta.validation` vs `org.hibernate.exception`;
      a wrong import tests the opposite.
- [ ] **Custom constraint in the contract** — annotation + validator in the contract module; the validator
      returns `true` for `null` (one constraint, one rule). Did: `@IpAddress` with `InetAddress.ofLiteral`.
- [ ] **Validating an IP with `InetAddress.getByName()`** — DNS lookup for host names (latency, SSRF).
- [ ] **Error responses that say what is wrong** — Problem Details with an `errors` list (field + message),
      `spring.mvc.problemdetails.enabled`. Not echoing rejected values → `11_Security`.
- [ ] **Business outcome vs failure** — return an outcome (`unsuccessful`), throw only unexpected errors;
      decides the Kafka offset → `05_Consistency/3_Cross-System_Consistency/todo_Pitfalls_Cross-System.md`.

## 4. Configuration

- [ ] **Environment values without defaults** — `${X:localhost}` silently points to the wrong place in
      production; no default = fail fast at startup. Did: `settings/env.yaml` as the environment contract.
- [ ] **Configuration split by concern** — one file per concern (kafka, http-client, oauth2, actuator,
      timeouts), every key in exactly one file, all durations of the chain in one file with the formula on top.
- [ ] **Test config overrides, not replaces** — `src/test/resources/config/application.yaml`, only differences.
- [ ] **Duplicate YAML keys** — a second `spring:` block overwrites the first.
- [ ] **Names from config, not constants** — topic names via `@Value` in code and tests.
- [ ] **A setting with no Boot property** — set it via a customizer bean, keep the value in YAML next to the
      others (listener shutdown timeout → `09_Operability/4_Infrastructure/todo_Pitfalls_Infrastructure.md`).
- [ ] **Client defaults are invisible** — Kafka client defaults live in `kafka-clients` code; read the
      `ConsumerConfig values:` block in the startup log.
- Not here: secrets → `11_Security`; how the runtime delivers config (env, Kubernetes Secrets) → `09/4`.

## 5. Persistence code inside the service

- [ ] **Entity basics** — no record (no-arg constructor, proxies); `protected` constructor for JPA;
      `timestamp with time zone` for `Instant`; `open-in-view: false`.
- [ ] **Repository with only the needed methods** — extend the marker `Repository<T, ID>`.
- [ ] **SpotBugs `URF_UNREAD_FIELD` on entity fields** — add getters, don't suppress every field.
- [ ] **Where the transaction boundary sits in code** — Did: the service is deliberately NOT `@Transactional`
      so a duplicate fails inside `save()` → `05_Consistency/1_DB_Consistency/todo_Pitfalls_Idempotent_Insert.md`.

## 6. Testing strategy

- [ ] **Levels** — unit (no Spring) → component (one component in Spring, external side replaced: WireMock,
      MockMvc) → layer IT (one real boundary, Testcontainers) → one E2E per service.
- [ ] **Contract test for free** — the server's E2E builds its client from the shared HTTP interface.
- [ ] **Test the boundaries, not only the happy path** — succeeds on attempt N and gives up after N; a response
      just inside and just outside the timeout.
- [ ] **Read what the tool really counts** — exact request counts in WireMock expose hidden layers
      ("expected 4 token requests, got 8").
- [ ] **A green test for the wrong reason** — the HTTP library retried, not the app.
- [ ] **Prove infrastructure with an experiment** — break the framework magic once on purpose.
- [ ] **Tests must not depend on the developer machine** — external systems only via Testcontainers / WireMock;
      the Kafka listener off by default in tests (a forgotten `true` fails always, a forgotten `false` fails
      sometimes).
- [ ] **Shared state in the cached Spring context** — circuit breaker, token store: reset in `@BeforeEach`;
      Surefire's default run order is the file-system order.
- [ ] **Identical payloads on a shared topic** — a unique id per test, read filtered by it.
- [ ] **Per-class containers + cached contexts = orphaned listeners** — one singleton container for all ITs.
- [ ] **Property overrides create a new context** — such a test in its own small class; a lowered timeout proves
      the wiring, not the production value.
- [ ] **`@MockitoBean` creates a separate context** — a few seconds per variant.
- [ ] **`@DataJpaTest` wraps every test in a rolled-back transaction** — a duplicate never fails there;
      `NOT_SUPPORTED` + clean the table; `replace = NONE` to keep Testcontainers.
- [ ] **Async assertions: poll, don't sleep** — Awaitility, `verify(..., timeout(...))`; in E2E wait for the final
      output first, then check side effects.
- [ ] **Explicit construction in unit tests** — `new Service(mock)` instead of `@InjectMocks`.
- [ ] **Strict JSON comparison catches real bugs** — an unexpected extra field was a real mismatch.
- [ ] **Debugging aids off by default** — `andDo(print())`, PostgreSQL `log_statement`, `liquibase.executor`.
- [ ] **Simulating network faults** — WireMock scenarios (500 → 503 → reset → 200), `withFault`,
      `withFixedDelay`; `docker compose pause` for a hanging service.
- [ ] **Coverage-only tests** — a test that exists only for the threshold adds no quality; say so in its comment.

---

## Covered elsewhere

- Proxies and `@Transactional` traps → `05_Consistency/1_DB_Consistency/todo_Transactions_Scenarios_ORM.md`.
- Fault injection as a resilience practice → `06_Resilience/todo_Resilience.md` §5.

## Not here

- API contract compatibility between services → `08_Integration/1_Encoding_and_Evolution/todo_Pitfalls_Contracts_&_JSON.md`.
- Retries, timeouts, pools → `06_Resilience`.
- Docker, healthchecks, graceful shutdown → `09_Operability/4_Infrastructure`.
- Secrets, personal data, CVEs, security starters → `11_Security`.
