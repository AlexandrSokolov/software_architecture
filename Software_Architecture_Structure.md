# Software Architecture Playbook — Structure

**Spine:** zoom out one level per folder — class → one service → one data system → that data system on many
machines → many systems → traps that cross levels.

```text
Software_Architecture_Playbook/
├── 01_Code_Design/
│   ├── 1_Principles/                        (SOLID, Coupling/Cohesion, Composition vs Inheritance)
│   └── 2_Patterns/                          (Creational, Structural, Behavioral)
├── 02_Application_Architecture/             (Layers, Hexagonal, Modules, Data Access/N+1)
├── 03_Data_Systems/                         (DDIA Part I - Folder per Chapter)
│   ├── 1_Data_System_Architecture/
│   ├── 2_Nonfunctional_Requirements/
│   ├── 3_Data_Modeling/
│   │   ├── 1_Relational_and_Trees/          (Karwin, Celko)
│   │   ├── 2_Extreme_Scale_NoSQL/           (DeBrie)
│   │   └── 3_Analytical_and_Warehousing/    (Kimball)
│   ├── 4_Storage_and_Retrieval/             (B-Trees, LSM Trees)
│   └── 5_Encoding_and_Evolution/            (Protobuf, Avro, Schema Evolution)
├── 04_Distributed_Data/                     (DDIA Part II - Folder per Chapter)
│   ├── 6_Replication/
│   ├── 7_Partitioning/                      (Sharding)
│   ├── 8_Transactions/
│   ├── 9_Trouble_with_Distributed_Systems/  (Clocks, Fencing Tokens)
│   └── 10_Consistency_and_Consensus/        (Linearizability, Raft/Paxos)
├── 05_Integration_and_Event_Driven/
│   ├── 1_Enterprise_Integration_Patterns/   (RabbitMQ, ActiveMQ, traditional Queues)
│   ├── 2_Event_Driven_Architecture/         (Event Sourcing, CQRS, Stopford/Bellemare)
│   ├── 3_Batch_and_Stream_Processing/       (DDIA Part III, Kafka partitioned & Share Groups)
│   ├── 4_Microservices_and_Sagas/           (Outbox, Saga, API Composition)
│   └── 5_Resilience_and_Observability/      (Release It!: Circuit Breakers, Timeouts, Metrics)
└── 06_Architecture_Traps/                   (FLAT DIRECTORY: The Dual-Write Problem, Poison Pills, Stampedes)
```

## What each folder covers

### 01_Code_Design

Design inside one codebase, at class and object level.

- **1_Principles** — SOLID, coupling and cohesion, composition vs inheritance. Principles come before patterns:
  every pattern serves one of them. The Java deck already has inheritance and composition cards (4.3/4.4) — link
  to them, do not restate.
- **2_Patterns** — the GoF patterns, filed by the book's three groups. Per pattern: the problem it solves and the
  principle it serves.

### 02_Application_Architecture

The structure of one service as a whole.

- Layers and which way dependencies point; hexagonal (ports and adapters); module boundaries inside one service.
- Consistency units inside the domain model: DDD aggregates (Evans).
- Data access: N+1 queries, offset vs keyset pagination, streaming large results, batch sizes.

### 03_Data_Systems

One data system — DDIA Part I. Folder number = chapter number.

- **1_Data_System_Architecture** — trade-offs, operational vs analytical systems, cloud vs self-hosting,
  distributed vs single-node.
- **2_Nonfunctional_Requirements** — performance (latency, response time, throughput, percentiles), reliability,
  scalability, maintainability.
- **3_Data_Modeling** — relational vs document vs graph models and their query languages (DDIA core), then the
  pattern families from the second-tier books:
  - **1_Relational_and_Trees** — trees in SQL (adjacency list, materialized path, nested sets, closure table),
    EAV, polymorphic associations.
  - **2_Extreme_Scale_NoSQL** — embedded documents, single table design, overloaded partition/sort keys
    (also NoSQL Distilled).
  - **3_Analytical_and_Warehousing** — star and snowflake schemas, slowly changing dimensions (SCD type 2),
    bitemporal modeling (Snodgrass).
- **4_Storage_and_Retrieval** — how the database stores data on disk and finds it again: append-only logs,
  LSM trees vs B-trees, indexes, column storage for analytics.
- **5_Encoding_and_Evolution** — JSON, Protobuf, Avro; backward and forward compatibility; old and new code running
  side by side during a rolling deploy; API and message schema changes; expand/contract database migrations.

### 04_Distributed_Data

One data system spread over many machines — DDIA Part II. The system itself can give you the guarantee
(transactions, consensus).

- **6_Replication** — leader/follower, multi-leader, leaderless; replication lag and what the user sees
  (read-your-own-writes, monotonic reads).
- **7_Partitioning** — splitting data by key (hash vs range), hot keys, rebalancing, secondary indexes.
- **8_Transactions** — ACID, isolation levels, lost updates (version column, row locks), write skew.
- **9_Trouble_with_Distributed_Systems** — lost and slow network messages, unreliable clocks, process pauses,
  distributed locks and fencing tokens.
- **10_Consistency_and_Consensus** — linearizability, ordering guarantees, CAP, consensus (Raft, Paxos).

### 05_Integration_and_Event_Driven

Many systems with separate owners. No single system can give the guarantee, so you build it between systems.

- **1_Enterprise_Integration_Patterns** — the named messaging patterns (Hohpe & Woolf): point-to-point vs
  publish-subscribe channels, competing consumers, router, splitter, aggregator, scatter-gather, dead letter
  channel, idempotent receiver, message translator. The patterns do not depend on one broker; queue brokers
  (RabbitMQ, ActiveMQ) are where competing consumers lives.
- **2_Event_Driven_Architecture** — why events instead of direct calls (Stopford); event design, event sourcing,
  CQRS (Bellemare).
- **3_Batch_and_Stream_Processing** — DDIA Part III: batch jobs, stream processing; Kafka consumer groups (one
  consumer per partition, order per key) and share groups (queue-style consumption — newer feature, check its
  release status before writing cards); exactly-once and idempotency; event ordering, late events.
- **4_Microservices_and_Sagas** — outbox and change data capture (the fixes for dual write), sagas (orchestration
  vs choreography) vs 2PC, API composition, sync vs async calls between services, service boundaries (DDD bounded
  contexts). Richardson, Newman.
- **5_Resilience_and_Observability** — Release It! stability patterns (timeouts, circuit breaker, bulkheads,
  back pressure, shed load, fail fast); graceful shutdown and draining, readiness vs liveness; logs, metrics,
  traces, correlation IDs; alerting on user-facing targets (SLOs); metric cardinality.

### 06_Architecture_Traps

Flat. Design challenges that cross several folders ("design X", "why does Y break"). Each answer links to the
mechanism cards in 01–05 and never restates them. Seed list: the issue lists in `todo.md` — dual write (DB write
plus broker send, one fails), poison pills, cache stampedes, cache vs DB staleness, the 3am incident.

## Placement rules

- **Folder = topic, book = source.** A second book on a topic adds cards to that topic's folder, never a new
  book folder.
- **One mechanism → 01–05. Several mechanisms combined into a design → 06.**
- **Can one system give the guarantee?** Yes → 04. No, you build it between systems → 05.
- **Competing consumers ≠ partitioned consumption.** Shared queue, any consumer takes any message, no order
  (05/1) vs one consumer per partition, order per key (05/3). Separate cards, never one.
