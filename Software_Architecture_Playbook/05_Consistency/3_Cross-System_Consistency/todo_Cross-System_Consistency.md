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
