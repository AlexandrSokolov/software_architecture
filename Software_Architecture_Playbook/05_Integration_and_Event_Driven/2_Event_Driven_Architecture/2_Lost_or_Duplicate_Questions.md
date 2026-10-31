# Lost or Duplicate

## Crashes while handling

- Your service crashes right after confirming it got the message. What is lost?
- Your service crashes after doing the work but before confirming. What happens next?
- When do you answer "200 OK" to a webhook: before or after doing the work? What does each choice risk?
- The sender waits only 5 seconds for your answer, but the work takes 30. What do you do?
- You keep received messages in memory and handle them later. What is the risk?
- You must save to your DB and also send an event. One succeeds, the other fails. What now?

## Duplicates

- The same message arrives twice. How do you make sure the work happens once?
- Why can't the sender simply promise to deliver each message exactly once?
- How do you tell that two messages are the same: by the message ID or by the business data?
- Two copies of one message arrive at the same moment on two threads. Both check "seen before?" and both get "no".
  How do you stop the work from running twice?
- How long must you remember the IDs of messages you already handled?
- Some actions are safe to repeat ("set status to paid"), some are not ("add 10 to the balance"). How do you make an
  unsafe one safe?
- Your handler writes to your DB and also calls an outside API. A duplicate arrives. Which part is the problem?

## Lost events

- The sender gave up retrying and you missed an event. How do you find out and fix it?
- Your endpoint was down for three hours. What did you lose, and how do you get it back?
- Every night you compare your data with the sender's data. What do you compare, and how do you avoid pulling
  everything?
- The nightly check and a live event bring the same change. How do you avoid applying it twice?
- How do you notice that events are missing before a customer tells you?
- If events can be lost anyway, why use them at all instead of only the nightly check?
