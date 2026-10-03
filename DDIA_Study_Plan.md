# DDIA — Study Plan (Senior Java Developer / Architect Track)

Book: *Designing Data-Intensive Applications*, 2nd ed. — Kleppmann & Riccomini.
Chapter list: [DDIA_TOC.md](DDIA_TOC.md). Folder layout: [Software_Architecture_Structure.md](Software_Architecture_Structure.md).

## 1. Chapter → folder

| Ch. | Title | Folder |
|---|---|---|
| 1 | Trade-Offs in Data Systems Architecture | `03_Data_Systems/1_Data_System_Architecture/` |
| 2 | Defining Nonfunctional Requirements | `03_Data_Systems/2_Nonfunctional_Requirements/` |
| 3 | Data Models and Query Languages | `03_Data_Systems/3_Data_Modeling/` (top level, DDIA core) |
| 4 | Storage and Retrieval | `03_Data_Systems/4_Storage_and_Retrieval/` |
| 5 | Encoding and Evolution | `03_Data_Systems/5_Encoding_and_Evolution/` |
| 6 | Replication | `04_Distributed_Data/6_Replication/` |
| 7 | Sharding | `04_Distributed_Data/7_Partitioning/` |
| 8 | Transactions | `04_Distributed_Data/8_Transactions/` |
| 9 | The Trouble with Distributed Systems | `04_Distributed_Data/9_Trouble_with_Distributed_Systems/` |
| 10 | Consistency and Consensus | `04_Distributed_Data/10_Consistency_and_Consensus/` |
| 11 | Batch Processing | `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/` |
| 12 | Stream Processing | `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/` |
| 13 | A Philosophy of Streaming Systems | `05_Integration_and_Event_Driven/` — [split by section](#2-sections-that-leave-their-chapters-folder) |
| 14 | Doing the Right Thing | No folder — ethics and privacy are not in the structure |

`01_Code_Design/` and `02_Application_Architecture/` get nothing from DDIA. `06_Architecture_Traps/` is built from
combinations of the folders above, never from one chapter.

## 2. Sections that leave their chapter's folder

The folder follows the topic, not the chapter. These sections are filed where their topic lives.

| Section | From | Goes to |
|---|---|---|
| Microservices and Serverless (21) | Ch. 1 | `05_Integration_and_Event_Driven/4_Microservices_and_Sagas/` |
| Data Systems, Law, and Society (24) | Ch. 1 | No folder |
| Stars and Snowflakes (77) | Ch. 3 | `03_Data_Systems/3_Data_Modeling/3_Analytical_and_Warehousing/` |
| Event Sourcing and CQRS (101) | Ch. 3 | `05_Integration_and_Event_Driven/2_Event_Driven_Architecture/` |
| Durable Execution and Workflows (187) | Ch. 5 | `05_Integration_and_Event_Driven/4_Microservices_and_Sagas/` |
| Event-Driven Architectures (189) | Ch. 5 | `05_Integration_and_Event_Driven/2_Event_Driven_Architecture/` |
| Distributed Transactions Across Different Systems (328) | Ch. 8 | `05_Integration_and_Event_Driven/4_Microservices_and_Sagas/` (next to saga vs 2PC) |
| Exactly-Once Message Processing Revisited (334) | Ch. 8 | `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/` |
| Messaging Systems (489) | Ch. 12 | `05_Integration_and_Event_Driven/1_Enterprise_Integration_Patterns/` (queues, competing consumers) |
| Keeping Systems in Sync (501) | Ch. 12 | `06_Architecture_Traps/` (dual write) |
| Change Data Capture (503) | Ch. 12 | `05_Integration_and_Event_Driven/4_Microservices_and_Sagas/` (the fix for dual write) |
| State, Streams, and Immutability (508) | Ch. 12 | `05_Integration_and_Event_Driven/2_Event_Driven_Architecture/` |
| Data Integration (539) | Ch. 13 | `05_Integration_and_Event_Driven/2_Event_Driven_Architecture/` |
| Unbundling Databases (546) | Ch. 13 | `05_Integration_and_Event_Driven/2_Event_Driven_Architecture/` |
| Aiming for Correctness (561) | Ch. 13 | `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/` |

## 3. Interview order

Tuned for the senior Java developer role first. Architect notes at the end.

| # | Chapter | Read | Why it gets asked |
|---|---|---|---|
| 1 | Ch. 2 Nonfunctional Requirements | Home Timelines case study (34), Latency and Response Time (38), Average, Median, and Percentiles (40), Fault Tolerance (43), Understanding Load (50), Shared-Nothing (51). Skim Maintainability (52). | Every system design answer starts here: p99 vs the mean, load, scaling, fan-out on write vs on read. |
| 2 | Ch. 8 Transactions — first half (277–322) | The Meaning of ACID (279), Read Committed (290), Snapshot Isolation (293), Preventing Lost Updates (299), Write Skew and Phantoms (303), Two-Phase Locking (313) vs Serializable Snapshot Isolation (317). | Isolation levels, lost updates, optimistic vs pessimistic locking — the theory behind `@Transactional` and `@Version`. |
| 3 | Ch. 4 Storage and Retrieval — OLTP half (116–134) | Log-Structured Storage (118), B-Trees (125), Comparing B-Trees and LSM-Trees (129), Multicolumn and Secondary Indexes (132), Storing Values Within the Index (133). Skip the analytics half (134+) unless the role is analytics. | "Why is this query slow", how an index works, B-tree vs LSM-tree, composite and covering indexes. |
| | **Milestone 1 — Java database round** | | You can handle database-depth questions: indexes, isolation levels, locking. |
| 4 | Ch. 3 Data Models | Object-Relational Mismatch (68), Normalization, Denormalization, and Joins (72), Many-to-One and Many-to-Many (75), When to Use Which Model (80). Event Sourcing and CQRS (101) if the role is event-driven. Graph query languages (88–98): awareness only. | SQL vs NoSQL, embedding vs joins, when joins stop working. |
| 5 | Ch. 6 Replication | Synchronous Versus Asynchronous Replication (200), Problems with Replication Lag (209), Solutions for Replication Lag (214), Dealing with Conflicting Writes (222). Skim Leaderless (229): quorum `w + r > n` and why it is not enough. | Read replicas, lag bugs, read-your-writes, monotonic reads. |
| 6 | Ch. 7 Sharding | Sharding by Key Range (256) vs by Hash of Key (258), Skewed Workloads and Relieving Hot Spots (263), Local (268) vs Global Secondary Indexes (270). Skim Multitenancy (254), Request Routing (265). | Hash vs range, hot keys, reads that must ask every shard (scatter/gather). |
| | **Milestone 2 — System design round** | | You can scale reads and writes, and pick and defend a data store. |
| 7 | Ch. 5 Encoding and Evolution | Protocol Buffers (169), Avro (172), The Merits of Schemas (177), Dataflow Through Databases (178), REST and RPC (180), Event-Driven Architectures (189). Skim Language-Specific Formats (164). | Backward and forward compatibility while old and new code run side by side, API versioning, changing an event's schema without breaking consumers. |
| 8 | Ch. 12 Stream Processing | Log-Based Message Brokers (495), Change Data Capture (503), Reasoning About Time (518), Fault Tolerance (526). | Kafka ordering per key, consumer groups, CDC, exactly-once. |
| 9 | Ch. 9 — three sections only | Timeouts and Unbounded Delays (352), Process Pauses (366), Distributed Locks and Leases (373). | A GC pause outlasts a lock's lease; the service wakes up still thinking it holds the lock. Fencing tokens fix it. |
| 10 | Ch. 8 Distributed Transactions (323–335) | Two-Phase Commit (324), Distributed Transactions Across Different Systems (328), Exactly-Once Message Processing Revisited (334). Needs Ch. 6 and 7 first. | 2PC, what happens when the coordinator fails, XA limits — the ground for saga vs 2PC. |
| | **Milestone 3 — Microservices and messaging** | | You can design multi-service systems with event streams and explain how they survive network and JVM faults. Covers most integration-flavored architect questions. |
| 11 | Ch. 1 Trade-Offs | Characterizing Transaction Processing and Analytics (5), Systems of Record and Derived Data (10), Cloud Versus Self-Hosting (12), Microservices and Serverless (21). | Monolith vs microservices, cloud vs self-hosting, operational vs analytical systems. |
| 12 | Rest of Ch. 9, then Ch. 10 | Ch. 9: Fault Detection (351), Unreliable Clocks (358–365), The Majority Rules (372); skim Byzantine Faults (377), Formal Methods (384). Ch. 10: headline only; for CAP, The Cost of Linearizability (413) — its 1st-ed. location, not checked for the 2nd ed. | Ch. 10 in three lines: linearizability = the system acts like one copy; consensus = Raft/ZooKeeper-style agreement; it costs latency and availability when the network splits. |
| 13 | Ch. 11, 13, 14 | Summary sections only. | Rarely asked for this goal. |

**Architect override:** if an architect interview gets scheduled early, move Ch. 1 to #2, right after Ch. 2. It gives
the vocabulary for high-level trade-offs before the low-level mechanics.
