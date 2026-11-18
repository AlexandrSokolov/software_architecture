### Transaction scenarios (ORM) — what's covered?
<details><summary>Show answer</summary>

- **What the ORM writes**
  - [Counter misses increments](#orm-counter-misses-increments--why)
  - [Edited field reverts](#hibernate-edited-field-reverts-after-another-save--why)
  - [Merge reverts a field](#hibernate-merge-reverts-a-field-despite-dynamicupdate--why)
- **Bulk updates**
  - [Entity shows the old value](#orm-entity-shows-old-value-after-bulk-update--why)
  - [UPDATE query throws](#spring-data-update-query-throws--why)
- **Optimistic locking in practice**
  - [Version check passes anyway](#orm-rest-update-with-version-still-overwrites--why)
  - [Retry fails too](#orm-retry-after-a-version-conflict-fails--why)
- **Pessimistic locking in practice**
  - [Lock added, conflicts continue](#orm-row-lock-added-conflicts-continue--why)
  - [Locked count fails](#orm-locked-count-query-fails-on-postgresql--why)
- **Rules across rows**
  - [Doctors with versions](#orm-doctors-with-version-both-go-off-call--why)
  - [Booking double-books](#orm-booking-check-then-save-double-books--fix)
  - [Duplicate name not caught](#orm-duplicate-username-not-caught-by-trycatch--why)
- **Where the transaction really is**
  - [Partial writes stay](#spring-partial-writes-stay-after-an-exception--why)
  - [Checked exception commits](#spring-checked-exception-transaction-still-commits--why)
  - [Rule broken despite serializable](#orm-rule-broken-despite-serializable-service--why)

What Hibernate writes → how you skip it → how versions and locks really behave → rules across rows → where the
transaction starts and ends.

</details>

### ORM counter misses increments — why?
<details><summary>Show answer</summary>

```java
@Transactional
void view(long pageId) {
  Page p = pageRepo.findById(pageId).orElseThrow(); // SELECT, no lock
  p.setViews(p.getViews() + 1);                     // computed in Java
}                                                   // flush: UPDATE page SET views = 43 (a fixed value)
```

**Verdict:** dirty checking writes the value computed in Java, so two requests that read 42 both write 43. Let the
database add 1 with a JPQL bulk update:

```java
@Modifying
@Query("UPDATE Page p SET p.views = p.views + 1 WHERE p.id = :id")
void incrementViews(@Param("id") long id);
```

**Reasoning:** `@Transactional` does not protect a read → modify → write. At the default read committed level,
nothing locks or checks the row between the find and the flush.

**Trade-off:** the bulk update skips the persistence context, so an already loaded `Page` keeps the old value. When
the logic doesn't fit one statement, use `@Version` (and retry) or `PESSIMISTIC_WRITE` (and wait) instead.

</details>

### Hibernate: edited field reverts after another save — why?
<details><summary>Show answer</summary>

Ann changes a customer's `email`, Bob changes the same customer's `phone`, at the same time. Ann's email is gone.

**Verdict:** by default Hibernate's `UPDATE` sets every column, filling the unchanged ones with the values it loaded.
Bob's flush writes back the old email he read. Add `@Version`: Bob's save then fails instead of overwriting.
`@DynamicUpdate` (only changed columns in the `UPDATE`) helps only partly.

**Reasoning:** dirty checking decides whether to write a row; the default statement still writes all of its columns.

**Trade-off:** `@DynamicUpdate` fixes only edits to different fields. Two edits of the same field still lose one, and
it doesn't help with detached entities (see [merge](#hibernate-merge-reverts-a-field-despite-dynamicupdate--why)).
`@Version` covers all three.

</details>

### Hibernate merge reverts a field despite @DynamicUpdate — why?
<details><summary>Show answer</summary>

```java
Customer c = customerFromEarlierRequest; // email = 'old@x.com'
c.setPhone("555");
customerRepo.save(c);                    // merge: loads the row (email = 'ann@x.com'), copies 'old@x.com' over it
// UPDATE customer SET email = 'old@x.com', phone = '555' WHERE id = 1
```

**Verdict:** `merge()`, which Spring Data `save()` calls for an existing entity, copies every field of the detached
object onto the freshly loaded one. The stale email now differs from the database, so it counts as changed and is
written.

**Reasoning:** `@DynamicUpdate` writes only changed columns, and merge makes the stale columns look changed.

**Trade-off:** fix it with `@Version` (merge then fails on the old version), or load the entity and copy only the
fields the request actually changed. The second needs more code per endpoint.

</details>

### ORM entity shows old value after bulk update — why?
<details><summary>Show answer</summary>

```java
Page p = pageRepo.findById(1L).orElseThrow(); // views = 42, kept in the persistence context
pageRepo.incrementViews(1L);                   // bulk UPDATE: the database now has 43
p.getViews();                                  // still 42
p.setTitle("New");                             // flush writes the whole row: views = 42 again
```

**Verdict:** a bulk `UPDATE` goes straight to the database and skips the persistence context. Use
`@Modifying(clearAutomatically = true)` and load the entity again, or call `em.refresh(p)`.

**Reasoning:** Hibernate serves loaded entities from memory, and nothing tells it the row changed underneath.

**Trade-off:** clearing drops every loaded entity, including changes not yet flushed; add `flushAutomatically = true`
to save those first. Old references like `p` stay stale, so fetch fresh ones.

</details>

### Spring Data UPDATE query throws — why?
<details><summary>Show answer</summary>

**Verdict:** one of two things is missing:
- `@Modifying`: without it, Spring runs the query as a read (`getResultList()` or `getSingleResult()`), and
  Hibernate refuses to run an `UPDATE` that way.
- A transaction: `executeUpdate()` needs one, otherwise it throws `TransactionRequiredException`. Call the method
  from a `@Transactional` method.

**Reasoning:** `@Modifying` is what makes Spring call `executeUpdate()`, the JPA call that sends an `UPDATE` or
`DELETE`.

**Trade-off:** nothing to weigh here, both are required. The real cost of the bulk update is the
[stale persistence context](#orm-entity-shows-old-value-after-bulk-update--why).

</details>

### ORM REST update with @Version still overwrites — why?
<details><summary>Show answer</summary>

```java
@Transactional
void update(long id, TicketDto dto) {               // dto.version() = 5: what the agent saw
  Ticket t = ticketRepo.findById(id).orElseThrow(); // loaded now: version = 6, someone saved meanwhile
  t.setBody(dto.body());
}                                                   // UPDATE ... WHERE id = ? AND version = 6 → passes
```

**Verdict:** the check uses the version Hibernate just loaded, not the one the agent saw. Compare them yourself; then
`@Version` still guards the short gap between the find and the flush:

```java
if (t.getVersion() != dto.version()) throw new OptimisticLockException(); // stale edit → 409 Conflict
```

**Reasoning:** `@Version` protects one transaction. The agent's edit spans two requests, so the version must travel
with the data and be checked on the way back. JPA says the app must not change the version field, so copying
`dto.version()` into it is not the fix.

**Trade-off:** one extra comparison per update endpoint, and forgetting it on one endpoint reopens the hole.

</details>

### ORM retry after a version conflict fails — why?
<details><summary>Show answer</summary>

```java
@Transactional
void rename(long id, String title) {
  try {
    ticketRepo.findById(id).orElseThrow().setTitle(title);
  } catch (OptimisticLockException e) { // never caught: the version check runs at flush, after the method returns
    rename(id, title);                  // and a call through 'this' would not start a new transaction anyway
  }
}
```

**Verdict:** retry from outside the transaction. A method on another bean calls `rename` through the Spring proxy, so
each attempt gets a new transaction and a fresh persistence context:

```java
for (int attempt = 1; ; attempt++) {
  try { ticketService.rename(id, title); return; }
  catch (ObjectOptimisticLockingFailureException e) { if (attempt == 3) throw e; }
}
```

**Reasoning:** the conflict shows up at flush, usually at commit. By then the transaction is marked for rollback and
its loaded entities are stale. One transaction is one attempt.

**Trade-off:** each retry redoes all work in the transaction, side effects included. Keep attempts few and side
effects after commit.

</details>

### ORM row lock added, conflicts continue — why?
<details><summary>Show answer</summary>

**Verdict:** two usual causes:
- The locked read and the write run in different transactions, e.g. a helper `@Transactional` method does the read
  and the write happens after it returns. The lock is released in between.
- Another code path loads the same row with a plain `findById`. A plain read doesn't wait for the lock.

Do the read, the check and the write in one `@Transactional` method, and make every path that changes the row use the
locked read.

**Reasoning:** `FOR UPDATE` only makes other locking reads and writes wait, and only until its transaction ends.

**Trade-off:** more waiting under load. When one transaction locks several rows, lock them in a fixed order, or
deadlocks follow.

</details>

### ORM locked count query fails on PostgreSQL — why?
<details><summary>Show answer</summary>

**Verdict:** `@Lock(PESSIMISTIC_WRITE)` on a `countBy...` method makes Hibernate send `SELECT COUNT(*) ... FOR UPDATE`,
and PostgreSQL rejects `FOR UPDATE` with aggregate functions. Fetch the rows with the lock and count in Java, or lock
one parent row instead.

**Reasoning:** a lock attaches to rows. A count returns a number, not rows.

**Trade-off:** fetching rows costs more than counting when there are many. A parent-row lock is cheaper, but coarser:
it blocks every change under that parent.

</details>

### ORM doctors with @Version both go off call — why?
<details><summary>Show answer</summary>

**Verdict:** `@Version` guards only the row being written. Alice and Bob write different rows, so both checks pass.
Make them conflict on one row: force a version bump on the parent `Shift` at commit,

```java
em.find(Shift.class, shiftId, LockModeType.OPTIMISTIC_FORCE_INCREMENT); // UPDATE shift SET version = ? at commit
```

or lock the doctor rows the check reads (`PESSIMISTIC_WRITE`), or run at serializable.

**Reasoning:** write skew conflicts in the rows that were read, and versions only see rows that were written. The
force increment turns "read the shift" into "write the shift", so the second commit gets 0 rows and fails.

**Trade-off:** every path that changes the shift's doctors must bump the shift. Plain `OPTIMISTIC` (check without
bump) is not enough: its check near the end can race.

</details>

### ORM booking check then save double-books — fix?
<details><summary>Show answer</summary>

```java
@Transactional
void book(long roomId, Instant start, Instant end) {
  if (bookingRepo.existsOverlap(roomId, start, end)) throw new RoomTakenException(); // no rows → nothing to lock
  bookingRepo.save(new Booking(roomId, start, end));                                 // a row the check never saw
}
```

**Verdict:** a phantom: the conflicting row doesn't exist yet, so `@Lock` on the check locks nothing. Three fixes:
- Lock the `Room` row first (`@Lock(PESSIMISTIC_WRITE)` on a room find), then check and save. Safe at read committed.
- A database constraint (on PostgreSQL, an exclusion constraint on room and time range).
- Serializable isolation, with a retry.

**Reasoning:** each fix gives the conflict something real to hit: an existing row to lock, a constraint the database
checks on insert, or the database tracking what the check read.

**Trade-off:** the room lock also blocks bookings that don't overlap. The constraint needs database-specific DDL and
error handling. Serializable needs retries.

</details>

### ORM duplicate username not caught by try/catch — why?
<details><summary>Show answer</summary>

```java
@Transactional
void register(String name) {
  try {
    userRepo.save(new User(name));          // INSERT may wait until flush, at commit
  } catch (DataIntegrityViolationException e) {
    throw new NameTakenException(name);     // never reached
  }
}                                           // commit: the INSERT fails here, outside the try
```

**Verdict:** Hibernate may delay the `INSERT` until flush (with sequence-generated ids it usually does), so the
unique violation surfaces at commit, after the try block. Use `saveAndFlush`, so the `INSERT` runs inside the try.

**Reasoning:** `save` only hands the entity to the persistence context; the SQL runs at flush.

**Trade-off:** after the violation the transaction is unusable and must roll back, so nothing else can follow it in
the same transaction. Also, the constraint must exist in the database: `@Column(unique = true)` only feeds schema
generation.

</details>

### Spring: partial writes stay after an exception — why?
<details><summary>Show answer</summary>

```java
@Service
class OrderService {
  void place(Order o) {
    saveAll(o);                    // call through 'this': the proxy is skipped, no transaction
  }

  @Transactional
  void saveAll(Order o) {
    orderRepo.save(o);             // commits on its own
    lineRepo.saveAll(o.lines());   // throws → the order row stays
  }
}
```

**Verdict:** `@Transactional` is applied by the Spring proxy around the bean, and a call through `this` skips it. With
no transaction, each repository call commits on its own. Put `@Transactional` on the method called from outside
(`place`), or call `saveAll` through another bean.

**Reasoning:** the annotation does nothing by itself; only a call that goes through the proxy starts a transaction.

**Trade-off:** nothing to weigh, it's a wiring rule. The same trap hits other proxy-based annotations, like retries.

</details>

### Spring: checked exception, transaction still commits — why?
<details><summary>Show answer</summary>

**Verdict:** by default Spring rolls back only on unchecked exceptions (`RuntimeException`) and `Error`. A checked
exception passes through, and the transaction commits whatever was written. Use
`@Transactional(rollbackFor = Exception.class)` or throw an unchecked exception.

**Reasoning:** the default follows the old EJB convention: checked exceptions were seen as business outcomes, not
failures.

**Trade-off:** `rollbackFor = Exception.class` everywhere is simple but easy to forget on one method. Some teams keep
the default and wrap checked exceptions in unchecked ones.

</details>

### ORM rule broken despite serializable service — why?
<details><summary>Show answer</summary>

**Verdict:** a batch job or another service changes the same data at the default read committed. PostgreSQL's
serializable guarantee covers only transactions that run at serializable, so the job's writes are not checked. Run
every transaction that touches the rule at serializable, or protect the rule with a lock or constraint that every
path hits.

**Reasoning:** the database looks for conflicts only among serializable transactions; others are not watched.

**Trade-off:** at serializable the batch job must retry too, and long batch transactions abort often. Split the job
into small transactions.

</details>
