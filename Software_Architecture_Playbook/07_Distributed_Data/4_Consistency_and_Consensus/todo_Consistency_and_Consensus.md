# 07_Distributed_Data / 4_Consistency_and_Consensus

**Question it answers:** Several replicas hold the same data. What guarantees can readers get, what do they cost,
and how do nodes agree on one value (leader, order, lock) when some of them fail?

**Sources:**
- DDIA 2nd ed. — chapter "Consistency and Consensus".
- Kleppmann, *Please stop calling databases CP or AP* (2015) — what CAP really says.
- Jepsen, *Consistency Models* map (jepsen.io/consistency) — the spectrum in one picture.
- raft.github.io — Raft visualisation (only to see leader election and log replication working).
- *Kafka: The Definitive Guide* 2nd ed. — replication, controller, KRaft (practice side).

Developer view only: what each guarantee gives you and costs; no algorithm internals.

---

## 1. Consistency models (the spectrum)

- [ ] Eventual consistency — what it promises and what it doesn't.
- [ ] Session guarantees: read-your-writes, monotonic reads, consistent prefix — link `1_Replication`
      (`4.1.3_Replication_Lag.md`), don't restate.
- [ ] Causal consistency — happens-before, why it's the strongest model that survives partitions.
- [ ] Linearizability — the strongest single-object model.
- [ ] **ACID "C" vs this "consistency"** — same word, different meaning; link `05_Consistency`.

## 2. Linearizability

- [ ] Definition: the system behaves as if there is one copy and every operation is atomic.
- [ ] Linearizability vs serializability vs strict serializability.
- [ ] Where you need it: leader election and locks, uniqueness constraints (username, seat), cross-channel timing
      (write, then send a message that triggers a read).
- [ ] Which replication gives it: single-leader (only if reads go to the leader), consensus (yes), multi-leader
      (no), leaderless quorums (not necessarily).

## 3. The cost

- [ ] CAP — what it really says (during a partition: consistency or availability), why "CP/AP" labels mislead.
- [ ] PACELC — the latency trade-off when there is no partition.
- [ ] Linearizability is slow even without failures: every read and write waits for coordination.

## 4. Ordering and IDs

- [ ] Causality and happens-before.
- [ ] ID generation: auto-increment per node, UUIDv4 vs UUIDv7, Snowflake-style IDs; which ones are ordered.
- [ ] Logical clocks: Lamport timestamps, hybrid logical clocks; vector clocks (link leaderless replication).
- [ ] Why timestamps from wall clocks can't order events — link `3_Trouble_with_Distributed_Systems`.
- [ ] Total order broadcast / shared log — and why it's equivalent to consensus.

## 5. Consensus

- [ ] The problem: nodes agree on one value, and the decision survives node failures.
- [ ] What it's used for: leader election, shared log, locks, membership, atomic commit.
- [ ] Majority quorum: why 3 or 5 nodes, why odd numbers, how majorities prevent split brain.
- [ ] Terms/epochs: how an old leader is recognised and ignored.
- [ ] Raft, Paxos, Zab — what they do, at the level of "leader + majority + epochs".
- [ ] Limits: FLP in one sentence; consensus is slow and needs a stable majority.

## 6. Coordination services

- [ ] ZooKeeper, etcd: locks, leader election, service discovery, configuration, membership.
- [ ] Fencing tokens from the coordination service — link `3_Trouble_with_Distributed_Systems`.
- [ ] Where you meet them: etcd in Kubernetes, Kafka KRaft (ZooKeeper replaced), consensus per range in
      CockroachDB/Spanner.

## 7. Kafka as the practice case (block 1)

- [ ] Controller quorum (KRaft) = consensus.
- [ ] Data replication via ISR = NOT majority quorum — how Kafka trades it off (`min.insync.replicas`,
      `acks=all`, unclean leader election).
- [ ] Leader epochs — Kafka's version of terms/fencing.
- [ ] Kafka-specific cards live in `08_Integration/6_Message_Brokers/Kafka`; each links to the theory card here.

---

## Not here

- Atomic commit / 2PC → `05_Consistency/2_Distributed_Transactions`.
- Replication methods and lag → `1_Replication`.
- Network, clock and pause faults themselves → `3_Trouble_with_Distributed_Systems`.
