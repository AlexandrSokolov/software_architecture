# Software Design Learning Path — Coordination & Distribution

**Core thesis:** the "split tasks → handle → monitor" problem has moved off the single machine and into
the broker. Priority is the distributed/event-driven track; in-process concurrency is good-to-know
background, not the goal.

**Entry point:** DDIA 2e + Stopford — the *problem* (why distribution is hard) plus the *paradigm shift*
(why events replace explicit calls), before any pattern catalog.

---

## Group 1 — Foundations: Distributed Data & Messaging  *(priority · 2 books)*

The bedrock: the problems of distribution, and the vocabulary for messaging. Read first.

### 1. Designing Data-Intensive Applications, 2nd ed. — Kleppmann & Riccomini
The *why*. Partitioning, replication, consistency, delivery semantics, the trade-offs — the most
rigorous, vendor-neutral problem-model you can get. Get the 2026 2nd edition, not the 2017 first.
* Covered: append-only logs, sharding, replication, event sourcing, NoSQL-vs-relational trade-offs.

#### DDIA 2nd Edition — Full ToC + Folder Mapping

---

##### Part I — Data Systems Foundations (Ch. 1–5)

| Ch | Title                                   | Target Folder (under `Data_Systems/`)   |
|----|-----------------------------------------|-----------------------------------------|
| 1  | Trade-offs in Data Systems Architecture | `1_Data_System_Architecture`            |
| 2  | Defining Nonfunctional Requirements     | `1_Data_System_Architecture`            |
| 3  | Data Models and Query Languages         | `2_Data_Modeling`                       |
| 4  | Storage and Retrieval                   | `3_Storage_and_Retrieval`               |
| 5  | Encoding and Evolution                  | `4_Data_Encoding_and_Serialization`     |

---

##### Part II — Distributed Data (Ch. 6–10)

| Ch | Title                                | Target Folder                                          |
|----|--------------------------------------|--------------------------------------------------------|
| 6  | Replication                          | `Enterprise_Integration_Patterns_EIP/Distributed_Data` |
| 7  | Partitioning (Sharding)              | `Enterprise_Integration_Patterns_EIP/Distributed_Data` |
| 8  | Transactions                         | `Enterprise_Integration_Patterns_EIP/Distributed_Data` |
| 9  | The Trouble with Distributed Systems | `Enterprise_Integration_Patterns_EIP/Distributed_Data` |
| 10 | Consistency and Consensus            | `Enterprise_Integration_Patterns_EIP/Distributed_Data` |

---

##### Part III — Derived Data (Ch. 11–14)

| Ch | Title                           | Target Folder                                                   |
|----|---------------------------------|-----------------------------------------------------------------|
| 11 | Batch Processing                | `Enterprise_Integration_Patterns_EIP/Event_Driven_Architecture` |
| 12 | Stream Processing               | `Enterprise_Integration_Patterns_EIP/Event_Driven_Architecture` |
| 13 | Philosophy of Streaming Systems | `Enterprise_Integration_Patterns_EIP/Event_Driven_Architecture` |
| 14 | Doing the Right Thing           | `Enterprise_Integration_Patterns_EIP/Event_Driven_Architecture` |

---

### Recommended Folder Structure

### 2. Enterprise Integration Patterns — Hohpe & Woolf
The *vocabulary*. The named patterns for split / distribute / collect, and the bible under every
integration tool (Camel, Spring Integration, MuleSoft, Workato).
* Covered: Pub/Sub, Content-Based Router, Splitter, Aggregator, Scatter-Gather, Competing Consumers,
  Dead Letter Channel, Message Translator.

---

## Group 2 — Event-Driven Architecture  *(priority · 3 books)*

The paradigm: events handled by the platform's semantics, not by explicit thread code.

### 1. Designing Event-Driven Systems — Ben Stopford
Short, motivation-first, free from Confluent. The cleanest "why events instead of explicit calls"
argument from first principles — exactly the "events aren't handled explicitly anymore" intuition.
Read early; it's the conceptual bridge.
* Covered: event sourcing, CQRS, "event streams as a source of truth."

### 2. Building Event-Driven Microservices, 2nd ed. — Bellemare
The deeper, practical version of Stopford's thesis: event/stream design, schemas and evolution,
service patterns. Get the 2nd edition.

### 3. Kafka: The Definitive Guide
The mechanism level — how the broker actually distributes work: consumer groups, partition
assignment, delivery guarantees.

> **Precision check to carry through Groups 1–2:** for every distribution mechanism, ask —
> *competing consumers* (shared queue, pull) or *partitioned consumption* (Kafka, split by key with
> ordering)? Different guarantees; conflating them is the most common architect-level mistake.

---

## Group 3 — Microservices: Distributed Service Design  *(priority, but postponed · 2 books)*

