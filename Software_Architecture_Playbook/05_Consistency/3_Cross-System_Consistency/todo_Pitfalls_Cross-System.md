# 05_Consistency / 3_Cross-System_Consistency — pitfalls from practice: consume → call → publish

**Question it answers:** A service reads an event from Kafka, calls a REST API and publishes a result. Every step
can fail or repeat. Where can an event be lost, duplicated or end with an accidental outcome — and what decides
it (offsets, error handler, DLT, idempotency)?

**Source:** `developer-pitfalls.md` (draft v4) §4.4, §6.1–6.6c, §6.16–6.24, §6.29, §6.31–6.32, §7.1;
login-tracking assessment (login-processing-service: `LoginEventListener`, `KafkaListenerConfig`,
`TrackingResultPublisher`, `LoginTrackingService`, `LoginEventListenerIT`).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Delivery guarantees in the flow

- [ ] **Delivery is at-least-once** — rebalance, retry, redeploy repeat events; every handler idempotent.
      Did: storage idempotent by `messageId` (→ `1_DB_Consistency/todo_Pitfalls_Idempotent_Insert.md`).
- [ ] **Retries require idempotency on the receiver** — a timeout after the server already stored the data →
      the retry stores it twice unless the receiver deduplicates by a business id.
- [ ] **Idempotency covers only idempotent side effects** — the DB row is (PK), the published result is not:
      after a crash between publish and commit a second result follows, in **any** ack mode. Result consumers
      deduplicate by `messageId`.
- [ ] **"Effectively once"** = at-least-once + idempotent consumer. Kafka transactions give exactly-once only
      Kafka → Kafka (→ `2_Distributed_Transactions/todo_Distributed_Transactions.md` §6).

## 2. Exception handling decides the offset

- [ ] **Handler returns → offset committed; handler throws → the error handler decides** (retry, skip, DLT).
- [ ] **Business outcomes are returned, not thrown** — `unsuccessful` after REST retries is a processed result in
      its own topic; only unexpected errors propagate.
- [ ] **A commit is a position, not a list** — the committed value is the next record to read, per group +
      partition, in `__consumer_offsets`; one consumer with several partitions commits a position for each.
- [ ] **Failure in the middle of a batch — two cases:**
  - [ ] process dies after 5 of 10 → nothing committed (`AckMode.BATCH`) → all 10 again, 5 duplicates;
  - [ ] handler throws on record 3, process lives → position after 1–2 committed, 3 → DLT, rest re-polled;
        if the DLT publish fails too, 3 comes again later.
- [ ] **Ack mode is a choice** — `BATCH` (fewer commits, up to `max-poll-records` repeats) vs `RECORD` (at most one
      repeat, a commit per record) vs `MANUAL`. Zero duplicates is impossible anyway.
- [ ] **Offsets in your own database** — offset in the same transaction as the result, `seek` on assignment;
      atomic, but lag metrics / Kafka UI / offset tools lose the real position; external REST still outside.
      Default + idempotency is usually the better trade.

## 3. Publishing the result

- [ ] **Fire-and-forget loses messages** — `send()` is async; the input offset is committed even if the publish
      fails. Did: `send(...).join()` inside the listener flow, a failure propagates.
- [ ] **The producer already retries** — a failed `join()` means retries for up to `delivery.timeout.ms`
      (2 min default) already happened; no extra app-level retry on publish.
- [ ] **A comment must match the error handler** — "join() throws → offset not committed → reprocessed" is false
      with a DLT recoverer: the failed publish goes to the DLT and the offset **is** committed; only if the DLT
      publish fails too is it redelivered.
- [ ] Producer timeouts as part of the time budget → `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md`.

## 4. Failure between steps

- [ ] **Crash between the REST call and the publish** — offset not committed → redelivered → insert hits the PK →
      200 → `successful` published. This is what idempotency by business id buys.
- [ ] **Dependency down on redelivery** — the row exists, but the result is `unsuccessful`.
- [ ] **Unknown outcome after a read timeout** — the server may have stored the event; the task defines success as
      HTTP 200, so the result is `unsuccessful`. Link: `todo_Cross-System_Consistency.md` §1 (unknown outcome).

## 5. Poison pills and the DLT

- [ ] **Default behaviour loses or blocks messages** — without `ErrorHandlingDeserializer` a malformed value fails
      inside `poll()` forever; without your own handler: 10 attempts, then **log and skip**.
      Did: `ErrorHandlingDeserializer` + `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.
- [ ] **Don't re-queue into the same topic** (JMS habit) — breaks per-key order, creates loops; retry in place,
      then DLT, or pause the consumer.
- [ ] **What goes to the DLT (design decision)** — "could not process": deserialization errors, bugs, failed
      publish. Not business outcomes (an outage would flood it, DLT alerts become useless).
- [ ] **No retries before the DLT for state errors** — bug, bad data, wrong config don't fix themselves;
      retries only for unreliable calls, at their own layer. Did: `FixedBackOff(0, 0)`.
- [ ] **Why DLT over "log and skip"** — keeps the payload + error headers, explicit monitored problem, replayable.
- [ ] **Two kinds of values in the DLT** — failed deserialization carries the original `byte[]`, failed handler
      carries the object; JSON template would base64 the bytes. Did: `DelegatingByTypeSerializer`.
- [ ] **Serializer configured twice** — setters + config properties → `IllegalStateException`.
- [ ] **DLT partitioning** — same partition number by default; partition `-1` lets the producer choose by key.
- [ ] **Reading the DLT headers** — the main exception header is usually the wrapper
      `ListenerExecutionFailedException`; the reason is in `kafka_dlt-exception-cause-fqcn`.
- [ ] **The DLT lives on the same Kafka** — broker down → DLT publish fails → record not skipped, re-polled later:
      no loss, no progress.
- [ ] **Operating the DLT** — alert on growth, define replay (back to the source topic after a fix), topic name from
      config. DLT as personal data storage → `11_Security`.
- [ ] **Testing the DLT** — unique key per test, read filtered by key; prove the partition is not blocked: broken
      + valid message with the **same key**.

## 6. Accidental outcomes: no validation on the consumer side

- [ ] **Valid JSON with invalid fields** is sent downstream anyway (400 → `unsuccessful`); a missing `customerId`
      reaches the DLT through an NPE on the record key; a missing `messageId` publishes a result with nulls.
      The outcome is accidental, not designed.
- [ ] **Do:** validate in the listener with the contract's rules, invalid events to the DLT explicitly
      (→ `02_Application_Architecture/todo_Pitfalls_Application.md` §3).

---

## Covered elsewhere

- Outbox, inbox, idempotent consumer as patterns → `todo_Cross-System_Consistency.md` §3–§4.
- Kafka offsets/retention mechanics → `08_Integration/6_Message_Brokers/Kafka/todo_Pitfalls_Kafka.md`.
- Open circuit breaker and "unsuccessful" vs pausing the listener →
  `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md`.

## Not here

- Retry classification, timeouts, breaker config → `06_Resilience/todo_Pitfalls_HTTP_Calls.md`.
- Keys and per-entity order → `08_Integration/3_Event_Driven_Architecture/todo_Pitfalls_Keys_&_Order.md`.
