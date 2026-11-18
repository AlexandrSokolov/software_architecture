# Order & Scaling

## Order

### Two updates arrive in wrong order?
<details><summary>Show answer</summary>

The older one wins. "Status = SHIPPED" (v2) arrives first, then "status = PAID" (v1). You apply v1 last, so the order
shows PAID — and stays wrong until the next change, because no later event fixes it.

Without a check, the last update to arrive wins, not the last one that happened. The fix is to
[spot the older update](#is-this-update-older-than-mine).

</details>

### Is this update older than mine?
<details><summary>Show answer</summary>

The event must carry something you can compare:
- **Version per record** — the sender adds 1 on every change. Store the version with your row and apply only newer
  ones: `UPDATE ... SET ..., version = ? WHERE id = ? AND version < ?`.
- **Sequence number** — e.g. the position in the sender's DB change log; same idea.
- **Timestamp** — works only if one clock sets it (the sender's DB). Clocks on different machines disagree, and two
  changes in the same millisecond tie.

Compare a version that only the owner of the record increases; time is the weakest choice.

</details>

### Ordering: global or per entity?
<details><summary>Show answer</summary>

Per entity is almost always enough. Updates to order 42 must stay in sequence; order 42 and order 43 don't care about
each other.

Global order means a single line: one partition, one consumer — no parallel work, and one slow event stops everything.

Per-entity order is what [partitioning by key](#spread-work-keep-per-customer-order) gives: same key → same
partition → one consumer.

Order across two kinds of records ("customer created" before "order placed")? Give both the same key (customer ID), or
make the receiver [handle a missing parent](#delete-arrives-before-create).

Choose the smallest unit whose changes must stay in sequence, and use it as the key.

</details>

### 10 handler threads — what about order?
<details><summary>Show answer</summary>

It's gone. Thread 1 takes v1 of order 42, thread 2 takes v2; thread 2 finishes first, so v1 is applied last. Even
messages from one Kafka partition lose their order once you hand them to a thread pool.

To keep order and still run in parallel:
- **Same key, same thread** — `thread = hash(orderId) % 10`, so one order's messages always go to one thread;
- **More partitions and consumers** instead of threads.

Add a [version check](#is-this-update-older-than-mine) on write as a safety net.

Parallel across keys is fine; within one key there must be a single line.

</details>

### Delete arrives before create?
<details><summary>Show answer</summary>

Don't drop it, and don't fail forever:
- **Tombstone** — store "order 42 deleted, v3". When "created, v1" arrives, the
  [version check](#is-this-update-older-than-mine) sees it is older and ignores it. Clean up tombstones after a while.
- **Park and retry** — put the event aside (retry topic or pending table) and try again later, hoping the create
  arrives. After N tries, [move it aside for good](#messages-that-keep-failing--where-to).
- **Ask the sender** — fetch order 42; it doesn't exist → done.

The tombstone is the usual choice: it records what you learned, needs no waiting, and works in any arrival order.

</details>

### Fetch current state on each event — when?
<details><summary>Show answer</summary>

Worth it when order is hard to guarantee and only the latest state matters:
- events come out of order or get lost, and the sender has no versions — a fetch always returns the newest state, so
  arrival order stops mattering;
- the event is [only a notice](1_Event_Design.md#notice-only-event--cost-for-receivers) anyway.

Cost:
- one API call per event — load on the sender, and the sender must be up;
- you miss steps: after two quick changes you see only the second. Fine for "current status", wrong when each step
  counts (audit, every payment).

You trade API load for not caring about order.

</details>

## Too much load

### Spread work, keep per-customer order?
<details><summary>Show answer</summary>

Partition by customer ID:
- the producer sets key = customer ID; the key's hash picks the partition, so all events of one customer land in one
  partition, in order;
- consumers in one group split the partitions — each partition is read by exactly one consumer at a time;
- add consumers up to the number of partitions; one customer's events still go through one consumer.

Limits: no more parallel consumers than partitions ([extra ones sit idle](#more-consumers-no-speed-up--why)), and a
[heavy customer stays on one consumer](#one-customer-sends-100-times-more).

Parallel across customers, in sequence within one.

</details>

### Many workers, one shared queue — trade-off?
<details><summary>Show answer</summary>

Gain:
- easy scaling — add a worker and it starts taking messages; no partitions to plan;
- even load — any free worker takes the next message; a slow message holds up only one worker.

Loss:
- order — v1 and v2 of one order go to two workers and finish in any order;
- the same record changed by two workers at once — you need locks or version checks.

Good for independent tasks (send email, resize image); wrong when order per record matters — then
[partition by key](#spread-work-keep-per-customer-order).

This is **competing consumers**: any message to any worker — fast and simple, but no order.

</details>

### One customer sends 100 times more?
<details><summary>Show answer</summary>

All its events have one key, so they land on one partition and one consumer — a **hot key**:
- that consumer falls behind while others sit idle;
- other customers on the same partition wait behind the big one;
- more consumers don't help — one key can't be split between them.

Options:
- give the big customer its own partition, topic or consumer;
- if order per customer isn't needed for all its events, use a narrower key — customer ID + order ID keeps order per
  order only;
- make each event cheaper to handle (batch the writes).

Partitioning spreads keys, not load; one heavy key stays on one worker.

</details>

### More consumers, no speed-up — why?
<details><summary>Show answer</summary>

- **More consumers than partitions** — in a Kafka group each partition has one consumer. 10 partitions and 15
  consumers → 5 sit idle.
- **Hot key** — most traffic goes to [one partition](#one-customer-sends-100-times-more).
- **The bottleneck is further on** — all consumers write to the same DB or call the same slow API. More consumers just
  wait there — or make it worse with locks, full connection pools, rate limits.

Find the real bottleneck first; more consumers help only when the work can actually spread.

</details>

### Messages arrive faster than you handle?
<details><summary>Show answer</summary>

The backlog grows. In a log (Kafka) messages wait and consumer lag rises; every message is handled later and later. If
the backlog gets older than the retention, messages are deleted unread.

What you can do:
- **Wait it out** — fine for a peak: the broker is the buffer. On average you must still be faster than the input.
- **Add consumers** — if the [work can spread](#more-consumers-no-speed-up--why).
- **Do less per message** — batch writes, skip work that isn't needed.
- **Push back** — slow down producers or reject new requests at the entry point (**back pressure**).
- **Drop or delay** low-priority messages, keep the critical ones.

A broker buys time, not capacity.

</details>

### Work moves to another consumer — risks?
<details><summary>Show answer</summary>

- **Duplicates** — the new consumer starts from the last saved offset. Messages the old one handled but didn't commit
  are handled again — the handler must be
  [safe to repeat](2_Lost_or_Duplicate.md#message-arrives-twice--do-work-once).
- **Two owners at once** — the old consumer wasn't dead, only paused (GC, slow network). It wakes up and keeps working
  on the partition it lost. Its offset commit is rejected, but its writes and calls already happened.
- **Pause** — the moved partitions aren't read until the new consumer starts; lag jumps.
- **Lost memory state** — caches and half-built batches in the old consumer are gone.

A hand-off always means some messages run twice; build for it.

</details>

## Messages that always fail

### Message fails every time — the rest?
<details><summary>Show answer</summary>

It depends on whether you keep order:
- **Log with order (Kafka partition)** — you can't move past it. Retry it forever and the whole partition stops:
  everything behind it waits, including other customers on that partition.
- **Shared queue** — it comes back again and again, wasting workers; others pass, but it loops forever.

Such a message is a **poison pill**. You must stop retrying at some point and
[move it aside](#messages-that-keep-failing--where-to).

In an ordered stream, one bad message blocks everything behind it.

</details>

### Bad message or brief outage — how to tell?
<details><summary>Show answer</summary>

By what failed:
- **Temporary** (**transient**) — the problem is around the message: timeout, connection refused, DB down, HTTP 503 or
  429, deadlock. Trying later may work.
- **Permanent** — the message itself is wrong: can't parse, wrong schema, failed validation, HTTP 400 or 422. Trying
  again gives the same result.

Sort errors into these two in code. Unknown errors count as temporary, but with a retry limit — so a wrongly sorted
permanent one still ends up [set aside](#messages-that-keep-failing--where-to).

A strong signal: if every message fails at once, it's an outage — don't set them all aside. If one fails and the
others pass, it's the message.

Temporary: something around the message. Permanent: the message itself.

</details>

### Retries — how many, how long between?
<details><summary>Show answer</summary>

- **How many** — a few (often 3–5) for temporary errors; none for permanent ones — set them aside right away.
- **How long between** — exponential backoff (1s, 2s, 4s, 8s…) plus random jitter, so many consumers don't retry at the
  same moment and hit the recovering service together.
- **Total time** — longer than a usual outage of what you call (seconds for a DB failover, minutes for a deploy).
- **Where to wait** — not inside the consumer for long, or the partition stops. For long waits, use retry topics with
  delays (retry-1m, retry-10m) and go on with the next message. Cost: order for that key breaks.

Retry fast a few times, back off with jitter, then move the message aside.

</details>

### Messages that keep failing — where to?
<details><summary>Show answer</summary>

To a **dead letter queue** (DLQ) — a separate queue or topic for messages that ran out of retries. Store with each one:
- the original message, unchanged;
- the error, the number of tries, the time, and where it came from (topic, partition, offset).

Who looks: the team that owns the consumer, alerted when the DLQ is not empty. Someone decides: a bug in our code, bad
data from the sender, or a problem in a dependency.

A DLQ without an alert is just a slower way to lose messages.

</details>

### Bug fixed — rerun the failed messages?
<details><summary>Show answer</summary>

Send them from the DLQ back into the main flow (**redrive**), but check:
- **Safe to repeat** — some may have partly worked or been fixed by hand; the handler must
  [drop repeats](2_Lost_or_Duplicate.md#message-arrives-twice--do-work-once).
- **Newer data** — while they waited, newer events for the same record were handled. An old message can overwrite newer
  data, so [check the version](#is-this-update-older-than-mine).
- **Pace** — replay at a limited rate, not 50,000 at once.
- **A few first** — try a handful, then the rest; new failures go back to the DLQ, not into a loop.

A replayed message is an old, late duplicate — handle it like one.

</details>
