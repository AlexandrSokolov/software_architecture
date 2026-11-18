# 05_Consistency / 2_Distributed_Transactions

**Question it answers:** One logical change touches several participants that ALL support transactions (several
databases, DB + XA broker, several shards or partitions). How do they all commit or all abort?

**Sources:**
- DDIA 2nd ed. — distributed transactions section of the transactions chapter (atomic commit, 2PC, XA,
  exactly-once).
- Dave Syer, *Distributed transactions in Spring, with and without XA* (JavaWorld, 2009) — XA, best-efforts 1PC,
  the Java/Spring view.
- *Kafka: The Definitive Guide* 2nd ed. — exactly-once semantics / transactions chapter.
- Richardson, *Microservices Patterns* ch. 4 — why microservices avoid 2PC (bridge to sagas).

---

## 1. The problem: atomic commit on several nodes

- [ ] Why single-node atomicity doesn't extend: each node can commit or fail on its own.
- [ ] What can go wrong: one participant commits, another crashes or times out → partial commit.

## 2. Two-phase commit (2PC)

- [ ] Phases: prepare (vote) → commit/abort; the coordinator's decision is the point of no return.
- [ ] **In-doubt transactions:** participant voted "yes" and lost the coordinator → must wait, holding locks.
- [ ] Coordinator as a single point of failure; its recovery log.
- [ ] Heuristic decisions (manual commit/abort by an operator) and why they break atomicity.
- [ ] 3PC — why it exists on paper and not in practice (one card).

## 3. XA in Java

- [ ] XA / JTA: transaction manager + resource managers; Narayana, Atomikos; Spring `JtaTransactionManager`.
- [ ] Typical case: DB + JMS broker (ActiveMQ, IBM MQ) in one XA transaction. Kafka and most REST APIs can't join.
- [ ] Costs: extra round trips, locks held across the network, recovery after crash, driver/DB quirks
      (Postgres `max_prepared_transactions`).
- [ ] When XA is still a reasonable choice: few participants, one organisation, one data centre, legacy stack.

## 4. Without XA: best-efforts 1PC

- [ ] Commit resources one after another in a fixed order; what can still go wrong (Syer).
- [ ] Spring `ChainedTransactionManager` — what it did and why it's deprecated.
- [ ] Bridge: when best-efforts isn't enough → outbox (`3_Cross-System_Consistency`).

## 5. Transactions inside distributed databases

- [ ] Sharded / NewSQL databases (Spanner, CockroachDB, YugabyteDB): cross-shard transactions exist, but cost
      latency; 2PC over consensus groups instead of single nodes (developer view only).
- [ ] Isolation across shards: distributed snapshot isolation, serializable — link `1_DB_Consistency`.
- [ ] Design rule: keep transactions inside one shard/partition (choose keys and aggregate boundaries for that);
      link `07_Distributed_Data/2_Partitioning`.

## 6. Exactly-once via transactions (Kafka)

- [ ] Kafka transactions: atomic write to several partitions + consumer offsets (consume-transform-produce).
- [ ] `transactional.id`, producer epochs, zombie fencing; consumers with `read_committed`.
- [ ] Limit: exactly-once only inside Kafka; your DB is outside → `3_Cross-System_Consistency`.
- [ ] Kafka-specific cards live in `08_Integration/6_Message_Brokers/Kafka`; theory card here links to them.

## 7. Choosing

- [ ] 2PC/XA vs saga vs outbox: consistency guarantee, availability, latency, coupling — one comparison card.
- [ ] Why microservices usually reject 2PC (Richardson) → sagas in `08_Integration/5_Microservices_and_Sagas`.

---

## Not here

- Single-DB transactions and isolation → `1_DB_Consistency`.
- Participants without transactions (broker, REST): outbox, idempotency, sagas → `3_Cross-System_Consistency`.
- Consensus (the theory behind fault-tolerant commit) → `07_Distributed_Data/4_Consistency_and_Consensus`.