> **Why postpone until after Groups 1 & 2:** these books are the *application layer* — they *compose*
> the foundations the earlier groups establish. Richardson's Saga / Outbox / CQRS are solutions to
> problems DDIA proves are hard and that event-driven systems address; Newman's orchestration-vs-
> choreography and sync-vs-async decisions only make sense once you already hold the messaging (EIP)
> and event-driven (Stopford/Bellemare) models. Read microservices first and you collect patterns
> without the problem-model that makes them inevitable — expensive for a deep-reading style.

### 1. Microservices Patterns: With Examples in Java — Chris Richardson
How to keep data consistent when the pipes cross service boundaries — transactions without a central DB.
* Covered: Saga (Process Manager), Transactional Outbox, API Composition, CQRS integration.

### 2. Building Microservices — Sam Newman
The holistic blueprint: the "glue" between services, synchronous RPC vs asynchronous event-based
integration.
* Covered: event-driven architecture, orchestration vs choreography, API gateways (scatter-gather hubs).

### Companion to Group 3 — Release It! Design and Deploy Production-Ready Software — Michael Nygard
(2nd ed., 2018), from the O'Reilly ToC
The *runtime-resilience* lens: how a running distributed system survives — or fails to survive — real
production load. **Not only about how to release and deploy** (that's only Parts III–IV); the core
(Parts I–II) is the resilience of a live system under stress — the failure modes one slow or dead
service inflicts on the rest, and the patterns that contain them. The origin of the Circuit Breaker
pattern. Widely regarded as one of the top must-read books for architects.
* Covered: stability antipatterns (integration points, cascading failures, blocked threads, slow
  responses, dogpile); stability patterns (**Circuit Breaker, Bulkheads, Timeouts, Shed Load,
  Back Pressure, Fail Fast, Governor**); interconnect (load balancing, demand control, discovery).
* **Why here:** it's the resilience counterpart to Newman — Newman is *how services talk*, Release It!
  is *how to stop one service's failure from taking down the rest*. Those failure modes only become
  real once services communicate across a network, so it assumes the messaging/event models of Groups
  1–2. It also fills a gap DDIA leaves: DDIA Ch. 8–9 *name* the distributed-failure problems; Release
  It! gives the *patterns* that answer them. Not a light read like Burns — a core book at the
  application-resilience altitude.

### Companion to Group 3 — Designing Distributed Systems — Brendan Burns  *(optional, light read)*
The *deployment/composition* lens: reusable container patterns for arranging distributed systems on
Kubernetes — the physical counterpart to Richardson's and Newman's logical service design. Short and
readable, not a deep-learning commitment.
* Covered: single-node patterns (sidecar, ambassador, adapter); multi-node patterns (replicated-
  load-balanced, scatter-gather, **work-queue**).
* **Why here, not in the priority path:** it's framed around containers/orchestration, not messaging
  or data guarantees — it assumes the layer Groups 1–2 teach. Its scatter-gather and work-queue
  chapters touch your task-distribution interest, but at the *infrastructure* altitude, so read it as
  a practical bridge into composing/deploying microservices, after Groups 1–2.


---

## Group 4 — Single-Machine Concurrency  *(optional / wish · 2 books)*

> **Why last and optional:** modern systems push "split/handle/monitor" out of the JVM and into the
> broker (Groups 1–2), so this is background, not priority. Two caveats if you do enter here:
> JCIP predates Fork/Join (it's the Java 5 world — Executors, BlockingQueue, the memory model);
> The Art of Multiprocessor Programming is a graduate algorithms text (rigorous, but solutions already
> exist — you won't be implementing them). And the *modern* in-process answer to your exact
> "split/handle/monitor" question isn't a book at all — it's **virtual threads + structured concurrency**
> (Java 21+ → JEP 453/505, `StructuredTaskScope`, official docs). Read those JEPs instead of an old book.

### 1. The Art of Multiprocessor Programming, 2nd ed. — Herlihy & Shavit
Work-stealing balancing, concurrent stacks/queues/deques, derived from first principles. The rigorous
answer to "work-stealing algorithms" — academic, detailed.

### 2. Java Concurrency in Practice — Goetz
Still the best book on the threads-and-locks mental model and the Java Memory Model. Dated: no
Fork/Join, no CompletableFuture, no reactive.

---

## Summary

| Order | Group                                     | Priority        | Books |
|-------|-------------------------------------------|-----------------|-------|
| 1     | Foundations: Distributed Data & Messaging | Highest         | 2     |
| 2     | Event-Driven Architecture                 | Highest         | 3     |
| 3     | Microservices (after 1 & 2)               | High, postponed | 2     |
| 4     | Single-Machine Concurrency                | Optional / wish | 2     |
