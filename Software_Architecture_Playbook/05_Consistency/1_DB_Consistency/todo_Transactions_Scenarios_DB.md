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

### Transfer crashes between debit and credit — what happens?
<details><summary>Show answer</summary>

**Verdict:** if both `UPDATE`s run in one transaction, nothing is lost: the database undoes the debit. If each ran as
its own auto-committed statement, the money is gone.

**Reasoning:** atomicity means a transaction either commits fully or leaves no trace. After a crash, the database
uses its log to roll back every transaction that did not commit, so the app can simply retry the whole transfer.

**Trade-off:** a retry is safe only if the first attempt really failed. If the commit succeeded but the reply was lost
(network timeout), a blind retry moves the money twice. Give each transfer a unique id and let the retry check it.

</details>

### Report from many queries shows money missing — why?
<details><summary>Show answer</summary>

**Verdict:** the report read some accounts before a transfer committed and others after it. Run the whole report in
one snapshot: snapshot isolation (`REPEATABLE READ` in PostgreSQL).

**Reasoning:** at read committed, every query sees the latest committed data. A transfer moves $100 from A to B.
Query 1 reads B before the transfer (no $100 yet), query 2 reads A after it ($100 already gone): the total is $100
short. Rerun the report and it is correct, because the problem is the timing, not the data. Backups and long
analytic queries need the same fix: one consistent snapshot for the whole run.

**Trade-off:** a long snapshot makes the database keep old row versions until it ends. In PostgreSQL that holds back
cleanup (vacuum), and busy tables grow.

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

### Page-view counter loses increments under load — why?
<details><summary>Show answer</summary>

**Verdict:** the app reads the count, adds 1 in code and writes the result back. Two requests read 42 and both write
43. Let the database do the math:

```sql
UPDATE pages SET views = views + 1 WHERE id = 7;
```

**Reasoning:** a read → modify → write cycle in app code loses updates. The one-statement version is safe: the
database locks the row from its read to its write.

**Trade-off:** every increment of one row now waits for the previous one. For a very hot counter, split it into
several rows and sum them, or collect increments in memory and write them in batches.

</details>

### Last item in stock sold twice — fix?
<details><summary>Show answer</summary>

**Verdict:** check and write in one statement, then look at the row count:

```sql
UPDATE products SET stock = stock - 1 WHERE id = 42 AND stock > 0; -- 1 row: sold; 0 rows: sold out
```

**Reasoning:** "read stock, if it is above 0 then update" lets both buyers see 1. In the one-statement version the
second buyer's `UPDATE` waits for the first one's row lock, then checks `stock > 0` again on the new value and changes
0 rows. A `CHECK (stock >= 0)` constraint is a cheap safety net on top.

**Trade-off:** all buyers of one product queue on one row. For a flash sale, take reservations or put orders into a
queue instead.

</details>

### Two agents edit one ticket, one edit vanishes — fix?
<details><summary>Show answer</summary>

**Verdict:** an optimistic check with a version. The page gets `version = 5` with the ticket and sends it back on
save:

```sql
UPDATE tickets SET body = ?, version = 6 WHERE id = 7 AND version = 5; -- 0 rows: someone saved first
```

On 0 rows, show the agent the newer ticket instead of overwriting it.

**Reasoning:** the edit spans user think time across two HTTP requests. A row lock lives only inside an open
transaction, so it can't wait for the agent. A version can travel with the page and come back.

**Trade-off:** the second agent has to redo or merge the edit. If conflicts are common, save per field or show
"someone else is editing".

</details>

### Moved to MySQL, overwrites go unnoticed — why?
<details><summary>Show answer</summary>

**Verdict:** the code relied on PostgreSQL's repeatable read, which aborts the second of two transactions that
overwrite the same row. MySQL InnoDB's repeatable read does not detect lost updates, so the second write simply wins.
Protect the writes explicitly: an atomic `UPDATE`, `SELECT ... FOR UPDATE`, or a version check.

**Reasoning:** isolation level names are not portable. "Repeatable read" gives different guarantees in different
databases.

**Trade-off:** explicit protection is more code, but it works on any database and shows the intent in the code.

</details>

### Multi-region cart loses items — why?
<details><summary>Show answer</summary>

**Verdict:** the database settles concurrent writes with last write wins: it keeps one write and drops the others.
Keep both versions (siblings) and merge them, e.g. as a union of the carts, or use a CRDT set.

**Reasoning:** multi-leader and leaderless replication accept writes on several nodes at once, so there is no single
up-to-date copy to lock or compare against. Merging loses nothing when the updates give the same result in any order,
and adding items does.

**Trade-off:** removals don't merge cleanly: a union brings deleted items back, unless each delete is kept as a marker
(a tombstone).

</details>

### Both on-call doctors went off call — why?
<details><summary>Show answer</summary>

**Verdict:** write skew. Both checked "at least 2 on call", both passed, and each took itself off call. Lock the rows
the check reads, or run the transaction at serializable:

```sql
SELECT * FROM doctors WHERE on_call AND shift_id = 1234 FOR UPDATE;
```

**Reasoning:** each transaction wrote a different row, so nothing was overwritten. Fixes that guard the written row
(versions, PostgreSQL's lost-update detection) see no conflict. The conflict sits in the rows that were read.

**Trade-off:** the lock makes every change to that shift wait in line. Serializable needs no lock in the code, but it
needs a retry on abort.

</details>

### Meeting room double-booked despite a check — fix?
<details><summary>Show answer</summary>

**Verdict:** the check finds no overlapping booking, so there is no row to lock, and both inserts pass: a phantom. On
PostgreSQL, let the database reject overlaps itself:

```sql
CREATE EXTENSION btree_gist;                                        -- needed for room_id WITH =
ALTER TABLE bookings ADD EXCLUDE USING gist (room_id WITH =, during WITH &&); -- during: a tstzrange column
```

Elsewhere: serializable, or lock the room's row before the check.

**Reasoning:** `FOR UPDATE` on the check locks only the rows it returns, and it returns none. The constraint compares
the new row with all rows, including ones still being inserted by other transactions.

**Trade-off:** the constraint is PostgreSQL-only. Locking the room's row works anywhere, but it also blocks bookings
that don't overlap, and it is safe only at read committed.

</details>

### Same username registered twice — fix?
<details><summary>Show answer</summary>

**Verdict:** a unique constraint on the username, and the violation is handled as "name taken".

**Reasoning:** "check the name is free, then insert" checks for a row that doesn't exist yet, so nothing can be
locked. The unique index is checked at insert time: the second insert waits for the first transaction, then fails.

**Trade-off:** the error now comes from the insert, not from the check, so the code must handle it there. For
case-insensitive names, put the index on `lower(username)`.

</details>

### Concurrent purchases drive a balance negative — why?
<details><summary>Show answer</summary>

Each purchase inserts a ledger row, sums the account's rows and checks that the sum stays at or above 0.

**Verdict:** write skew through inserts: each sum misses the other purchase's new row. Two fixes:
- Lock the account row first (`SELECT ... FROM accounts WHERE id = ? FOR UPDATE`), then sum and insert. Safe at read
  committed.
- Keep a balance column and spend with one conditional statement:
  `UPDATE accounts SET balance = balance - 50 WHERE id = ? AND balance >= 50` (0 rows: not enough money).

**Reasoning:** the rows that matter are new rows the sum never saw, so locking the summed rows doesn't help. The
account row is one existing row that every purchase can queue on.

**Trade-off:** purchases on one account run one at a time, which is usually fine. The account lock works only if
every path that spends takes it.

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
