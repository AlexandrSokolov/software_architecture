# 07_Distributed_Data / 3_Trouble_with_Distributed_Systems

**Question it answers:** What can the environment do to ANY distributed mechanism — network, clocks, paused
processes — and what can a node actually know about the others?

**How it differs from Replication and Partitioning:** those folders describe problems of one mechanism (failover,
lag, hot keys). This folder describes the faults underneath all of them. Replication failover, consensus,
Kafka rebalancing and resilience patterns all exist because of these faults — they link here.

**Sources:**
- DDIA 2nd ed. — chapter "The Trouble with Distributed Systems".
- Kleppmann, *How to do distributed locking* (2016) — fencing tokens, the Redlock debate.
- The fallacies of distributed computing (Deutsch) — one card.
- *Kafka: The Definitive Guide* 2nd ed. — consumer group timeouts, producer fencing (practice side).

---

## 1. Partial failure

- [ ] Single machine: works or crashes. Distributed: some parts fail, unpredictably.
- [ ] Fallacies of distributed computing — the network is reliable, latency is zero, …
- [ ] Fault models: crash-stop vs crash-recovery vs Byzantine (and why most systems ignore Byzantine).

## 2. Unreliable networks

- [ ] A request can be lost, delayed, duplicated; the response can be lost too.
- [ ] **You can't tell slow from dead** — the only tool is a timeout.
- [ ] Unbounded delays: queueing in switches, OS, GC; why latency has long tails.
- [ ] Network partitions: some nodes see each other, others don't.
- [ ] Choosing timeouts: too short → false failover, too long → slow recovery. What to DO about it → link
      `06_Resilience`.

## 3. Failure detection

- [ ] Heartbeats and timeouts; adaptive detectors (phi accrual) in one card.
- [ ] False positives: a node declared dead keeps working → two leaders (split brain); link
      `4.1.2_Single_Leader_Node_Outages.md`.

## 4. Unreliable clocks

- [ ] Wall clock vs monotonic clock (`System.currentTimeMillis()` vs `System.nanoTime()`) — which to use for what.
- [ ] Clock drift, NTP jumps backwards, leap seconds.
- [ ] Last-write-wins with timestamps silently loses data.
- [ ] Clock uncertainty intervals (Spanner TrueTime) — awareness only.
- [ ] Rule: never order events across nodes by wall clock → logical clocks in `4_Consistency_and_Consensus`.

## 5. Process pauses

- [ ] Stop-the-world GC, VM suspension, swapping, a blocked thread — the process doesn't notice it was paused.
- [ ] Lease expires during a pause → old leader / lock holder keeps writing.
- [ ] Java angle: GC tuning reduces pauses, but code must still be correct with them.

## 6. Truth is defined by the majority

- [ ] A node can't trust its own view (am I still the leader?); the quorum decides.
- [ ] **Distributed locks and leases** — why a lock alone is unsafe.
- [ ] **Fencing tokens:** monotonically increasing token checked by the storage; the Redlock debate (Kleppmann).
- [ ] Epochs/generations as the general form of fencing — link consensus terms.

## 7. Safety vs liveness

- [ ] Safety: nothing bad happens. Liveness: something good eventually happens. Which one a design can give up
      during faults.

## 8. Kafka as the practice case (block 1)

- [ ] Consumer group: `session.timeout.ms`, heartbeats, `max.poll.interval.ms` — a GC pause or slow processing
      triggers a rebalance (failure detection with false positives).
- [ ] Zombie consumer / producer after a pause → generation IDs and producer epochs fence it.
- [ ] Broker considered dead by the controller → leader election; link Kafka cards.

## 9. Testing awareness

- [ ] Jepsen — how real databases broke under partitions (read a report or two, no cards on internals).
- [ ] Fault injection in your own setup → link `06_Resilience` practice.

---

## Not here

- Patterns that react to these faults (timeouts, retries, circuit breaker) → `06_Resilience`.
- Agreement despite these faults (consensus, linearizability) → `4_Consistency_and_Consensus`.
- Failover and replication lag details → `1_Replication`.
