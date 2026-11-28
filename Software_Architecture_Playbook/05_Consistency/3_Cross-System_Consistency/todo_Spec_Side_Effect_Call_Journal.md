# Spec: Side-Effect Call Journal (annotation-based)

## Goal

Make remote calls with a side effect (payment, shipment) crash-safe without any intent/status code in business
logic. The client method gets an annotation; an aspect records the intent before the call and the outcome after it;
a sweeper finishes calls left unfinished by a crash.

This is one possible solution, not the only one — see [Alternatives](#alternatives).

## Scope

- **In**:
  - synchronous REST calls with a side effect, one remote call per annotated method;
  - idempotency key: created, stored, sent with every attempt;
  - a journal table;
  - a sweeper for stuck calls.
- **Out**:
  - undo of done steps (saga, compensations);
  - read-only calls (nothing to protect);
  - broker sends (outbox covers them).

## Target API

```java
@SideEffectCall(operation = "payment.charge", key = "#order.id")
@Retry(name = "payment")
@CircuitBreaker(name = "payment")
public ChargeResult charge(Order order) { ... }
```

- `operation` — a stable name of the call type.
- `key` — SpEL over the method arguments; the business identity of this one logical call.
- Idempotency key = `operation + ":" + key` — deterministic, so a resume after a crash produces the same key.
- The key reaches the HTTP request through a context set by the aspect and read by a `RestClient` interceptor,
  which adds the `Idempotency-Key` header. No extra method parameter, no business code involved.

## Journal table

`side_effect_call`:

- `id`
- `operation`
- `idempotency_key` — unique
- `status` — `PENDING`, `DONE`, `FAILED`, `UNKNOWN`
- `request_payload` — enough to repeat the call (masked, see traps)
- `response_payload` — enough to finish the business write without calling again
- `attempts`, `last_error`
- `created_at`, `updated_at`

## Flow

1. **Before the call**:
   - no active transaction → `TransactionSynchronizationManager.isActualTransactionActive()` is true → throw;
   - short transaction (`TransactionTemplate`): insert `PENDING` with the key, commit;
   - key already exists:
     - `DONE` → return the stored response, no call;
     - `PENDING` / `UNKNOWN` → another request or the sweeper owns it → throw a "call in progress" exception;
     - `FAILED` → per operation policy (throw, or allow a new attempt).
2. **The call** — proceeds through retry and circuit breaker, with the same key on every attempt.
3. **After the call**, each a short transaction:
   - success → `DONE` + response;
   - clear failure (4xx, business rejection) → `FAILED`;
   - no answer (timeout, retries exhausted on 5xx) → `UNKNOWN` — never `FAILED`.
4. **Sweeper** (`@Scheduled`):
   - selects `PENDING` / `UNKNOWN` rows older than the maximum call time, `FOR UPDATE SKIP LOCKED`;
   - asks the remote side for the status, or repeats the call with the same key;
   - marks `DONE` or `FAILED`, then hands the result to the operation's completion handler.

## Business completion — the open decision

The aspect knows the call, not the order. Two ways to close the gap between "call done" and "order `PAID`":

- **A — completion handler (default)**: the aspect marks `DONE`; the business code writes `PAID` in its own
  transaction. For each operation a `SideEffectCompletionHandler` is registered; the sweeper calls it for `DONE` rows
  whose business write is missing (a crash between the two). Business code stays clean; the handler must be
  idempotent.
- **B — same transaction**: the aspect leaves the row `PENDING`; the business code marks it `DONE` in the same
  transaction as `PAID`. No gap, but one line of journal code in business logic.

## Traps

- **Aspect order** — the journal aspect must be outermost: journal → retry → circuit breaker → call. Inside the
  retry, every attempt writes a new intent. Resilience4j aspects have their own order properties; set the journal
  aspect's `@Order` explicitly and test it.
- **Random key per invocation** — a resume after a crash would get a new key → a second charge. The key comes from
  business identity and is stored.
- **Same business key, legitimately a second call** (a second payment for the same order) — the key must include
  what makes the call unique (e.g. a payment attempt ID), or the second call returns the first one's result.
- **Outer transaction present** — the "short" transactions join it: the intent isn't durable before the call, and
  the call holds the outer locks. Hence the check that throws.
- **Self-invocation** — a call from the same class skips the proxy: no journal, no retry. Document it; test it.
- **Timeout recorded as `FAILED`** — the remote side may have done the work; a later new attempt charges twice.
  Timeout → `UNKNOWN`, the sweeper checks.
- **Sweeper races a live request** — the row is still `PENDING` because the call is in flight. Age threshold >
  timeout × attempts + backoff; `SKIP LOCKED`; the remote side must deduplicate by key anyway.
- **Concurrent requests with the same key** — the unique constraint lets only one insert win; the other reads the
  existing row and follows step 1.
- **Remote side without idempotency keys** — repeating is unsafe; the sweeper may only ask for the status, or the
  call goes to reconciliation.
- **Context leaks** — the key context is thread-bound: clear it in `finally`; it doesn't work for reactive clients.
- **Sensitive data in payloads** — card data, tokens, personal data: mask or store references only.
- **Journal growth** — retention job for old `DONE` / `FAILED` rows.

## Alternatives

- **Business status in the domain row** (`PAYMENT_PENDING` on the order) — no generic journal; simplest with few
  call types (the approach in `5.3.2`).
- **Outbox for calls** — a command row in the business transaction, a worker makes the call — when the caller
  doesn't need the answer now.
- **Workflow engine** (Temporal, Camunda) — durable execution: every step recorded, retried and resumable;
  multi-step and compensation included; a heavy dependency.
- **Spring Modulith event publication registry** — the same idea for events, not for request/response calls.

## Acceptance tests

Testcontainers (Postgres) + WireMock:

- success → one journal row `DONE`, one remote request, `Idempotency-Key` header present;
- retries → 3 attempts, one journal row, the same key in all 3 requests;
- crash after the call, before `DONE` → row stays `PENDING` → sweeper repeats with the same key → remote dedupes →
  `DONE`, completion handler called once;
- timeout → `UNKNOWN` → sweeper status check → `DONE` or `FAILED`;
- called inside an outer transaction → exception, no remote request;
- two concurrent requests with the same key → one remote request;
- self-invocation → documents that no journal row is written.
