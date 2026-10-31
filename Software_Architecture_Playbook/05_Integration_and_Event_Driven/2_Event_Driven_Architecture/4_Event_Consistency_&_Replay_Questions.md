# Event Consistency & Replay

## Reading events again

- A new service starts today and needs last month's events. Is that possible?
- A message was handled and confirmed. Is it gone, or can someone read it again? What decides that?
- You find a bug in how you handled last week's events. Can you run them again through the fixed code?
- How does a consumer remember where it stopped reading? What if it saves that point too early or too late?

## Events as the record of truth

- You keep every change instead of the current state. What does that give you?
- If you keep only the changes, how do you answer "what is the balance now?" fast?
- An event stored a year ago turns out to be wrong. What do you do, if stored events must never change?
- Writes and reads need very different shapes of data. How do you serve both?
- The read side is updated a moment after the write side. What does the user see right after saving?
- How can other systems get every change from your database without your code sending each change?
- A user asks you to delete all their data, but your history must never change. What do you do?
