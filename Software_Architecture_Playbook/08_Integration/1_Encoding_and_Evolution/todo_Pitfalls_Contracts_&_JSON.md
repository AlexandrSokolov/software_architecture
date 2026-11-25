# 08_Integration / 1_Encoding_and_Evolution — pitfalls from practice: shared contract and JSON

**Question it answers:** Two services share an HTTP interface and DTOs, and events travel as JSON. How does the
contract break without any Java test noticing, and how do I keep it tolerant to evolution?

**Source:** `developer-pitfalls.md` (draft v4) §2.1–2.3, §2.6–2.9, §2.11, §2.17, §3.5, §6.12, §10.8a;
login-tracking assessment (login-api: `TrackLoginApi`, `LoginEvent`, `LoginTrackingResult`, serialization tests;
`TrackLoginServiceEndToEndIT` as contract test).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Testing the contract

- [ ] **Contract tested as JSON, not as Java objects** — an object round-trip is symmetric and hides a renamed
      field or a changed date format. Do: serialization against a fixed JSON string with strict JSONAssert,
      deserialization with `equals`. Verify: renaming a record component fails the test.
- [ ] **Contract test for free** — the server's E2E builds its client from the shared interface: path, method,
      JSON, status codes proven together. Did: `TrackLoginServiceEndToEndIT`.
- [ ] **Strict comparison catches real bugs** — an unexpected extra field was a real mismatch, not noise.

## 2. Evolution: tolerant readers

- [ ] **Unknown fields must not break the reader** — `FAIL_ON_UNKNOWN_PROPERTIES = false`; Boot's mapper sets it,
      a hand-made `JsonMapper` may not — check. Verify with a JSON that has an extra field.
- [ ] **Unknown enum values** — a new `"client": "DESKTOP"` fails deserialization. Decide explicitly: fallback
      (`@JsonEnumDefaultValue UNKNOWN`) or deliberate rejection (400 / DLT). Never by accident.
      Did: rejected → 400 on REST, DLT on Kafka.
- [ ] **Read-modify-write loses fields the client doesn't know** — an old client wipes newer fields. Do: keep
      unknown fields (`@JsonAnySetter` / `@JsonAnyGetter`) or PATCH with changed fields only.
- [ ] **Timestamps** — `Instant` as ISO-8601 UTC (`2026-11-19T12:43:00Z`); not epoch numbers, not local time
      without offset; the contract test fixes the exact string.

## 3. Enum values: JSON vs Java vs database

- [ ] **The spec says `web` / `successful`, the code says `WEB`** — a reviewer sending the spec's value gets a
      deserialization error (event in the DLT).
- [ ] **Renaming constants to lowercase also changes what `@Enumerated(STRING)` stores** — a CHECK on `'WEB'`
      then rejects every insert (500).
- [ ] **Jackson uses `name()`, not `toString()`** — `name()` and `ordinal()` are final.
- [ ] **Do:** decouple the JSON form — `@JsonValue` on `name().toLowerCase(Locale.ROOT)` or `@JsonProperty` per
      constant; Java names and DB values stay. `Locale.ROOT` matters: Turkish locale → `"ıos"`.
      Did: lowercase constants matching the spec + CHECK on the lowercase values (a choice, not the general fix).

## 4. Shared contract module

- [ ] **HTTP interface (`@HttpExchange`) + DTOs in a separate jar** — both sides compile against it; framework deps
      `provided`, no `application.*`.
- [ ] **The contract jar is a hidden coupling** — a breaking change breaks all consumers at once; semantic
      versioning, only additive changes within a major version.
- [ ] **Minimal HTTP interface** — `void` + exceptions for "succeeded or not", no `ResponseEntity` / `boolean`;
      `@RequestBody` is required; define the success status explicitly (200 vs 201 vs 204).
- [ ] **Keep client concerns out of the contract** — no `@Retry`, `@CircuitBreaker`, `@ClientRegistrationId`,
      `@Validated`: the contract would know who calls it and how. Did: all on the client's side.
- [ ] **Validation rules in the contract** — only rules both sides agree on; springdoc puts them into the OpenAPI
      schema → `02_Application_Architecture/todo_Pitfalls_Application.md` §3.
- [ ] **API explorable locally** — OpenAPI + Swagger UI with a way to get a local test token.

## 5. Kafka message format

- [ ] **Type headers couple services** — Spring's JSON serializer adds `__TypeId__` with the Java class name; the
      consumer must know the producer's classes. Do: `add.type.headers=false`, consumer
      `use.type.headers=false` + `value.default.type`; test with a raw JSON producer. Side effect: no "trusted
      packages" needed.
- [ ] **Input value kept as `String` on purpose** — `InetAddress` deserialization may trigger DNS and normalizes
      IPv6; validate the format instead.

## 6. Library migration traps

- [ ] **Jackson 3 mixed packages** — `tools.jackson.*`, annotations stay in `com.fasterxml.jackson.annotation`;
      Jackson 2 classes on the classpath compile but are silently ignored; `ObjectMapper` → `JsonMapper`.

---

## Covered elsewhere

- Existing theory: `3.6.1`–`3.6.5` files in this folder (compatibility, formats, schema evolution, API design,
  versioning).
- Personal data in `toString()` and error responses → `11_Security/todo_Pitfalls_Security.md`.
