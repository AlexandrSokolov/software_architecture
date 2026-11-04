# Event Design

## Event Design Questions

### Why events at all

- Why send an event instead of calling the other service directly?
- What is the difference between asking another service to do something and telling it that something happened?
- Service A calls B, B calls C, C calls D, and D is slow. What happens to A? What changes if they send events instead?
- When is a direct call still better than an event?
- The sender no longer knows who reacts to its event. What does it gain, and what does it lose?
- One business process is spread over five services that react to each other's events. How do you see where a
  single order is stuck?

### Designing the event

- Should the event carry all the data, or only say "something changed"?
- If the event only says "something changed", what must every receiver do next, and what does that cost?
- What must every event contain so that a receiver can handle it safely?
- One event per changed field, or one event per business action? What goes wrong with each?
- Who owns the event's format: the sender or the receivers?
- You need to add, rename, or remove a field. How do you avoid breaking receivers that still run old code?
- Two teams want different data in the same event. One event or two?

## Why events at all

### Direct calls between services — problem and fix?
<details><summary>Show answer</summary>

With a direct call, the sender and the receiver are **tightly coupled**. 
The alternative is to send an **event** through a broker. 
With the broker in the middle, the two are **decoupled**, which gives three gains:
- **Performance (No blocking)** — with a direct call, the sender waits until the receiver answers. 
  With an event, the sender writes it to the broker and moves on. One slow receiver no longer
  [slows every caller up the chain](#one-slow-service-down-the-chain--what-happens-and-how-do-events-fix-it).
- **Reliability** — a direct call fails when the receiver is down. With an event, the broker keeps it until the
  receiver is back.
- **Extensibility** — with direct calls, the sender's code lists every receiver and calls each one, so a new receiver
  means changing the sender. With an event, the sender writes to a topic without knowing who listens. A new receiver
  subscribes to the topic, with [no change in the sender](#sender-doesnt-know-receivers--gain-loss).

</details>

### Messages between services — kinds?
<details><summary>Show answer</summary>

Asynchronous messages fall into two distinct conceptual categories:
- **Command (Intent)** — "do this", e.g. "charge this card". A request to perform an action.
  It is directed at a specific, known receiver who is expected to execute it. That receiver may refuse or fail. 
  The sender wants to know which.
- **Event (Fact)** — "this happened", e.g. "order placed". Goes to any number of receivers the sender doesn't know. 
  It already happened, so nobody can refuse it — each receiver only decides what to do about it.

Command: the sender decides what happens next. Event: the receivers decide.

</details>

### One slow service down the chain — what happens and how do events fix it?
<details><summary>Show answer</summary>

**The Problem (Cascading Failure):** In a direct call chain (A -> B -> C -> D), if D is slow, C blocks waiting for D.
C holds threads and connections open. As requests pile up, C's resource pool is exhausted, making C slow. This
cascades upstream to B, then A. A's response time becomes the sum of all four, often triggering timeouts even if A is
healthy.

**The Solution (Isolation):** With events, A publishes a message to a broker and immediately moves on. If D is slow,
messages simply accumulate in D's queue. Services A, B, and C keep working without being blocked.

**Handle:** Calls spread slowness upstream to every caller; events isolate slowness inside the slow service's queue.

</details>

### Fixing a slow chain with events — the cost?
<details><summary>Show answer</summary>

Events [isolate a slow service](#one-slow-service-down-the-chain--what-happens-and-how-do-events-fix-it) from its
callers up the chain. Two costs:
- **Delayed Completion (Eventual Consistency)** — Faster caller, slower work — speed moves in opposite directions: 
  - The caller gets faster: it hands the work to the broker and moves on.
  - The task it handed off gets slower: it waits in a queue at every step, so it finishes later — 
    much later when queues back up.
- **No Synchronous Result** — the first service replies before the last one has done its part, so it can't
  return the final result. The client must ask for it later (polling) or get a push notification (e.g. a WebSocket).

**Handle:** the caller gets faster, the work finishes later, and the client gets no result right away.

</details>

### Direct call over event — when?
<details><summary>Show answer</summary>

- **Need the result now** — the caller can't go on without the reply: data the user waits for on screen, a check the
  next step depends on (payment approved → ship), or a fast "no" on error (card declined). An event gives the caller
  [no result back](#fixing-a-slow-chain-with-events--the-cost).
- **One known receiver, simple flow** — a command for one service, not a fact for many. If that receiver is fast and
  rarely down, a broker only adds overhead: more infrastructure, later results, harder debugging.

**Handle:** call when you need the answer to go on, or when one fast, known receiver isn't worth a broker.

</details>

### Sender doesn't know receivers — gain, loss?
<details><summary>Show answer</summary>

**Gain:**
- **Add receivers freely** — a new receiver subscribes without any change in the sender.
- **Release on their own** — each team changes and deploys its receiver alone; a receiver's change can't break the
  sender.

**Loss:**
- **Implicit Flow** — no single place shows what happens after the event; the flow is spread over the receivers.
- **No feedback (Fire-and-Forget Blindness)** — the sender can't tell whether anyone handled the event, 
  or whether it failed.
- **Risky Schema Changes** — nobody has a full list of who depends on the event, so changing or removing it can break
  a receiver nobody knew about.

**Handle:** the sender is free of its receivers, but blind to them.

</details>

### Order stuck across services — how to find?
<details><summary>Show answer</summary>

- **Follow the trail:**
  - **Correlation ID** — the first service creates an ID (often the order ID). Every service copies it into each event
    it sends and into every log line. Search the central logs (e.g. ELK) by it: the last service that logged it shows
    where the order stopped.
  - **Tracing** — pass the trace context in event headers (OpenTelemetry). A tracing tool (Jaeger, Zipkin) shows each
    step, how long it took, and where the chain broke.
  - **Dead letter queue (DLQ)** — an event that failed too many times (e.g. bad data) is moved aside to a DLQ. Often
    the first place to look for a stuck order.
- **Keep the state in one place:**
  - **Status per order** — one service (often the order service) listens to every step's event and records the
    order's status (placed → paid → packed). An order stuck in one status too long raises an alert.
  - **Orchestrated saga** — when services only react to each other's events (**choreography**), nobody knows the whole
    state. An orchestrator drives the steps, tracks each order's state, and acts when a step takes too long.

**Handle:** follow the order's trail (logs, traces, DLQ), or keep its state in one place.

</details>

### Tracing across events — how does it work?
<details><summary>Show answer</summary>

- **Start** — the first service gets a request and creates a **trace ID**: one ID for the request's whole journey.
  Each step is recorded as a **span**: its own span ID, start and end time, and whether it failed. Publishing an
  event is one span, handling it is another.
- **Send** — services don't call each other, so nothing carries the ID except the event itself. The tracing library
  writes the trace ID and the current span ID into the event's headers (a W3C `traceparent` header); the body doesn't
  change. In Java, the OpenTelemetry agent does this for Kafka, JMS and RabbitMQ clients with no code changes.
- **Receive** — the consumer reads that header and starts its own span under the same trace ID, pointing back to the
  sender's span. It does the same when it sends the next event, so the ID travels down the whole chain.
- **Collect** — every service sends its spans to a tracing backend (Jaeger, Zipkin). The backend groups spans by trace
  ID, follows those pointers, and draws one timeline for the order.

The timeline shows each step and how long it took, the gap where the event waited in a queue, and where the chain broke: 
a span with an error, or the last span after which nothing came.

**Handle:** one trace ID per request, one span per step; the ID travels in the event headers; the backend joins spans by ID.

</details>

### Correlation ID vs trace ID vs span ID?
<details><summary>Show answer</summary>

- **Correlation ID (Business Root) — yours:** A business-driven ID (like an Order ID or API gateway UUID) used to tie log
  messages together. You search this in Kibana/Splunk to find all plain-text logs for a specific business action.
- **Trace ID (Technical Root):** Generated by a tracing tool (OpenTelemetry) at the workflow's start. 
  It stays identical across all services, grouping the entire execution tree into one visual trace.
- **Span ID (Step ID) & Parent Span ID:** A unique Span ID is generated for every individual action (e.g.,
  publishing or consuming). The Parent Span ID points to the step that triggered it, building the tree shape.

**Example: Fan-out (1 Sender -> 3 Subscribers)**
Service A publishes "OrderPlaced". Services B (Billing), C (Stock), and D (Email) consume it in parallel.

**Event Headers injected by Service A into the broker:**
```text
X-Correlation-ID: order-42
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

In `traceparent` (W3C standard): 
- `00` - version 
- `4bf92f3577b34da6a3ce929d0e0e4736` - trace ID (32 hex), 
- `00f067aa0ba902b7` - the sender's span ID (16 hex)
- `01` - trace status - '01' - this trace is recorded. 

**How Subscribers Handle It:**
When B, C, and D consume the event, they read the headers. They keep the Trace ID, set the Parent Span to `spanA123`,
and generate their own Span IDs. The tracing backend sees three spans with the same parent and draws parallel branches:
- **Service B:** Trace: `4bf92f3577b34da6a3ce929d0e0e4736`, Parent: `00f067aa0ba902b7`, Own Span: `spanB456`
- **Service C:** Trace: `4bf92f3577b34da6a3ce929d0e0e4736`, Parent: `00f067aa0ba902b7`, Own Span: `spanC789`
- **Service D:** Trace: `4bf92f3577b34da6a3ce929d0e0e4736`, Parent: `00f067aa0ba902b7`, Own Span: `spanD012`

When payment publishes "payment done", its `traceparent` has the same trace ID and span B's ID, 
so the tree grows under B. All log lines in all four services carry `order-42`.

**Handle:** Correlation ID finds text logs; Trace ID groups the whole tree; Span ID/Parent Span builds the branches.

</details>

### Tracing collector down — what happens?
<details><summary>Show answer</summary>

Spans are made in memory, and a background thread sends them to the collector in batches, so handling an event never
waits on the network. If the collector is down, sending fails, the in-memory queue fills up, and new spans are dropped. 
Nothing is blocked: you lose tracing for that time, not business work.

</details>

### todo
cards on `Keep the state in one place` in the above card

## Designing the event

### Event: full data or only a notice?
<details><summary>Show answer</summary>

- **Full data** (**event-carried state transfer**) — "order 42 placed: items, amount, address".
  - **Gain:** receivers work on their own, with no calls back; the sender may even be down.
  - **Cost:** bigger events; every receiver depends on the sender's data shape; copies of the data spread.
- **Only a notice** (**event notification**) — "order 42 changed".
  - **Gain:** small; its shape rarely changes; no copies of the data spread.
  - **Cost:** every receiver must [fetch the data itself](#notice-only-event--cost-for-receivers).

**Handle:** each option's gain is the other's cost. 
- Pick full data when receivers must work without the sender; 
- pick a notice when:
  - the data is large, 
  - sensitive, or 
  - only its latest version matters.

</details>

### Notice-only event — cost for receivers?
<details><summary>Show answer</summary>

Every receiver calls the sender's API to get the data. That costs:
- **Load** — N receivers make N calls per event; a burst of events becomes a burst of calls on the sender.
- **Coupling** — the sender must be up and fast when receivers call; the dependency events were meant to remove is
  back.
- **Lost steps** — the call returns the current state, not the state at the event's time. If two changes come fast,
  the fetch for the first event already returns the second state, and the first is never seen. A problem when every
  step matters (audit, history).

**When the load hurts:** count calls, not receivers — calls = events × receivers. 3 receivers on 1,000 events/s make
3,000 calls/s on the sender; 20 receivers on 10 events a day make no load at all.


**Handle:** the event stays small, but the cost moves to the sender's API.

</details>

### Fields every event must carry?
<details><summary>Show answer</summary>

- **Which event is this?**
  - **Event ID** — unique per event, to [drop duplicates](2_Lost_or_Duplicate.md#message-arrives-twice--do-work-once).
  - **Source** — the service that sent it, so you know where a bad event came from; event IDs are unique per source.
  - **Type and schema version** (`OrderPlaced`, v2) — to pick the right handler and read both old and new formats.
- **Which entity, and which version of it?**
  - **Entity ID** (order ID) — to keep order per entity and pick the partition.
  - **Version per entity** — goes up with each change of the entity (v3, v4…), to
    [spot an older update](3_Order_&_Scaling.md#is-this-update-older-than-mine) that arrives late.
- **When, and in which flow?**
  - **Event time** — when it happened, not when it was sent; for audit and time-based logic.
  - **Correlation ID** — to [follow one order across services](#order-stuck-across-services--how-to-find).

**Handle:** each field answers a question the receiver would otherwise have to guess.

</details>

### Event scope — which approaches?
<details><summary>Show answer</summary>

- **Per field** (**CRUD events**) — `AddressChanged`, `PhoneChanged`:
  - **Meaning lost** — receivers see column changes and must guess what the business did: `AddressChanged` doesn't
    say if the customer moved or a typo was fixed.
  - **Action split** — one action becomes several events; they can arrive apart or out of order, and a receiver may
    act on half.
  - **Tables leak** — receivers depend on your table layout; rename a column and they break.
- **Per action** (**domain events**) — `CustomerMoved`, `OrderCancelled`:
  - **Gain** — one event carries the meaning and all changes that belong together, so receivers get all or nothing.
  - **Risk: too wide** — one `OrderUpdated` for everything brings the guessing back: receivers compare old and new
    data to see what happened.

CRUD events still fit when receivers only copy the data and need no meaning: a replica, a warehouse fed by CDC.

**Handle:** name events after what the business did, not after which column changed.

</details>

### Who owns the event format?
<details><summary>Show answer</summary>

The sender. It owns the data and its meaning, and it publishes one format for all receivers. If receivers shaped the
event, the sender would change for every new receiver — the coupling events were meant to remove.

In return, the sender owes receivers a stable contract:
- **Published** — the schema lives where receivers can read it (schema registry or shared contract repo).
- **Compatible** — new versions [don't break old receivers](#change-event-fields-without-breaking-receivers).
- **Breaking changes** — announced early; the sender publishes old and new versions side by side until receivers move.

A receiver that needs another shape translates on its own side (an **anti-corruption layer**).

**Handle:** the sender owns the format and owes a stable contract; receivers adapt on their side.

</details>

### Change event fields without breaking receivers?
<details><summary>Show answer</summary>

- **Safe in one step:**
  - **Add** — make the field optional. Old receivers ignore fields they don't know, if their reader allows it: a plain
    `new ObjectMapper()` fails on them, Spring Boot's ignores them (**tolerant reader**). New receivers handle the
    field missing in old events.
- **Safe in several steps:**
  - **Remove** — first make it optional, wait until no receiver reads it, then drop it.
  - **Rename** — never in place. Add the new field, send both, move receivers over, remove the old one.
- **Breaking:**
  - **Change type or meaning** — publish a new version (`OrderPlacedV2`) next to the old one until all receivers have
    moved.

A schema registry with a compatibility check can block a breaking change before it is published.

**Handle:** every change becomes steps that old and new code can both survive.

</details>

### Two teams want different event data?
<details><summary>Show answer</summary>

Ask which business fact the data belongs to:
- **The same fact** — one event carries all data of that fact (the delivery address of a placed order); each team
  reads what it needs and ignores the rest (a [tolerant reader](#change-event-fields-without-breaking-receivers)).
- **A different fact** — billing reacts to "order paid", shipping to "ready to ship". Two events.
- **No fact the sender publishes** — don't add it to the event. The team fetches the data from its owner or builds its
  own view (**enrichment**).

**Anti-pattern:** one event per team ("OrderPlacedForBilling"). The sender ends up serving each team, and the coupling
is back.

**Handle:** events follow business facts, not what each team wants.

</details>