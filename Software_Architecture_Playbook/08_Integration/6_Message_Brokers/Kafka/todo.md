
- The app stops while sent messages still sit in the client's memory buffer. What is lost, and how do you prevent it?


### Q: Which position you save

- Auto-commit is on. When does it lose messages, and when does it not?
- Ten threads handle one partition. Message 7 failed, 8–10 are done. Which position do you save?
- You poll 500 messages, 3 fail, you commit the batch. What happened to the 3?
- The partition is taken away from you mid-processing (rebalance). What can be lost?

- The handler takes longer than the consumer's poll time limit. What happens, and what runs twice?
- The handler takes longer than the queue's lock time on the message. What happens?


### Kafka auto-commit — when does it lose messages?
<details><summary>Show answer</summary>

`enable.auto.commit=true` is the plain client's default. The commit runs inside `poll()`: every
`auto.commit.interval.ms` (5 s), a call to `poll()` commits the offsets of everything returned by earlier polls.

- **No loss** — you process each batch fully on the polling thread before calling `poll()` again. Every committed
  record is already done. A crash costs only duplicates (up to 5 s of work).
- **Loss** — you hand records to other threads and call `poll()` at once. The next poll commits records still waiting
  in those threads; a crash loses them.

Spring Kafka turns auto-commit off by default and commits itself after the listener returns (`AckMode.BATCH`).

Handle: auto-commit is safe only if `poll()` comes after the work is done.

</details>

### Ten threads, one partition — which offset to save?
<details><summary>Show answer</summary>

Save 7. A committed offset is one number per partition, and it means "everything below this is done; start here":

- **Save 7** — after a restart 7, 8, 9, 10 are read again: 8–10 are duplicates.
- **Save 11** — 7 is never read again: lost.

Rule: commit the first offset that is not done yet. Each worker marks its offset done; a tracker moves the commit
forward only across an unbroken run of finished offsets.

Ready-made: Confluent Parallel Consumer tracks every message and commits up to the first gap; Spring Kafka manual
acks with `asyncAcks=true` hold the commit until the gap closes.

