# 02_Application_Architecture

**Question it answers:** How is ONE service built inside — how its code is split, where each rule lives,
how it is configured, built and tested?

The other folders describe mechanisms (data correct → 05, keeps serving → 06, many systems → 08, running → 09,
security → 11). This folder is the frame they plug into: the service boundary from the inside.

**Sources (candidates):** Get Your Hands Dirty on Clean Architecture (Hombergs); Domain-Driven Design Distilled
(Vernon); Spring Modulith docs; own Pitfalls file — sections 1, 2, 4.1–4.2, 8, 10.

## Topics

- **Layers and dependency direction** — layered vs hexagonal (ports & adapters) vs clean architecture;
  what depends on what; where controller, service, repository, client end up.
- **Modules inside one service** — package by layer vs by feature; modular monolith; module boundaries
  checked by tools (Spring Modulith, ArchUnit).
- **Domain model (DDD tactical)** — entity, value object, aggregate; aggregate = consistency boundary
  (semantics of the transaction → 05/1).
- **Where the transaction boundary sits in code** — which layer owns `@Transactional`, why not the controller,
  what changes when a remote call or a catch is inside it.
- **Framework magic: proxies and annotations** — AOP proxy, self-invocation, an annotation that silently does
  nothing without its starter; resilience and security annotations on a wrapper bean, never on the shared
  interface.
- **Data access** — repository with only the needed methods, DTO vs entity, mapping, N+1, pagination
  (offset vs keyset), `open-in-view`.
- **Validation and error handling** — validate at every boundary where data is created; contract rules vs
  server-only rules; three lines of defense (request, entity, database); one error format (Problem Details).
- **Configuration** — environment contract (no defaults, fail fast), config split by concern, names from config
  not constants, test config overrides not replaces.
- **Build** — parent POM, quality gates bound to `verify`, overriding a managed version via a property,
  independent builds vs multi-module.
- **Testing strategy** — unit / component / layer IT / one E2E per service; contract test through the shared
  interface; counting requests in WireMock; shared cached Spring context and order-dependent tests;
  Testcontainers singleton.

## Not here

- API contracts and compatibility between services → `08_Integration/1_Encoding_and_Evolution`.
- Isolation levels, races, ORM locking → `05_Consistency/1_DB_Consistency`.
- Retries, timeouts, circuit breaker, pools → `06_Resilience`.
- Logs, metrics, traces, health → `09_Operability`; Docker, deployment, config delivery at runtime → `09/4`.
- Authentication, secrets, personal data in logs, CVEs → `11_Security`.
