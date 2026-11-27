# 05_Reliability / 2_Cross-System_Consistency

**Question it answers:** My service changes its own database AND tells another system (broker, REST API,
webhook). How do both sides end up consistent when any step can fail?

**Sources:**
- Kleppmann, *Using logs to build a solid data infrastructure (or: why dual writes are a bad idea)*, 2015 — the
  problem.
- Richardson — microservices.io (Transactional outbox, Polling publisher, Transaction log tailing, Idempotent
  consumer, Saga) and *Microservices Patterns* ch. 3 (messaging, outbox), ch. 4 (sagas).
- Morling, *Revisiting the Outbox Pattern*, 2024 (morling.dev) — practice and pitfalls; Debezium docs (Outbox
  Event Router).
- DDIA 2nd ed. — distributed transactions / 2PC, log-based integration (stream chapter).

---

## 1. The problem: dual write

- [ ] What dual write is: app code updates two places (DB + broker, DB + REST, DB + cache/search index).
- [ ] Failure orders: DB commit OK → send fails (event lost); send OK → DB rollback (phantom event).
- [ ] Race condition between two writers: two requests update DB and broker in different orders → permanent
      divergence (Kleppmann's diagram).
- [ ] **Unknown outcome:** a timeout is not a failure — the remote call may have succeeded. What do you do then?
- [ ] Why not 2PC / XA: blocking coordinator, availability cost, most brokers and REST APIs can't take part.
- [ ] Kafka transactions: only cover Kafka → Kafka (consume-transform-produce), not your DB. Link to `Kafka/`.

## 2. Remote call vs local transaction (where to put the call)

- [ ] **Before the transaction** — call first, then write locally; what if the local write fails?
- [ ] **Inside the transaction** — holds a DB connection and row locks during a network call; slow remote →
      pool exhaustion; rollback can't undo the remote side.
- [ ] **After commit** — `@TransactionalEventListener(phase = AFTER_COMMIT)`, `TransactionSynchronization`:
      clean, but lost if the process dies right after commit (not durable).
- [ ] `@TransactionalEventListener` phases (BEFORE_COMMIT, AFTER_COMMIT, AFTER_ROLLBACK, AFTER_COMPLETION) and
      the trap: a new DB write inside AFTER_COMMIT needs `REQUIRES_NEW`.
- [ ] **Markers: when the call MUST stay inside / before** — you need the remote answer to decide (validation,
      reservation ID, payment authorization, uniqueness check owned by another service). Then: idempotency key +
      compensation, or reservation (see §6).
- [ ] **Markers: when to move it out** — notification only, nobody waits for the answer → outbox or after-commit.
- [ ] Durable after-commit: Spring Modulith event publication registry (a built-in outbox-like table).

## todo — DB transaction + network call: what it holds, what breaks

Scope: one `@Transactional` method that also calls the network (REST, broker send, any remote client).
Everything below is held until commit, i.e. for the whole network wait.

### What is held — per level
- **DB row locks** — explicit (`SELECT … FOR UPDATE`) and implicit: any `UPDATE` (e.g.
  `SET counter = counter + 1`) locks the row until commit; every other writer of that row waits.
- **DB connection** — taken from the pool for the whole call; pool runs out → every request that
  needs the DB waits, even ones that never call the remote side.
- **Request thread** — a platform thread blocks; the pool runs out → thread starvation, the
  service stops taking new work.
- **HTTP client connection** — the outgoing pool is held too; a second limit that can run out.
- **Open transaction itself** — Postgres can't clean old row versions while it is open
  ("idle in transaction"); a long one bloats tables for everybody.

### What breaks
- **No timeout on the call** — wait forever → locks, connection and thread never released.
- **Thread starvation deadlock** — the remote side calls back into your service (callback,
  webhook, sync chain) and needs a thread from the same exhausted pool → nobody can finish.
- **Distributed lock deadlock** — you hold a row lock and wait on the remote side; the remote
  side calls back and needs that same row → both wait; the DB can't see it (it's not a DB-only cycle).
- **Cascade upstream** — your service stalls → your callers' threads and pools fill up the same way.
- **Lock waits / lock timeouts for others** — other transactions fail on lock wait, not on their own fault.
- **Transaction timeout during the call** — rollback while the remote side may have done the work.
- **Retry of the whole transaction** — repeats the call (second charge) unless it is idempotent.
- **Virtual threads don't fix it** — threads are cheap, but DB connections and row locks are still limited and still held.

### Marker — when must the call really stay inside?
- **Only when** data read before the call must not change before the write → and even then
  prefer a version check (optimistic) over holding the lock across the call.
- Otherwise → before the transaction, after commit, or between two transactions with a saved status.

### Had to keep it inside — how to limit the cost
- **Timeout on every call**, shorter than the transaction timeout.
- **Call first, lock last** — do the call before any `UPDATE` / `FOR UPDATE`; take locks at the end.
- **`flush()` before the call** — DB errors show up before the remote side changes anything.
- **Separate pool (bulkhead)** for these calls, so a slow remote side can't take all threads → 06_Resilience.
- **Circuit breaker** — fail fast instead of waiting on a dead side → 06_Resilience.
- **Idempotency key** — so a retried transaction repeats nothing.
- **Watch it** — transaction duration, pool usage, lock waits.

