# Event Consistency & Replay

## Reading events again

### New service needs last month's events?
<details><summary>Show answer</summary>

It depends on the broker:
- **Queue** (RabbitMQ, SQS) — no. A message is deleted once confirmed; a new consumer sees only new messages.
- **Log** (Kafka) — yes, if they are still within retention (Kafka's default is 7 days). The new consumer group starts
  from the earliest offset and reads everything kept.
- **Compacted topic** — keeps the latest event per key. The new service gets the current state of each record, not the
  full history.
- **Your own store** (events in a DB or object storage) — yes, if you keep them.

Otherwise: load the current state from the sender's API or a DB export, then switch to live events.

You can replay only what someone kept.

</details>

### Confirmed message — gone or readable again?
<details><summary>Show answer</summary>

The broker type decides:
- **Queue** — confirm means delete. The message existed to be done once.
- **Log** — confirm means the consumer moves its own read position (offset). The message stays until retention removes
  it; other groups read it on their own, and this group can move back and read it again.

In a log, reading doesn't use up the message — that is what makes replay and many independent consumers possible.

A queue tracks messages; a log tracks where each reader is.

</details>

### Bug in last week's handling — rerun?
<details><summary>Show answer</summary>

With a log, yes, if the events are within retention: stop the consumer group and move its offset back
(`kafka-consumer-groups --reset-offsets --to-datetime`), or read that range with a separate group.

Check first:
- **Safe to repeat** — every event is handled again, so writes must be "set to", not
  ["add"](2_Lost_or_Duplicate.md#make-add-10-safe-to-repeat).
- **Outside calls** — emails or charges would go out again for a whole week; turn them off or guard them during replay.
- **Newer data** — replay can overwrite changes made since; check versions or rebuild the affected data from scratch.
- **Your own output** — events you publish will go out again to your receivers.

Without a log, only if you stored the events yourself.

Starting a replay is easy; the handler decides whether it is safe.

</details>

### Read position saved early or late?
<details><summary>Show answer</summary>

The consumer saves its read position (offset) per partition — in Kafka as the group's committed offset, or in your own
DB.
- **Too early** (before the work is done) → crash → restart after messages that never got handled → lost
  (at-most-once).
- **Too late** (long after the work) → crash → restart from an old position → messages handled again (at-least-once).
  The later the save, the more repeats.

Best: save after the work. Or save the offset in the same DB transaction as the results — then results and position
always match: exactly-once effect for that DB.

The same choice as [confirming before](2_Lost_or_Duplicate.md#crash-after-confirming-before-the-work) or
[after the work](2_Lost_or_Duplicate.md#crash-after-the-work-before-confirming).

</details>

## Events as the record of truth

### Store every change, not current state?
<details><summary>Show answer</summary>

- **Full history** — what happened, when, in what order. Audit comes for free: "who raised the limit?"
- **Any past state** — replay events up to a date: "what did the account look like on 1 March?"
- **New views later** — build a new read model from all past events, even for questions nobody asked when the events
  were written.
- **Debugging** — replay the exact events that led to a bug.

This is **event sourcing**: the events are the data; the current state is computed from them.

You keep how you got here, not only where you are.

</details>

### Only changes stored — current balance fast?
<details><summary>Show answer</summary>

Don't replay all events for every request:
- **Snapshot** — every N events save the state ("balance 110 at event 500"). To load: the last snapshot plus the events
  after it.
- **Projection** — a consumer applies each event to a normal table (`accounts.balance`) as it arrives. Queries read the
  table.

A projection is updated a moment after the event is stored, so
[the user may see old data](#saved-but-read-side-not-updated).

Events are the truth; a stored state built from them is what you read.

</details>

### Wrong event from a year ago?
<details><summary>Show answer</summary>

Don't edit it. Append a new event that corrects it, like a correction entry in accounting:
- "payment 77: €100" was wrong → append "payment 77 corrected: −€100", then the right payment if needed;
- projections apply the correction like any other event; the history shows both the mistake and the fix.

You fix history by adding to it, never by changing it.

</details>

### Writes and reads need different shapes?
<details><summary>Show answer</summary>

Split them:
- **Write side** — a model built to check rules and accept changes. It emits events.
- **Read side** — one or more models shaped for each query (flat table, search index, cache), built from those events.

Each side scales and changes on its own; a new screen gets a new read model with no change to the write side.

Cost: two models to keep, and the read side [lags behind](#saved-but-read-side-not-updated). Worth it when reads and
writes really differ; for plain CRUD it's work for nothing.

This is **CQRS** (Command Query Responsibility Segregation) — separate models for changing and for reading data.

</details>

### Saved, but read side not updated?
<details><summary>Show answer</summary>

The user sees old data. They save, the page reloads from the read model, which hasn't got the event yet — their change
seems lost, and they click again.

Fixes:
- **Show what was sent** — the UI shows the saved data from the write response, not from a new read.
- **Wait for your version** — the write returns the new version; the read waits until the read model has at least that
  version, or reads from the write side for that user for a short time.
- **Say so** — show "processing…" until the change arrives.

The delay is by design (**eventual consistency**); hide it from the user who caused it.

</details>

### Get every DB change without app code?
<details><summary>Show answer</summary>

Read the database's own change log — **change data capture** (CDC):
- every DB writes each change to a log before applying it (Postgres WAL, MySQL binlog);
- a CDC tool (e.g. Debezium) reads that log and publishes each committed insert, update and delete as an event.

Gives: nothing missed, even changes made by scripts or other apps; only committed changes; order per row kept; no
[dual write](2_Lost_or_Duplicate.md#db-write-plus-event--one-fails).

Cost: events mirror tables ("row updated"), not business actions ("OrderShipped"), so your table layout becomes a
public contract. A common mix: write business events to an outbox table and let CDC publish them.

The DB already records every change; CDC only publishes that record.

</details>

### History never changes — delete user data?
<details><summary>Show answer</summary>

- **Keep personal data out of events** — store it in a normal table you can delete from; events carry only the user ID.
- **Crypto-shredding** — encrypt each user's personal fields with that user's own key. To delete, destroy the key; the
  data in old events can no longer be read. Whether this counts as deletion under GDPR is a question for legal.
- **Rewrite** — copy the events without that user's data and swap the store. Last resort.

Don't forget projections, caches, backups, and copies in other services.

Plan for deletion when you design the events; afterwards you can only work around it.

</details>
