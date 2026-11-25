# 05_Consistency / 1_DB_Consistency — Deadlocks

**Question it answers:** Two transactions wait for each other's locks and neither can finish. Why does it happen in
a database, what does the database do about it, and how does my code avoid, handle and diagnose it?

**Why here:** a deadlock is a liveness problem, not a consistency one — but in a database it comes from the locks
taken for isolation (row locks on writes, `FOR UPDATE`, lock-based serializable). It is the price of safety, next to
isolation levels and serializability.

**Sources:**
- DDIA 2nd ed. — transactions chapter (two-phase locking, deadlocks).
- PostgreSQL docs — "Explicit Locking" (row-level locks, deadlocks), `pg_locks`, `deadlock_timeout`.
- MySQL docs — "Deadlocks in InnoDB", `SHOW ENGINE INNODB STATUS`.
- SQL Server docs — "Deadlocks guide" (deadlock graph, lock escalation).

Developer view only: why my transactions deadlock and what I change in code and schema; no lock manager internals.

---

## 1. The problem

- [ ] What a deadlock is: T1 holds A and waits for B, T2 holds B and waits for A — a wait cycle.
- [ ] Deadlock vs plain lock wait: a wait ends when the holder commits; a cycle never ends by itself.
- [ ] Why it appears only under load: needs two transactions in the overlapping window — rare, hard to reproduce
      in tests (link race conditions in `5.1.2_Isolation_Levels.md`).

## 2. Where database locks come from (what my code takes without noticing)

- [ ] Every `UPDATE` / `DELETE` locks the rows it changes until commit — no `FOR UPDATE` needed.
- [ ] `SELECT ... FOR UPDATE` / `FOR SHARE` — explicit row locks.
- [ ] Lock-based serializable (two-phase locking: MySQL serializable, SQL Server serializable) — even reads lock.
- [ ] Foreign keys: inserting a child locks (shares) the parent row; deleting / updating a parent waits for children.
- [ ] Unique indexes: two inserts of the same key wait for each other.
- [ ] `UPDATE` without a matching index scans and locks far more rows than it changes (MySQL InnoDB gap / next-key
      locks).
- [ ] SQL Server lock escalation: many row locks turn into a table lock.
- [ ] ORM-generated SQL: flush order decides the lock order (Hibernate orders inserts / updates by entity type, not
      by your code).

## 3. Typical deadlock patterns

- [ ] **Opposite order:** transfer A→B locks A then B, transfer B→A locks B then A (link scenario "Opposite
      transfers fail with deadlocks" in `todo_Transactions_Scenarios_DB.md`).
- [ ] **Read, then upgrade:** two transactions take a shared lock on the same row, then both try to update it.
- [ ] **Batch updates in different order:** two jobs update the same set of rows sorted differently.
- [ ] **Parent and child:** one transaction updates the parent then inserts a child, another the other way round.
- [ ] **Hot row + long transaction:** not a deadlock, but looks like one (long waits, timeouts) — how to tell apart.

## 4. What the database does

- [ ] Detection: the database finds the cycle and aborts one transaction (the victim); the other continues.
- [ ] Error codes to recognise: PostgreSQL `40P01`, MySQL `1213`, SQL Server `1205`; in Spring —
      `DeadlockLoserDataAccessException` / `CannotAcquireLockException` (check the translation for your driver).
- [ ] Lock wait timeouts as the other safety net: `lock_timeout` (PostgreSQL), `innodb_lock_wait_timeout` (MySQL),
      `LOCK_TIMEOUT` (SQL Server) — different error, different handling.

## 5. Handling in the application

- [ ] A deadlock is transient → retry the **whole** transaction (link "Transaction failed — how to handle it?" in
      `5.1.1_Transactions.md`).
- [ ] Retry from outside the transaction, through the proxy — one transaction is one attempt (link scenario "ORM
      retry after a version conflict fails" in `todo_Transactions_Scenarios_ORM.md`).
- [ ] Side effects inside a retried transaction run again (link retry traps in `5.1.1_Transactions.md`).
- [ ] Few attempts with backoff; frequent deadlocks are a design bug, not something to retry away.

## 6. Prevention

- [ ] Fixed lock order: always lock rows in the same order (lowest id first); every code path must follow it.
- [ ] Short transactions: no remote calls, no user think time inside (link "Payment API called inside a transaction").
- [ ] Lock less: indexes for `UPDATE` / `DELETE` conditions; conditional single-statement updates instead of
      read-then-write.
- [ ] Lock one parent row instead of many children (a coarse lock with a clear order).
- [ ] Optimistic locking (`@Version`) instead of pessimistic locks where conflicts are rare — no waiting, so no
      cycle (link `3.5.4_Lost_Updates_and_Write_Skew_with_ORM.md`).
- [ ] MVCC read committed / snapshot reads don't lock → fewer deadlocks than read locks (link "Read committed can
      run on locks or on MVCC — which to pick?").

## 7. Diagnosis

- [ ] Where the database writes deadlock details: PostgreSQL server log (`log_lock_waits`), MySQL
      `SHOW ENGINE INNODB STATUS`, SQL Server deadlock graph (system_health session).
- [ ] Reading a deadlock report: which statements, which rows / indexes, which lock modes.
- [ ] Live lock waits: `pg_locks` + `pg_stat_activity` (blocking pid), MySQL `performance_schema.data_locks`.
- [ ] Monitoring: deadlock counter (`pg_stat_database.deadlocks`), lock wait time; alert on a rising rate.
- [ ] Reproducing: two sessions side by side, step by step; then an IT with two threads and a latch.

## 8. Practice

- [ ] Practice project: opposite transfers on PostgreSQL in Testcontainers → see `40P01`, the victim, the log entry;
      then fix with a fixed lock order and show zero deadlocks under the same load.
- [ ] Same with Hibernate: two entities flushed in different order.

---

## Covered elsewhere

- Deadlock as a scenario card → `todo_Transactions_Scenarios_DB.md` ("Opposite transfers fail with deadlocks").
- Diagnostics of transactions in general → `todo_Transactions_Diagnostics.md`.
- Distributed deadlocks and in-doubt locks in 2PC → `2_Distributed_Transactions/todo_Distributed_Transactions.md` §2.

## Not here

- Java thread deadlocks (`synchronized`, `ReentrantLock`) → Java decks (separate repo).
- Locks used for correctness (lost update, write skew, phantoms) → `3.5.3`, `3.5.4`.
