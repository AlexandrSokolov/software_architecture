# Event Design

## Why events at all

### Event instead of direct call — why?
<details><summary>Show answer</summary>

A direct call ties the sender to the receiver in three ways. An event removes each one:
- **Waiting** — a call blocks until the receiver answers. An event is written to the broker, and the sender moves on.
  One slow receiver no longer [slows every caller up the chain](#call-chain-with-one-slow-service).
- **Being up** — a call fails when the receiver is down. An event waits in the broker until the receiver is back.
- **Knowing who** — the sender calls each receiver by name, so every new receiver means a change in the sender. With an
  event, a new receiver subscribes on its own, and the
  [sender stops knowing who reacts](#sender-doesnt-know-receivers--gain-loss).

The sender stops depending on the receiver: on its speed, on it being up, and on who it is.

</details>

### Command vs event — what's the difference?
<details><summary>Show answer</summary>

- **Command** — "charge this card". Sent to one known receiver, which is expected to do it and may refuse.
- **Event** — "order placed". A fact in past tense: it already happened and can't be refused. The sender doesn't know
  who listens; each receiver decides on its own what to do.

Command: the sender decides what happens next. Event: the receivers decide.

</details>

### Call chain with one slow service?
<details><summary>Show answer</summary>

With calls, A waits for B, B for C, C for D. When D is slow:
- C holds a thread and a connection while it waits. Requests pile up, C's pool runs out, and C slows down too — then B,
  then A.
- A's response time is the sum of all four. A's timeout fires, though A did nothing wrong.
- In the end A fails because of D. This is a **cascading failure** — one slow service spreads to all its callers.

With events, A publishes and is done. D's slowness only makes D's queue grow; A, B and C keep working. The cost: the
work finishes later, and A can't return D's result to its own caller.

Calls spread one slow service to every caller up the chain; events keep the slowness inside its own queue.

</details>

### Direct call over event — when?
<details><summary>Show answer</summary>

- **The caller needs the answer to go on:**
  - the user waits for the result on screen — price, stock, login;
  - the next step depends on the reply — payment approved, then ship;
  - the caller must get a clear "no" right away — card declined, invalid input.
- **Nothing to gain from an event** — one receiver, a simple flow. The event only adds a broker, late results and harder
  debugging.

Need the answer to continue — call. Only announcing a fact — event.

</details>

### Sender doesn't know receivers — gain, loss?
<details><summary>Show answer</summary>

Gain:
- new receivers join without any change in the sender;
- teams change and release on their own; a receiver's change can't break the sender.

Loss:
- no single place shows what happens after the event — the flow is spread over the receivers;
- the sender can't tell whether anyone handled the event, or whether it failed;
- changing or removing the event is risky — nobody has a full list of who depends on it.

Easier to add receivers, harder to see the whole flow.

</details>

### Order stuck across services — how to find?
<details><summary>Show answer</summary>

- **Correlation ID** — the first service creates an ID (often the order ID). Every service copies it into each event it
  sends and into every log line. Search the logs by it: the last service that logged it is where the order stopped.
- **Tracing** — pass the trace context in event headers (OpenTelemetry). The trace shows each step and how long it took.
- **Status per order** — each step updates the order's status (placed → paid → packed). An order stuck in one status
  too long raises an alert.
- **One owner for the process** — if you need this view all the time, let an **orchestrator** drive the steps and track
  each order's state in one place.

When services only react to each other's events (**choreography**), nobody knows the whole state — you have to build
that view yourself.

</details>

## Designing the event

### Event: full data or only a notice?
<details><summary>Show answer</summary>

- **Full data** (**event-carried state transfer**) — "order 42 placed: items, amount, address". Receivers work on their
  own, with no calls back; the sender may even be down. Cost: bigger events, the sender's data shape reaches every
  receiver, and copies of the data spread.
- **Only a notice** (**event notification**) — "order 42 changed". Small and stable, but every receiver must
  [fetch the data itself](#notice-only-event--cost-for-receivers).

Pick full data when receivers must work without the sender — the usual case. Pick a notice when the data is large,
sensitive, or only its latest version matters.

</details>

### Notice-only event — cost for receivers?
<details><summary>Show answer</summary>

Every receiver calls the sender's API to get the data. That costs:
- **Load** — N receivers make N calls per event; a burst of events becomes a burst of calls on the sender.
- **Coupling** — the sender must be up when receivers call; the dependency events were meant to remove is back.
- **Lost steps** — the call returns the current state, not the state at the event's time. Two quick changes may both
  return the second one.

The notice keeps the event small but moves the cost to the sender's API.

</details>

### Fields every event must carry?
<details><summary>Show answer</summary>

- **Event ID** — unique per event, to [drop duplicates](2_Lost_or_Duplicate.md#message-arrives-twice--do-work-once).
- **Type and schema version** — to pick the right handler and read both old and new formats.
- **Entity ID** (order ID) — to keep order per entity and pick the partition.
- **Version per entity** — to [spot an older update](3_Order_&_Scaling.md#is-this-update-older-than-mine) that arrives
  late.
- **Event time** — when it happened, not when it was sent; for audit and time-based logic.
- **Correlation ID** — to [follow one order across services](#order-stuck-across-services--how-to-find).

Each field answers a question the receiver would otherwise have to guess.

</details>

### Event per field or per action?
<details><summary>Show answer</summary>

- **Per field** — "addressChanged", "phoneChanged". Receivers get table changes and must guess the business meaning.
  One business action becomes several events that can arrive split or out of order, and a receiver may act on half.
- **Per action** — "CustomerMoved", "OrderCancelled". One event carries the meaning and all changes that belong
  together. The risk is going too wide: one "OrderUpdated" for everything, so receivers must compare old and new data to
  see what happened.

Name events after what the business did, not after which column changed.

</details>

### Who owns the event format?
<details><summary>Show answer</summary>

The sender. It owns the data and its meaning, and it publishes one format for all receivers. If receivers shaped the
event, the sender would change for every new consumer — the coupling events were meant to remove.

In return the sender owes receivers a stable contract:
- the schema is published (schema registry or shared contract);
- changes stay [compatible with old receivers](#change-event-fields-without-breaking-receivers);
- breaking changes are announced, and the old version is kept for a while.

A receiver that needs another shape translates on its own side.

</details>

### Change event fields without breaking receivers?
<details><summary>Show answer</summary>

- **Add** — make the field optional. Old receivers ignore fields they don't know; new receivers handle it missing in old
  events.
- **Remove** — first make it optional, wait until no receiver reads it, then drop it.
- **Rename** — never in place. Add the new field, send both, move receivers over, remove the old one.
- **Change type or meaning** — that breaks receivers. Publish a new version (`OrderPlacedV2`) next to the old one until
  all receivers have moved.

A schema registry with a compatibility check can block a breaking change before it is published.

Every change becomes steps that old and new code can both survive.

</details>

### Two teams want different event data?
<details><summary>Show answer</summary>

Ask whether they describe the same business fact:
- **Same fact** — one event carrying the data that belongs to that fact; each team takes what it needs. If one team
  needs data that is part of the fact (the delivery address of a placed order), add it — still one event.
- **Different facts** — billing needs "invoice issued", shipping needs "ready to ship". These are two events.

Never one event per team ("OrderPlacedForBilling"): the sender ends up serving each team, and the coupling is back.
Data outside the fact: the team fetches it or builds its own view.

Events follow business facts, not what each team wants.

</details>
