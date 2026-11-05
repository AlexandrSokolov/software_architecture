# Books — Resilience

Folder: `05_Integration_and_Event_Driven/6_Resilience/`

## Main book

- **_Release It!_, 2nd ed.** — Michael Nygard (Pragmatic Bookshelf, 2018).
  How systems fail in production, and how to stop one failure from spreading to the whole system: timeouts,
  circuit breaker, bulkheads, back pressure, shed load, fail fast. Main idea: every call to another system
  (an integration point) is the most likely place for a failure to start.

## Also useful

- **_Site Reliability Engineering_ ("Google SRE book")** — Beyer, Jones, Petoff, Murphy (O'Reilly, 2016).
  Free to read at sre.google. Chapters "Handling Overload" and "Addressing Cascading Failures".
- **_Building Microservices_, 2nd ed.** — Sam Newman (O'Reilly, 2021).
  Chapter "Resiliency": timeouts, retries, circuit breakers, bulkheads between microservices.
- **_Designing Data-Intensive Applications_, Ch. 9** — already in your plan.
  Timeouts, partial failures, why a call that timed out may still have done the work.