## 3. Transactional outbox — the many variants

**Core idea:** write the business row and the event row in ONE local transaction; a separate process delivers
the event.

### 3.1 Outbox table design
- [ ] Columns: id, aggregate type, aggregate id, event type, payload, headers (trace context!), created_at.
- [ ] One generic table vs one per aggregate.
- [ ] **Payload or reference** — full event (fat) vs ID only (thin, consumer calls back); claim check for large
      payloads.

### 3.2 Delivery: polling publisher
- [ ] Simple, no extra infrastructure; cost: DB load, polling latency.
- [ ] Several worker instances: `SELECT … FOR UPDATE SKIP LOCKED` vs single leader (ShedLock, leader election).
- [ ] **Ordering trap:** IDs/sequences become visible out of commit order → a poller can skip a row that commits
      later. How to handle it.
- [ ] Mark-as-sent vs delete-after-send; batch size.

### 3.3 Delivery: transaction log tailing (CDC)
- [ ] Reads the DB commit log (Postgres WAL / MySQL binlog) — Debezium; the outbox row is the change event.
- [ ] Debezium Outbox Event Router: routing by aggregate type, key = aggregate id.
- [ ] Insert + immediate delete in the same transaction — table stays empty, the log still has the event.
- [ ] Postgres `pg_logical_emit_message` — outbox without a table (Morling).
- [ ] Operational cost: replication slots, WAL retention if the connector stops, connector restarts →
      duplicates.

### 3.4 Outbox concerns that apply to every variant
- [ ] At-least-once delivery → duplicates are normal → consumer must be idempotent (§4).
- [ ] Order per aggregate: Kafka partition key = aggregate id; one poller per partition of the outbox.
- [ ] Outbox cleanup: delete job, time-partitioned table, retention.
- [ ] Monitoring: outbox lag (oldest unsent row), stuck rows, failed sends.
- [ ] Event schema and versioning → link `07_Integration/1_Encoding_&_Evolution`.
- [ ] Tracing through the outbox: store trace context in headers → link `08_Operability/2_Observability`.
- [ ] Frameworks: Spring Modulith, Eventuate Tram, Debezium; build-your-own trade-offs.

### 3.5 Alternatives to the outbox
- [ ] **Listen to yourself** (event first): publish to Kafka, then consume your own event to update your DB.
- [ ] **Event sourcing**: the event log IS the source of truth → link `07_Integration/3_Event_Driven_Architecture`.
- [ ] CDC on business tables directly (no outbox) — and why it leaks your internal schema.

## 4. Receiver side

- [ ] **Idempotent consumer:** processed-message table with unique constraint, written in the same transaction
      as the business change.
- [ ] **Inbox pattern:** store first, ack, process later; dedup on insert.
- [ ] Natural idempotency (set status = X) vs needs dedup (add 10 €).
- [ ] Versioned / conditional updates: ignore stale events (version or timestamp check).
- [ ] Idempotent REST APIs: `Idempotency-Key` header, storing the first response, key expiry.

## 5. Synchronous calls without a broker

- [ ] Idempotent API + retry as the basic consistency tool.
- [ ] Unknown outcome after timeout: query status before retrying; status polling.
- [ ] Async request-reply: 202 Accepted + status endpoint / callback (webhook).
- [ ] **Reconciliation jobs:** periodic compare of both sides, repair drift — the safety net behind every
      pattern above (link your webhook reconciliation cards in `3_Event_Driven_Architecture`).

## 6. Multi-step business transactions across services

sagas stay in 08/5_Microservices_and_Sagas

- [ ] **Saga:** sequence of local transactions + compensations; orchestration vs choreography.
- [ ] Compensation is not rollback: semantic undo, can itself fail, some steps can't be undone (pivot step).
- [ ] Lack of isolation in sagas → anomalies (lost updates, dirty reads) and countermeasures: semantic lock,
      commutative updates, reread value, version file.
- [ ] **Reservation / TCC (try-confirm-cancel):** reserve first, confirm or release; reservation timeouts.
- [ ] Decide: existing `5.4.2_Saga.md`, `5.4.3_Orchestration_&_Choreography.md` — move here or link from here.

## 7. Read side across systems

CQRS lag goes to 08/3_Event_Driven_Architecture (replay file)
- [ ] Read models / CQRS projections lag behind the write side; read-your-writes across services.
cache + DB goes to 10_Architecture_Traps
- [ ] Cache + DB as a dual write: invalidate vs update, stale cache → link `09_Architecture_Traps`.

---

## Overlap to check

- `5.2.2_Lost_Before_Reaching_the_Broker.md` probably already has outbox cards — mechanism cards live here,
  event-driven file links here.
- 2PC cards from the old `04_Distributed_Data/3_Distributed_Transactions` move here.

## Not here

- Single-DB transactions, isolation, race conditions → `1_DB_Consistency`.
- Timeouts, retries, circuit breaker, load protection → `3_Resilience`.
- Event design, ordering and replay as event-driven topics → `07_Integration/3_Event_Driven_Architecture`.
