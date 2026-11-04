# Lost or Duplicate

## Crashes while handling

### Crash after confirming, before the work?
<details><summary>Show answer</summary>

The message is lost. On your confirm the broker deleted it (queue) or moved your read position past it (log). Your
work never ran, and nobody will send the message again.

This is **at-most-once**: each message is handled once or not at all.

Typical cause: confirming on receive — auto-ack, or handing the message to a background thread and confirming at once.

Confirm before the work: you can lose messages, but never get duplicates. Confirming after the work
[brings duplicates instead](#crash-after-the-work-before-confirming).

</details>

### Crash after the work, before confirming?
<details><summary>Show answer</summary>

The broker never got the confirm, so it delivers the message again — after a timeout, or to another consumer (Kafka:
the next consumer starts from the last saved offset). The work runs a second time.

This is **at-least-once**: nothing is lost, but duplicates happen. So the handler must be
[safe to run twice](#message-arrives-twice--do-work-once).

Work and confirm are two steps, and a crash can fall between them. No order of the two avoids both loss and duplicates
— that is [why delivery can't be exactly once](#why-cant-delivery-be-exactly-once).

</details>

### Webhook: reply 200 before or after work?
<details><summary>Show answer</summary>

- **Before** — the sender counts it as delivered and stops retrying. Crash before the work ends → the event is lost.
- **After** — a crash means no 200, so the sender retries → a duplicate. Slow work can also hit the sender's timeout →
  [retries while you are still working](#work-takes-longer-than-sender-waits).

Usual answer: save the event in a durable place (DB table or queue), then reply 200, then do the work from there.
Saving is fast, so no timeouts, and nothing is lost after the 200.

200 means "it's safe with me", not "it's done".

</details>

### Work takes longer than sender waits?
<details><summary>Show answer</summary>

Don't do the work inside the request. If you do, the sender times out after 5 seconds, retries, and the same work now
runs twice or more in parallel. Some senders also disable an endpoint that keeps timing out.

Split receiving from working:
- **On receive** — check the signature and format, [store the event durably](#webhook-reply-200-before-or-after-work),
  reply 200 — all in milliseconds.
- **In the background** — a worker takes the event from the store and does the 30-second work, with its own retries.

Reply fast, work later — but only after the event is stored.

</details>

### Messages kept in memory for later?
<details><summary>Show answer</summary>

You already confirmed them (or replied 200), so the broker and the sender have forgotten them. A crash, deploy or
restart wipes the memory, and all those messages are gone with no trace.

The buffer also grows under load until the service runs out of memory.

Fix: keep the buffer in a durable place (DB table or queue), or confirm only after the work is done.

A memory buffer behind a confirm loses data on every restart.

</details>

### DB write plus event — one fails?
<details><summary>Show answer</summary>

You save the order, then publish "OrderPlaced", and the broker is down: the order exists, nobody hears about it.
Publish first, then the DB commit fails: everyone hears about an order that doesn't exist. Retries don't help — a crash
can fall between the two writes. This is the **dual write** problem: the DB and the broker share no transaction.

Fix: write only once, to the DB, and get the event out of that write:
- **Outbox** — in the same DB transaction, save the order and an outbox row with the event. A separate relay reads the
  outbox and publishes, retrying until the broker confirms. Both are saved or neither. Delivery is at-least-once, so
  receivers must [drop duplicates](#message-arrives-twice--do-work-once).
- **CDC** — publish from the [DB's own change log](4_Event_Consistency_&_Replay.md#get-every-db-change-without-app-code)
  instead of from your code.

One write, to one place; the event comes out of it.

</details>

## Duplicates

### Message arrives twice — do work once?
<details><summary>Show answer</summary>

You can't stop the copy from arriving; you make the second run do nothing. Two ways:
- **Remember what you handled** — save the message ID in a table with a unique key, in the same DB transaction as the
  work. The copy finds the ID (or its insert fails) → skip and confirm.
- **Make the work safe to repeat** — "set status = PAID" instead of "add 10", upsert by key, update only if the version
  matches; [turn "add 10" into a repeatable step](#make-add-10-safe-to-repeat).

The ID record and the work must commit together. If not, a crash between them either skips the work or runs it twice.

A handler is **idempotent** when running it twice gives the same result as running it once.

</details>

### Why can't delivery be exactly once?
<details><summary>Show answer</summary>

The sender sends and waits for a confirm. No confirm comes. Two things could have happened:
- the message was lost → it must be sent again;
- the message arrived and was handled, but the confirm was lost (or the receiver crashed before sending it) → sending
  again makes a duplicate.

The sender can't tell them apart, so it must choose: resend (at-least-once, duplicates) or not (at-most-once, loss).

"Exactly-once" in products like Kafka means exactly-once *effect* inside that product: at-least-once delivery plus
duplicate detection and transactions. Once your handler touches another system, dropping duplicates is your job.

Exactly-once effect = at-least-once delivery + a handler that is safe to repeat.

</details>

### Duplicate check: message ID or business data?
<details><summary>Show answer</summary>

- **Message ID** — catches the same message delivered again by the broker, or a retry with the same ID. Misses the same
  fact sent again with a new ID: a double click, a sender that builds a new message on retry, the nightly check.
- **Business key** — payment ID, invoice number, order ID + action. Catches the same fact however it arrives. Risk: if
  the key is too wide, two real events look the same — two real €10 payments from one customer.

Use a business key when one truly names the fact; fall back to the message ID.

Check on what makes the business fact unique, not on the message that carried it.

</details>

### Two copies at once — both pass check?
<details><summary>Show answer</summary>

"Check, then act" is two steps: both threads read "not seen" before either writes. Make the check and the claim one
step:
- **Unique key** — insert the message ID into a table with a unique constraint, in the same transaction as the work.
  The second insert fails → rollback, skip. The DB does the locking.
- **Conditional update** — `UPDATE orders SET status = 'PAID' WHERE id = ? AND status = 'NEW'`. Only one thread gets
  "1 row updated".
- **Same key, same thread** — route by key so all copies go to one consumer in sequence (Kafka partition by key). This
  avoids the race instead of resolving it.

Doesn't work: a check in an in-memory set (other instances don't see it), or SELECT then INSERT without a unique key.

Let one atomic write decide who wins.

</details>

### How long to keep handled IDs?
<details><summary>Show answer</summary>

At least as long as a copy can still arrive:
- the sender's retry window — it retries for 3 days, you keep IDs longer than 3 days;
- the broker's retention, if you might replay from it;
- your own replays and nightly checks.

Then drop old IDs — a TTL, or a table split by day where old days are dropped.

If the window has no end (replay from the start), check by business state instead — version check or upsert. That needs
no memory of IDs.

Keep IDs for the longest window a copy can come back in.

</details>

### Make "add 10" safe to repeat?
<details><summary>Show answer</summary>

Tie the change to something that can be used only once:
- **Ledger** — insert a row `(txId, +10)` with a unique `txId`; balance = sum of rows. The copy fails on the unique key.
- **Expected version** — the event carries the version it applies to:
  `UPDATE account SET balance = balance + 10, version = 6 WHERE id = ? AND version = 5`. The copy finds version 6 → no
  change.
- **Processed-ID table** — [save the message ID](#message-arrives-twice--do-work-once) in the same transaction as the
  `+10`.

An increment becomes safe once it is bound to an ID or version that can be used only once.

</details>

### Duplicate hits DB write and API call?
<details><summary>Show answer</summary>

The API call is the problem. The DB write you can
[protect in the same transaction](#message-arrives-twice--do-work-once). The API is outside that transaction:
- the copy sends the email or charges the card again;
- if the DB rolls back after the call, you can't take the email back.

Fixes:
- **Idempotency key** — pass a key built from the message ID (e.g. Stripe's `Idempotency-Key` header). The provider
  drops the repeat.
- **Outbox for outgoing calls** — in the same transaction save "email pending"; a separate step makes the call and marks
  it "sent". A duplicate message finds the row already there.
- **Neither available** — check first ("was it done?") and accept a small gap, or accept duplicates where they are
  harmless.

Your duplicate check covers only what shares your transaction; outside calls need their own key.

</details>

## Lost events

### Sender stopped retrying — event missed?
<details><summary>Show answer</summary>

Find out:
- **Sender's side** — most webhook senders log failed deliveries and alert when they disable an endpoint.
- **Your side** — a [gap in versions](#missing-events--notice-before-customers) (got v5 after v3), or no events for
  longer than usual.

Fix:
- ask the sender to resend — many offer redelivery from a dashboard or an API;
- fetch the current state of the affected records from the sender's API;
- run the [nightly comparison](#nightly-check-without-pulling-all-data).

Events can't prove that nothing is missing; you need a way to pull data back from the sender.

</details>

### Endpoint down three hours — recover what?
<details><summary>Show answer</summary>

It depends on the sender's retry window:
- **Retries longer than 3 hours** (often a day or more) — nothing lost, but a flood arrives when you are back, often out
  of order.
- **Shorter, or no retries** — the events of those 3 hours are gone from the push.

Get them back:
- ask the sender to resend that window, or pull "changed since T" from its API;
- otherwise run the [nightly comparison](#nightly-check-without-pulling-all-data) for that window.

Know each sender's retry window: it is how long you can be down without losing data.

</details>

### Nightly check without pulling all data?
<details><summary>Show answer</summary>

**Reconciliation** — compare your data with the source of truth and fix the differences. To keep it small:
- **Only what changed** — ask the sender for records with `updated_at` after the last run (minus a safety margin).
- **Versions, not records** — compare pairs of (ID, version). Fetch full data only where they differ.
- **Summaries first** — per day or ID range, compare a count and a hash; look closer only at ranges that differ.

Then fetch and apply what is missing or newer, and flag records you have but the sender doesn't.

Narrow by time, then by summary; fetch full data only where the two sides disagree.

</details>

### Nightly check and live event overlap?
<details><summary>Show answer</summary>

Both paths must write the same way, keyed by record and version:
- write "set the record to version N" (upsert), not "apply this change" — doing it twice is harmless;
- skip if your stored version is already N or higher — this also stops an old nightly result from overwriting a newer
  live event.

A message-ID check doesn't help: the two paths carry different IDs, so
[check by business key](#duplicate-check-message-id-or-business-data).

Make both paths safe to repeat on (record, version), and the overlap stops mattering.

</details>

### Missing events — notice before customers?
<details><summary>Show answer</summary>

- **Gaps in versions** — last was v5, now v7 → v6 is missing.
- **Silence** — alert when an event type drops below its usual rate for that hour (no orders for 20 minutes on a
  weekday).
- **Broken flows** — an order "paid" but not "shipped" after N hours.
- **Nightly fixes** — count what the [nightly comparison](#nightly-check-without-pulling-all-data) had to fix; a rising
  count means events are being lost.
- **Sender alerts** — failed deliveries, a disabled endpoint.

Watch for what should have arrived, not only for errors on what did.

</details>

### Why events if nightly check exists?
<details><summary>Show answer</summary>

- **Speed** — events arrive in seconds; the nightly check is up to a day late. Order confirmations, stock and payments
  can't wait a day.
- **Load** — events carry only what changed, when it changed. Pulling everything often would be heavy for both sides.
- **Small nightly work** — events deliver almost everything, so the nightly check fixes only a handful of records.

They do different jobs: events are the fast path; the nightly check is the safety net for the rare miss.

</details>
