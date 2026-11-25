# 08_Integration / 3_Event_Driven_Architecture — pitfalls from practice: keys, order and parallelism

**Question it answers:** Who decides the order in which events of one entity are processed, and what silently
breaks it — keys, partitions, concurrency, hot keys?

**Source:** `developer-pitfalls.md` (draft v4) §6.10, §6.13, §7.2, §7.5–7.6, interview questions 4–6, 26–29;
login-tracking assessment (`TrackingResultPublisher` keyed by `customerId`, listener concurrency from the
environment).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Message key = ordering

- [ ] **Key by the business entity** — all events of one customer go to one partition, in order.
      Did: result key = `customerId`; test asserts the key.
- [ ] **A `null` key spreads events** over partitions (sticky partitioner) — no per-entity order.
- [ ] **The producer client chooses the partition** (`hash(key) % partitions`), not the broker.
- [ ] **Order holds only within a partition** — even one consumer reading several partitions gets their records
      interleaved; a key is needed whenever per-entity order matters, not only with several consumers.

## 2. The input key is the upstream producer's contract

- [ ] **Pitfall:** assuming input events are keyed because our tests send them with a key — the real producer
      decides; without a key per-customer order is lost **before** our service and can't be restored.
- [ ] **Do:** state it in the topic contract ("keyed by `customerId`"); our own output topic we key ourselves.
      Did: listed in Known limitations.
- [ ] **Manual test events have no key** — `kafka-console-producer` sends `null`; use `parse.key=true` and a
      separator that isn't in JSON (`|`, not `:`).

## 3. Partition count

- [ ] **Adding partitions breaks key → partition** — `hash(key) % N` changes, new events of a customer land
      elsewhere; order across the change is lost. Do: choose the count with headroom up front, treat a change
      as a migration.
- [ ] **Topics created by accident** get the broker's default partition count →
      `6_Message_Brokers/Kafka/todo_Pitfalls_Kafka.md` (explicit topic creation).

## 4. Parallelism

- [ ] **Partitions = unit of parallelism** — max useful consumers in a group = partitions; replication gives
      reliability, not partitions.
- [ ] **Listener `concurrency` above the partition count = idle threads** — useful threads across **all**
      instances ≤ partitions. Did: concurrency from the environment (a deployment decision).
- [ ] **Concurrency is bounded by downstream capacity too** — `concurrency × instances` concurrent calls on the
      downstream (link pools → `06_Resilience/todo_Pitfalls_HTTP_Calls.md` §5).

## 5. Hot partition

- [ ] **One key produces most traffic** (a bot, a huge customer) — one partition overloaded, one consumer lags,
      others idle; more instances don't help.
- [ ] **Do:** monitor lag **per partition**; rate-limit at the source; a finer key only if per-entity order can
      be given up.

## 6. Interview questions to answer out loud

- [ ] Who chooses the partition, what changes with a key?
- [ ] One customer generates most of the traffic — what happens?
- [ ] One consumer reads all partitions — is the order guaranteed?
- [ ] Who sets the key on the input and on the output topic; what if the input comes without a key?
- [ ] What happens to key-based order when partitions are added?
- [ ] How do you choose listener concurrency, and why no bulkhead here?

---

## Covered elsewhere

- Existing theory: `5.2.6_Order.md`, `5.2.7_Scaling.md`, `5.2.x_Order_&_Scaling_Questions.md` in this folder.
- Consumer group mechanics and rebalances → `6_Message_Brokers/Kafka/todo_Pitfalls_Kafka.md`.
