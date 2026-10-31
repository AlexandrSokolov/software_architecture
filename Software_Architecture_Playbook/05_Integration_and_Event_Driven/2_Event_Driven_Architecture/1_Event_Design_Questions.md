# Event Design

## Why events at all

- Why send an event instead of calling the other service directly?
- What is the difference between asking another service to do something and telling it that something happened?
- Service A calls B, B calls C, C calls D, and D is slow. What happens to A? What changes if they send events instead?
- When is a direct call still better than an event?
- The sender no longer knows who reacts to its event. What does it gain, and what does it lose?
- One business process is spread over five services that react to each other's events. How do you see where a
  single order is stuck?

## Designing the event

- Should the event carry all the data, or only say "something changed"?
- If the event only says "something changed", what must every receiver do next, and what does that cost?
- What must every event contain so that a receiver can handle it safely?
- One event per changed field, or one event per business action? What goes wrong with each?
- Who owns the event's format: the sender or the receivers?
- You need to add, rename, or remove a field. How do you avoid breaking receivers that still run old code?
- Two teams want different data in the same event. One event or two?
