# Event-Driven vs Stream Processing

## The key test

**Does handling one event need other events?**

- **No — one event is enough** → event-driven. An order arrives: save it, send an email. A webhook arrives: update a
  status.
- **Yes — the answer depends on many events together** → stream processing. Orders per minute, match a payment with
  its order, spot five failed logins in a row.

Fast check: if a question talks about time ("per minute", "late", "within an hour") or about many events together, it
is stream processing.

## What each one is about

- **Event-driven** — how systems talk by sending events, and how a receiver handles each event right: duplicates,
  lost events, wrong order, crashes, slow receivers.
- **Stream processing** — computing a result over many events while they keep coming: counts, sums, top N, matching
  events from two streams, patterns over time.

The messages are the same, and so are the tools (Kafka). The difference is what you do with them: react to each one,
or compute over many.

## Where Kafka goes

- Partitions, consumer groups, order per key, many consumers in parallel — this is delivery of single events →
  event-driven.
- Kafka Streams, Flink: counting, grouping by time, matching streams → stream processing.

## Looks the same, but isn't

- **Something is handled twice.** Event-driven: the same message arrives twice — do the work once. Stream processing:
  after a crash the job reads events again — the totals must not count them twice.
- **Something arrives out of time.** Event-driven: an older update arrives after a newer one — don't overwrite the
  newer data. Stream processing: an event arrives after its minute was already counted — fix the count or drop the
  event.

## DDIA, Ch. 12 "Stream Processing"

Despite the chapter's name, two of its three parts are event-driven:

- Transmitting Event Streams (489, 495) → event-driven
- Databases and Streams (501, 503, 508) → event-driven
- Processing Streams (514, 518, 523, 526) → stream processing

# Event-driven — questions

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

### Duplicates

- The same message arrives twice. How do you make sure the work happens once?
- Why can't the sender simply promise to deliver each message exactly once?
- How do you tell that two messages are the same: by the message ID or by the business data?
- Two copies of one message arrive at the same moment on two threads. Both check "seen before?" and both get "no".
  How do you stop the work from running twice?
- How long must you remember the IDs of messages you already handled?
- Some actions are safe to repeat ("set status to paid"), some are not ("add 10 to the balance"). How do you make an
  unsafe one safe?
- Your handler writes to your DB and also calls an outside API. A duplicate arrives. Which part is the problem?

### Lost events

- The sender gave up retrying and you missed an event. How do you find out and fix it?
- Your endpoint was down for three hours. What did you lose, and how do you get it back?
- Every night you compare your data with the sender's data. What do you compare, and how do you avoid pulling
  everything?
- The nightly check and a live event bring the same change. How do you avoid applying it twice?
- How do you notice that events are missing before a customer tells you?
- If events can be lost anyway, why use them at all instead of only the nightly check?

### Crashes while handling

- Your service crashes right after confirming it got the message. What is lost?
- Your service crashes after doing the work but before confirming. What happens next?
- When do you answer "200 OK" to a webhook: before or after doing the work? What does each choice risk?
- The sender waits only 5 seconds for your answer, but the work takes 30. What do you do?
- You keep received messages in memory and handle them later. What is the risk?
- You must save to your DB and also send an event. One succeeds, the other fails. What now?

### Order

- Two updates for the same order arrive in the wrong order. What breaks?
- How can you tell that an update is older than the data you already have?
- Must order hold for all events, or only for events about the same thing (one order, one customer)?
- You handle messages on 10 threads to go faster. What happens to order?
- "Order deleted" arrives before "order created". What do you do with it?
- On every event you ignore its data and fetch the current state from the sender's API. When is that worth it?

### Messages that always fail

- One message fails every time you handle it. What happens to the messages behind it?
- How do you tell a message that will never work from one that failed because the DB was down for a second?
- How many times do you retry, and how long do you wait between tries?
- Where do messages that keep failing go, and who looks at them?
- You fixed the bug. How do you handle the failed messages again safely?

### Too much load

- One consumer can't keep up. How do you spread the work but keep order per customer?
- Many workers take messages from one shared queue. What do you gain, and what do you lose?
- One customer sends 100 times more events than everyone else. What happens to the worker that gets them?
- You add more consumers, but handling doesn't get faster. Why?
- For an hour, messages arrive faster than you can handle them. What happens, and what can you do?
- One consumer stops, and its work moves to another consumer. What can go wrong in those few seconds?

### Reading events again

- A new service starts today and needs last month's events. Is that possible?
- A message was handled and confirmed. Is it gone, or can someone read it again? What decides that?
- You find a bug in how you handled last week's events. Can you run them again through the fixed code?
- How does a consumer remember where it stopped reading? What if it saves that point too early or too late?

### Events as the record of truth

- You keep every change instead of the current state. What does that give you?
- If you keep only the changes, how do you answer "what is the balance now?" fast?
- An event stored a year ago turns out to be wrong. What do you do, if stored events must never change?
- Writes and reads need very different shapes of data. How do you serve both?
- The read side is updated a moment after the write side. What does the user see right after saving?
- How can other systems get every change from your database without your code sending each change?
- A user asks you to delete all their data, but your history must never change. What do you do?

# Stream processing — questions

### When you need it

- Which answers can you only get by looking at many events together?
- When is a nightly job good enough, and when must the result be computed as events arrive?
- Why not store all events in a DB and run a query every minute?

### Which time counts

- You count orders per minute. Which minute does an order belong to: when it happened or when it arrived?
- The sender's clock is three minutes off. What happens to your counts?
- A phone was offline for a day and then sends the whole day's events at once. Where do they go?
- How does the system decide that all events up to 12:05 have probably arrived?
- You run yesterday's events through the same code again. Do you get the same counts? What decides that?

### Grouping events by time

- Orders per minute: fixed minutes that never overlap, or "the last 60 seconds", moving every second? When do you want
  which?
- A user's clicks form a visit, and a visit ends after 30 minutes without clicks. How do you know a visit has ended?
- You show "orders in the last hour", updated every minute. How many times is each order counted?

### Late events

- An order arrives 10 minutes late, after you already published that minute's count. What do you do?
- How long do you wait for late orders before you publish a count? What does waiting cost?
- You published a count, then a late order changes it. How do the users of your count learn about the change?
- An event is so late that you already threw away everything for its minute. What do you do with it?

### Matching two streams

- Payments and orders come on two separate streams, up to an hour apart. How do you match them?
- Where do you keep orders still waiting for their payment, and for how long?
- Each click needs the user's country, which lives in another system. How do you add it without a call per click?
- A user changes their country. Their clicks from before the change: old country or new?
- Search events and click events come separately. How do you tell which search led to which click?

### What you keep in memory

- Alert when one user fails to log in 5 times within a minute. What must you keep in memory?
- Show the top 10 products of the last hour, updated live. What do you store?
- Count unique visitors per day out of 100 million users. Can you keep every user ID? What do you give up if not?
- The data you keep is too big for one machine. How do you split it?
- Where does that data live: in the job's memory or in a separate DB? What does each choice cost?

### Crash and restart

- Your running totals are in memory and the job crashes. How do you restore them without counting twice?
- After a crash the job reads some events again. The counts are right, but an alert email went out twice. Why?
- How often do you save progress? What does saving too often or too rarely cost?
- You change the counting code. Can you keep the totals built by the old version?

### Writing results out

- The job writes counts to a DB every minute, restarts, and writes some counts again. How do you keep the DB right?
- A dashboard reads your results. Does it want every update, or only the final count per minute?
