# 08_Integration / 6_Message_Brokers / Kafka — pitfalls from practice: Kafka and Spring Kafka specifics

**Question it answers:** What is specific to Kafka (a log, not a queue) and to Spring for Apache Kafka that bites a
developer — retention, offsets, defaults, topic creation, container settings?

**Source:** `developer-pitfalls.md` (draft v4) §6.7–6.9, §6.11, §6.14–6.15, §6.26–6.27, §7.3–7.4, §7.7,
interview questions §6; login-tracking assessment (`settings/kafka.yaml`, `KafkaListenerConfig`,
docker-compose Kafka in KRaft mode).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.
Theory each card links to: `07_Distributed_Data`, `05_Consistency/3_Cross-System_Consistency`.

---

## 1. Kafka is a log, not a queue

- [ ] **Reading deletes nothing** — offsets decide what is "done"; a consumer offline catches up from its own
      offset, within retention (vs JMS queue / webhooks: pushed or lost).
- [ ] **Different groups are independent** — both get everything; the same group splits partitions.

## 2. Retention: two clocks

- [ ] **Consumer slower than retention = data loss** — `log.retention.*` (default 7 days, size unlimited,
      **per partition**) deletes whole segments, read or not. Alert on lag per partition long before retention.
- [ ] **Group offsets expire too** — a group without members for `offsets.retention.minutes` (7 days) loses its
      offsets → `auto-offset-reset` applies: `earliest` = mass reprocessing, `latest` = skipped messages.
      Keep `offsets.retention` ≥ `log.retention`; a long downtime is an incident with a deliberate offset reset.
- [ ] **`auto-offset-reset` applies only without a committed offset** — `latest` (default) skips messages sent
      before the first start. Did: `earliest`.

## 3. Defaults you don't see

- [ ] **Client defaults live in `kafka-clients` code** — not in any file, not in the broker config; read the
      `ConsumerConfig values:` / `ProducerConfig values:` block in the startup log.
- [ ] **`spring.kafka.consumer` vs `spring.kafka.listener`** — the Kafka client itself (records per poll, protocol
      timeouts, deserializers) vs Spring's container around it (threads, ack mode, stop behaviour).
- [ ] **Producer is lazy** — `KafkaTemplate` creates the producer at the first `send()`; no broker connection
      until then.

## 4. Topics are created explicitly

- [ ] **`auto.create.topics.enable=true` (broker default)** — a topic appears on the first produce/fetch with the
      broker defaults: partitions, replication factor 1, 7 days retention (also for personal data in a DLT),
      no ACLs; a typo creates a new topic. Locally the DLT appears only with its first record.
- [ ] **Production:** `auto.create.topics.enable=false`; every topic up front as code (Terraform, Strimzi
      `KafkaTopic`, a script; `NewTopic` beans for dev only) with partitions, RF 3, `min.insync.replicas` 2,
      retention, ACLs — the DLT too.
- [ ] Replication, ISR, under-replicated partitions — **to be studied** →
      `07_Distributed_Data/4_Consistency_and_Consensus/todo_Consistency_and_Consensus.md` §7.

## 5. Spring Kafka container settings

- [ ] **Default stop processes the whole poll** — `immediate-stop: true` finishes only the current record.
- [ ] **Listener shutdown timeout has no Boot property** — `ContainerProperties.shutdownTimeout` (10 s default)
      via a `ContainerCustomizer` bean. Chain → `09_Operability/4_Infrastructure/todo_Pitfalls_Infrastructure.md`.
- [ ] **Listener in tests connects to a local broker** — `spring.kafka.listener.auto-startup: false` in test
      config → `02_Application_Architecture/todo_Pitfalls_Application.md` §6.
- [ ] **Error handler bean is plugged in automatically** — any `CommonErrorHandler` bean. DLT details →
      `05_Consistency/3_Cross-System_Consistency/todo_Pitfalls_Cross-System.md` §5.

## 6. Local setup

- [ ] **Kafka in Docker needs two listeners** — internal (`kafka:29092`) for containers, external
      (`localhost:9092`) for the host, both advertised → `09/4`.
- [ ] **Single broker** — internal topics can't have more than one replica (`*_REPLICATION_FACTOR: 1`).
- [ ] **Kafka UI hides partitions of an empty group** — only partitions with committed offsets are shown; not a bug.

## 7. Interview questions to answer out loud (from the Pitfalls list)

- [ ] Kafka vs JMS queue / webhooks: what happens while a consumer is offline?
- [ ] What is an offset, who owns it, where is it stored?
- [ ] Two services read the same topic — interfere? Two instances of one service?
- [ ] Why partitions?
- [ ] How long are messages kept?
- [ ] The consumer is slower than the producer — step by step.
- [ ] `offsets.retention` vs `log.retention`; the service was down for two weeks — what happens on restart?
- [ ] A poll returns 10 records, crash after 5 — what's processed again? And if the handler throws on 3?
- [ ] What is `max.poll.interval.ms`, how does it relate to retries and timeouts?
- [ ] When does a rebalance happen and what does it cost?
- [ ] At-least-once vs exactly-once — how do you get "effectively once"?
- [ ] One consumer owns three partitions: what does one poll return, what does a commit store?
- [ ] Could you store offsets yourself? Why would you, and why usually not?
- [ ] `spring.kafka.consumer` vs `spring.kafka.listener`?
- [ ] Replication, ISR, under-replicated partitions — **to be studied**.

---

## Covered elsewhere

- Poll budget and timeouts → `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md`.
- Keys, order, hot partitions → `3_Event_Driven_Architecture/todo_Pitfalls_Keys_&_Order.md`.
- Existing cards: `5.7.1_Kafka_Replication_and_Followers.md`, `5.7.2_Kafka_Leader_Failover.md`,
  `Kafka_Broker_Message_Loss_Questions.md`.