Cost: one stuck message holds the commit back for the whole partition. It must end in
[a retry or a dead-letter topic](#retries-used-up--where-does-the-message-go), never wait forever.

</details>

### Batch of 500, 3 failed — commit it?
<details><summary>Show answer</summary>

Not as is. Committing the batch moves the offset past all 500, so the 3 failed ones are never read again — lost, with
no error.

Two correct ways:

- **Commit up to the first failure** — commit everything before it, read again from it. Spring Kafka: throw
  `BatchListenerFailedException` with the failed record's index; the container commits the records before it and
  retries from it. Cost: records after the failure are processed again.
- **Move the failures aside** — send the 3 to a dead-letter topic, wait until the send is confirmed, then commit the
  batch.

Handle: an offset can't jump over a hole — stop at it, or fill it with a dead-letter copy.

</details>

### Partition taken away mid-work — what's lost?
<details><summary>Show answer</summary>

A rebalance moves the partition to another consumer, which starts at the last committed offset.

- **Loss** — an offset was committed for work still running in the background. Your consumer then drops that work or
  dies, and the new owner starts after it.
- **Duplicate** (more common) — you finished the work, but the commit fails (`CommitFailedException`) because the
  partition is no longer yours. The new owner does it again.

A frequent trigger: handling one `poll()` batch takes longer than `max.poll.interval.ms` (5 min), so the consumer is
thrown out of the group while still working.

Fixes:

- in `ConsumerRebalanceListener.onPartitionsRevoked`, finish or stop the work in progress and commit only what is
  done;
- keep a batch well under `max.poll.interval.ms` (fewer records per poll);
- cooperative rebalancing (`CooperativeStickyAssignor`, or the new consumer protocol in Kafka 4.0) takes away only the
  partitions that really move.

</details>


### Q: When the error is swallowed

- The handler catches an exception, logs it, and confirms the message. What did you just do?
- A message failed N times and is thrown away. Where must it go instead, and who looks at it?
- Spring Kafka: the retries for a record are used up. What happens to it by default?
- The message can't even be parsed. What happens to it, and to the ones behind it?
- Sending to the dead-letter topic fails. Is the message gone?


## When the error is swallowed

### Catch, log, confirm — what did you do?
<details><summary>Show answer</summary>

Dropped the message on purpose, leaving only a log line. The listener returned normally, so the container confirmed
it: no retry, no dead-letter copy. Logs rotate, and nobody searches them for lost orders.

Fix: let the exception reach the framework's error handler, which retries and then
[moves the message aside](#retries-used-up--where-does-the-message-go). Catch only to add context and rethrow, or for
a case you really handle (e.g. "already exists" on an idempotent insert).

Handle: a swallowed exception is a confirm.

</details>

### Retries used up — where does the message go?
<details><summary>Show answer</summary>

To a **dead-letter queue or topic** (DLQ, DLT) — never into a log line or nowhere. Keep what is needed to fix it: the
original payload (raw bytes), the error and stack trace, the source topic, partition and offset, the time.

Around it:

- **Owner** — the team of the consuming service; alert as soon as the DLQ is not empty.
- **Replay** — after the fix, send the messages back to the main topic or queue; the handler must accept duplicates.
- **Retention** — a Kafka DLT deletes by time like any topic; keep messages longer than anyone takes to look.
- **Before giving up** — tell the two kinds of failure apart: a DB that was down for a second needs retries with a
  growing wait; a bad payload will never work and should go to the DLQ at once.

RabbitMQ has it built in: a dead-letter exchange gets messages that were rejected without requeue, or expired. Kafka
has no broker-side DLQ — the client or framework writes to another topic.

</details>

### Spring Kafka retries used up — default?
<details><summary>Show answer</summary>

The record is logged and skipped — lost. `DefaultErrorHandler` (default since Spring Kafka 2.8) tries 10 times in
total with no wait (`FixedBackOff(0, 9)`), then calls its recoverer. The default recoverer only logs the record at
ERROR, and the offset is committed.

To keep it: give the handler a `DeadLetterPublishingRecoverer`. It sends the record to `<topic>.DLT` (same partition
number), with the error in headers.

Some exceptions skip the retries and go to the recoverer at once, since a retry can't fix them: e.g.
`DeserializationException`, `MessageConversionException`, `ClassCastException`.

These retries block the partition while they run. For long waits, non-blocking retries (`@RetryableTopic`) move the
record through retry topics and then to a `-dlt` topic.

</details>

### Message can't be parsed — what happens?
<details><summary>Show answer</summary>

In Kafka the bytes are turned into objects inside `poll()`, before your code runs. A bad record makes `poll()` throw
(`RecordDeserializationException`, which carries its partition and offset):

- **Plain client** — your loop must catch it and `seek` past the record. Otherwise every `poll()` hits it again and
  the partition is stuck: the messages behind it never arrive.
- **Spring Kafka** — wrap the real deserializer in `ErrorHandlingDeserializer`. It catches the failure and passes the
  record on with the error attached; the error handler sends it to the recoverer without retries.

Loss is decided by the recoverer: log-and-skip loses it, a DLT keeps it. Store the raw bytes — there is no parsed form.

Handle: a retry can't fix bad bytes; move them aside and keep the partition moving.

</details>

### Dead-letter send fails — is the message gone?
<details><summary>Show answer</summary>

Only if you commit the original anyway. The DLT send is a normal produce and can fail: broker down, record too large,
no permission on the topic.

Rule: commit the original offset only after the DLT send is confirmed (`acks=all` on that producer). If the send
fails, don't commit; the record is read again and the error path runs again.

Spring Kafka: `DeadLetterPublishingRecoverer` waits for the send result; if publishing fails, the error handler
doesn't commit, and the record is delivered again. A fire-and-forget DLT send in your own code loses it.

Two more traps:

- the DLT is usually in the same cluster, so when Kafka is down both fail — the consumer stalls, which beats losing;
- a DLT with short retention [loses the messages later](#retries-used-up--where-does-the-message-go).

</details>

### App stops with messages still buffered?
<details><summary>Show answer</summary>

**Graceful vs. Hard Shutdown**
Async `send()` only puts the message into the client's memory buffer and returns.
A background thread sends the buffer in batches shortly after.
Stop the app before it does, and those messages are gone, even though `send()` returned without an error.

**The Fixes:**
- **Graceful Shutdown (SIGTERM):** call `producer.close(timeout)` (or `flush()`) on shutdown.
  This blocks exit until the buffer is fully sent and confirmed by the broker.
  Give it a time limit: if the broker is down, waiting forever blocks the shutdown, and giving up loses the rest.
- **Hard stop** (`kill -9`, crash): Shutdown hooks won't run; the memory buffer is destroyed.
  Only a [Transactional Outbox](#crash-between-db-commit-and-broker-publish) (saving events to the DB until ACKed)
  survives this.

Trade-off: a bigger buffer and a longer batching wait give more throughput, but more messages sit in memory at risk.

</details>