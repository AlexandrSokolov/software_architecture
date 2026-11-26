# 1_DB_Consistency — sources map

## 5.1.07_Transaction_Consistency_Scenarios_DB.md
- Main: todo_Transactions_Scenarios_DB.md — the 12 consistency scenarios, grouped as in 5.1.06:
  - Atomicity: Transfer crashes midway.
  - Read skew: Report shows money missing.
  - Lost update: Counter, Last item sold twice, Ticket edit vanishes, Moved to MySQL, Multi-region cart.
  - Write skew, phantom: Doctors on call, Room double-booked, Username twice, Balance negative.
- Open: "Order shows as paid, then unpaid" (dirty read) fits no group — keep in 5.1.02 as an example, or drop.
- Not here: Errors at serializable, Retry sends two emails, Deadlocks on transfers, Payment API inside a
  transaction → 5.1.09.

## 5.1.08_Transaction_Consistency_Scenarios_ORM.md
- Main: todo_Transactions_Scenarios_ORM.md.
- Also: todo_Pitfalls_Idempotent_Insert.md §1–§2, §4 — save() with an assigned id merges, catching the duplicate,
  concurrent duplicates.
- Reference only (Java project, not linkable): 2.4_Transactions_and_Design.md, snippet "two confirmed payments" —
  write skew with five ORM fixes; a model for a full ORM scenario.

## 5.1.09_Transaction_Protection_Costs.md
- Main: todo_Deadlocks.md.
- Also:
  - todo_Transactions_Scenarios_DB.md — the 4 cost scenarios listed above.
  - todo_Pitfalls_Idempotent_Insert.md — duplicates on retry, idempotent insert as the fix.
  - 5.1.01_Transactions.md — retry cards already there; link, don't repeat.
- Reference only (Java project): 2.4_Transactions_and_Design.md — remote call out of a transaction, outbox,
  reconciliation.

## 5.1.10_Transaction_Diagnostics.md
- Main: todo_Transactions_Diagnostics.md — 13 snippets.
- Reference only (Java project): 2.4_Transactions_and_Design.md, the two "Describe a code snippet" cards — same
  format, good candidates to rewrite here.

## 5.1.11_Serializability.md
- Low priority. Clean up the "TODO #1" and "TODO #2 Gemini" blocks at the end of the file.

## Rules
- Link only to finished deck files in this folder: no todo files, no files from the Java project.
- Links get fixed in one pass at the end, after the renumbering to 5.1.01–5.1.11.