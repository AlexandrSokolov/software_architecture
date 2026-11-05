# Order & Scaling

## Order

- Two updates for the same order arrive in the wrong order. What breaks?
- How can you tell that an update is older than the data you already have?
- Must order hold for all events, or only for events about the same thing (one order, one customer)?
- You handle messages on 10 threads to go faster. What happens to order?
- "Order deleted" arrives before "order created". What do you do with it?
- On every event you ignore its data and fetch the current state from the sender's API. When is that worth it?

## Too much load

- One consumer can't keep up. How do you spread the work but keep order per customer?
- Many workers take messages from one shared queue. What do you gain, and what do you lose?
- One customer sends 100 times more events than everyone else. What happens to the worker that gets them?
- You add more consumers, but handling doesn't get faster. Why?
- For an hour, messages arrive faster than you can handle them. What happens, and what can you do?
- One consumer stops, and its work moves to another consumer. What can go wrong in those few seconds?

## Messages that always fail

- One message fails every time you handle it. What happens to the messages behind it?
- How do you tell a message that will never work from one that failed because the DB was down for a second?
- How many times do you retry, and how long do you wait between tries?
- Where do messages that keep failing go, and who looks at them?
- You fixed the bug. How do you handle the failed messages again safely?
