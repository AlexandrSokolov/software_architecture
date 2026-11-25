# 05_Consistency / 1_DB_Consistency — pitfalls from practice: idempotent insert with JPA

**Question it answers:** The same event arrives twice (redelivery, retry after a timeout, two at once). How does
the database stop the duplicate — and why does JPA make that harder than it looks?

**Source:** `developer-pitfalls.md` (draft v4) §13.3–13.5, §13.9, §2.14; login-tracking assessment
(track-login-service: `LoginEventEntity`, `LoginTrackingService`, `LoginEventRepositoryIT`).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. `save()` with an assigned id merges

- [ ] **Pitfall:** the id is set in code (`messageId` from the event) → Spring Data treats the entity as existing →
      `merge` → SELECT + **UPDATE**: a duplicate silently overwrites the stored row.
- [ ] **How `isNew` is decided** — `Persistable#isNew()`, else a wrapper-typed `@Version` (null = new), else a
      null id.
- [ ] **Do:** implement `Persistable` (transient flag, set to false in `@PostLoad` / `@PostPersist`) → plain
      INSERT, a duplicate fails on the key. `@Version` or a surrogate key would change the schema because of the
      framework — avoid.
- [ ] **Verify:** save the same id twice with different data → exception **and** the stored row unchanged.
      Did: `rejectsDuplicateWithoutOverwritingTheStoredRow`.

## 2. Catching the duplicate

- [ ] **Catch `DataIntegrityViolationException` outside any transaction** — a failed constraint marks the
      transaction rollback-only; inside an outer transaction the INSERT moves to its commit and the catch never
      fires (500 instead of 200).
      Did: the service is deliberately NOT `@Transactional`; `save()` runs in its own transaction.
- [ ] **Only the primary key means "duplicate"** — check the constraint name
      (Hibernate `ConstraintViolationException#getConstraintName()`); name the key in the migration
      (`pk_login_event`); any other constraint is a real error and is rethrown.
- [ ] **A duplicate answers success (200), not an error** — the caller's retry or redelivery must be safe.
- [ ] **Hibernate logs every duplicate as an SQL ERROR** before you catch it — don't silence that logger globally.
- [ ] **Alternative:** native `INSERT ... ON CONFLICT DO NOTHING` — atomic, no exception, returns 0/1, but
      database-specific SQL in the repository. Decide and write down why.

## 3. Write-behind and flush

- [ ] **`persist` queues the INSERT** — flushed at commit or before a query that needs it (`FlushMode.AUTO`).
- [ ] **A repository call without an outer transaction commits at its end** — errors surface from that call;
      `saveAndFlush` matters only inside an outer transaction.
      Link: `todo_Transactions_Scenarios_ORM.md` → "Duplicate username not caught by try/catch".

## 4. Concurrent duplicates

- [ ] **Two inserts with the same key at once** — the second waits for the first transaction, then fails with
      23505 (unique violation) → caught → 200. Correct; costs a dead tuple and error lines in both logs.
- [ ] **Link:** "Same username registered twice" in `todo_Transactions_Scenarios_DB.md` — the same mechanism
      (unique index checked at insert time).

## 5. Which line of defense stopped it

- [ ] **Entity validation runs at flush** — `@NotNull` violation surfaces as `TransactionSystemException` with root
      cause `jakarta.validation.ConstraintViolationException`; no SQL is sent.
- [ ] **Database constraint** — `DataIntegrityViolationException` (root: `org.hibernate.exception` …).
      Did: `rejectsInvalidEntityBeforeTheDatabase` asserts the root cause, not just "it failed".

---

## Covered elsewhere

- Idempotent consumer as a cross-system pattern (processed-message table, inbox) →
  `3_Cross-System_Consistency/todo_Cross-System_Consistency.md` §4.
- Isolation, lost updates, write skew → `todo_Transactions_Scenarios_DB.md`, `todo_Transactions_Scenarios_ORM.md`.
