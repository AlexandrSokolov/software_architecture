### Transaction scenarios (database) — what's covered?
<details><summary>Show answer</summary>

- **One unit of work**
  - [Transfer crashes midway](#transfer-crashes-between-debit-and-credit--what-happens)
- **Reads while others write**
  - [Report shows money missing](#report-from-many-queries-shows-money-missing--why)
  - [Paid, then unpaid](#order-shows-as-paid-then-unpaid-again--why)
- **Two writers, one row**
  - [Counter misses increments](#page-view-counter-loses-increments-under-load--why)
  - [Last item sold twice](#last-item-in-stock-sold-twice--fix)
  - [Ticket edit vanishes](#two-agents-edit-one-ticket-one-edit-vanishes--fix)
  - [Overwrites unnoticed after a move to MySQL](#moved-to-mysql-overwrites-go-unnoticed--why)
  - [Multi-region cart loses items](#multi-region-cart-loses-items--why)
- **Two writers, different rows**
  - [Doctors on call](#both-on-call-doctors-went-off-call--why)
  - [Room double-booked](#meeting-room-double-booked-despite-a-check--fix)
  - [Username taken twice](#same-username-registered-twice--fix)
  - [Balance goes negative](#concurrent-purchases-drive-a-balance-negative--why)
- **The price of safety**
  - [Errors at serializable](#serializable-causes-random-errors-under-load--bug)
  - [Retry sends two emails](#retry-after-an-abort-sends-two-emails--why)
  - [Deadlocks on transfers](#opposite-transfers-fail-with-deadlocks--fix)
  - [External call inside a transaction](#payment-api-called-inside-a-transaction--risk)

All or nothing → who sees what → who overwrites whom → who breaks whose rule → what the fixes cost.

</details>

### Order shows as paid, then unpaid again — why?
<details><summary>Show answer</summary>

**Verdict:** the screen read a change that was not committed yet, and that transaction then rolled back: a dirty read.
Read committed prevents it. Look for read uncommitted, or SQL Server's `NOLOCK` hint, which is the same thing.

**Reasoning:** read committed shows only committed data, so you never act on a write that may still be undone. Most
databases default to read committed or stronger.

**Trade-off:** `NOLOCK` usually gets added so that reads don't wait behind writers. In SQL Server the safe way to get
that is snapshot-based read committed (`READ_COMMITTED_SNAPSHOT`), not dirty reads.

</details>


### Serializable causes random errors under load — bug?
<details><summary>Show answer</summary>

**Verdict:** not a bug. At serializable, the database aborts one of two conflicting transactions (PostgreSQL: SQL
state `40001`). Catch the error and retry the whole transaction.

**Reasoning:** PostgreSQL's serializable mode runs transactions without extra locks and tracks what each one read. If
the result could not have come from running them one after another, it aborts one. Lock-based databases make
transactions wait instead, and sometimes deadlock.

**Trade-off:** more conflicts mean more retries, so keep transactions short. The guarantee covers only transactions
that run at serializable; one running at read committed can still break the rule.

</details>

### Retry after an abort sends two emails — why?
<details><summary>Show answer</summary>

**Verdict:** the email was sent inside the transaction, and the retry sent it again. A rollback undoes database
writes, not emails. Send after commit, or write the message to an outbox table in the same transaction and send it
from there.

**Reasoning:** anything outside the database (emails, HTTP calls, messages) happens when the code runs, whatever the
transaction does later. Every retry repeats it.

**Trade-off:** the outbox adds a table and a sender process, and the sender may still send twice after a crash, so the
receiver should handle duplicates.

</details>

### Opposite transfers fail with deadlocks — fix?
<details><summary>Show answer</summary>

**Verdict:** transfer A→B locks A, then B; transfer B→A locks B, then A. Each waits for the other. Lock both rows in a
fixed order (lowest id first), and retry when the database aborts one of them.

**Reasoning:** the database detects the wait cycle and aborts one transaction; that abort is the error you see. With
a fixed order, the cycle can't form.

**Trade-off:** every path that locks these rows must use the same order. One path that doesn't brings the deadlocks
back.

</details>

### Payment API called inside a transaction — risk?
<details><summary>Show answer</summary>

**Verdict:** the transaction holds its row locks and a database connection while it waits for the network. Others
queue behind the locks, and under load the connection pool runs out. Move the call out of the transaction.

**Reasoning:** split the work into short steps: commit the order as PENDING, call the API with an idempotency key,
commit the result. Nothing holds locks while waiting on the network.

**Trade-off:** there is no single atomic step anymore. A crash between the steps leaves a PENDING order, so a job must
find such orders and finish them.

</details>
