# 09_Operability / 2_Observability — pitfalls from practice: logging

**Question it answers:** What should one service log, at which level, how cheaply — and how do I find a failed
event in the logs without logging personal data?

**Source:** `developer-pitfalls.md` (draft v4) §9.1, §9.3–9.5, §13.4; login-tracking assessment
(log lines by `messageId`, Log4j API over Logback, LogQL queries in the README).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Volume and level

- [ ] **An INFO line per incoming event** (every login) — millions of lines a day, storage cost, noise. Per-event
      logs at DEBUG; counts and rates as metrics.
- [ ] **Level = what the reader must do** — ERROR: someone must act; WARN: handled failure / degraded outcome
      (tracking `unsuccessful`, duplicate ignored); INFO: lifecycle; DEBUG: per-event detail.
- [ ] **Framework logs you can't avoid** — Hibernate logs every duplicate as an SQL ERROR before you catch it;
      don't silence that logger globally — know it is expected.

## 2. Finding things

- [ ] **Technical ids only** — every line references `messageId`; that is the correlation key without personal
      data (rules → `11_Security`).
- [ ] **Stack traces are many lines in Loki** — one exception = several log lines; count with
      `count_over_time(... |~ "WARN|ERROR" [5m])`.
- [ ] **`traceId` in every log line** — not done in the project; next step with tracing → `todo_Observability.md`.

## 3. Cost of logging code

- [ ] **Lazy message construction** — Log4j API supplier lambdas: `log.debug(() -> "...%s".formatted(id))`,
      nothing formatted when the level is off. `LogManager.getLogger()` without arguments.
- [ ] **Logging API vs implementation** — Boot logs through Logback; Log4j2 API on top via `log4j-to-slf4j`; SLF4J 2
      fluent API offers the same. A choice, not a defect — be ready to explain it.

---

## Covered elsewhere

- Structured logs, correlation, trace context → `todo_Observability.md`.
- Shipping logs (Alloy → Loki) → `3_Instrumentation/todo_Pitfalls_Instrumentation.md`.
- Personal data in logs, `toString()`, exception messages → `11_Security/todo_Pitfalls_Security.md`.
