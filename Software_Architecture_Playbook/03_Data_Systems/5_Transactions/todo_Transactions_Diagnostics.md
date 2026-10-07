### Transaction snippets — what's in this file?
<details><summary>Show answer</summary>

- **SQL scripts**
  - [#1 Placing an order](#transaction-snippet-1--whats-wrong)
  - [#2 Going off call](#transaction-snippet-2--whats-wrong)
  - [#3 Booking a room](#transaction-snippet-3--whats-wrong)
  - [#4 Transferring money](#transaction-snippet-4--whats-wrong)
- **Entity updates**
  - [#5 Counting likes](#transaction-snippet-5--whats-wrong)
  - [#6 Saving an edit from the browser](#transaction-snippet-6--whats-wrong)
- **Bulk updates**
  - [#7 Publishing an article](#transaction-snippet-7--whats-wrong)
- **Locking**
  - [#8 Moving a game figure](#transaction-snippet-8--whats-wrong)
- **Retries**
  - [#9 Retrying a transfer](#transaction-snippet-9--whats-wrong)
- **Rules across rows**
  - [#10 Going off call, ORM version](#transaction-snippet-10--whats-wrong)
  - [#11 Registering a username](#transaction-snippet-11--whats-wrong)
- **Transaction boundaries**
  - [#12 Placing an order with payment](#transaction-snippet-12--whats-wrong)
  - [#13 Checkout with a seat reservation](#transaction-snippet-13--whats-wrong)

Read each snippet fully before you open the answer: most have more than one problem.

</details>

### Transaction snippet #1 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```sql
-- place an order for product 42; default isolation level (read committed)
BEGIN;
SELECT stock FROM products WHERE id = 42;                 -- app reads 1
-- app: if stock > 0, go on
UPDATE products SET stock = 0 WHERE id = 42;              -- app computed 1 - 1
INSERT INTO orders (product_id, user_id) VALUES (42, 7);
COMMIT;
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Lost update: two buyers both read 1, both write 0, and both insert an order. The last item is sold twice, and stock
  shows 0, so nothing looks wrong afterwards.

**Not guaranteed:**
- On PostgreSQL at repeatable read, the second `UPDATE` would fail with a serialization error. At read committed (the
  default), and on MySQL at repeatable read, it goes through. Don't rely on the isolation level to catch it.

**Minimal fix:** `SELECT stock ... FOR UPDATE`. It works, but it keeps the check in app code, and every path that
sells must remember the lock.

**Rewrite:** check and write in one statement.

```sql
BEGIN;
UPDATE products SET stock = stock - 1 WHERE id = 42 AND stock > 0; -- 0 rows → sold out, ROLLBACK
INSERT INTO orders (product_id, user_id) VALUES (42, 7);
COMMIT;
```

</details>

</details>

### Transaction snippet #2 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```sql
-- Alice goes off call; the rule: at least one doctor stays on call
BEGIN;
SELECT COUNT(*) FROM doctors WHERE on_call AND shift_id = 1234;              -- 2
SELECT * FROM doctors WHERE name = 'Alice' AND shift_id = 1234 FOR UPDATE;
UPDATE doctors SET on_call = false WHERE name = 'Alice' AND shift_id = 1234;
COMMIT;
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Write skew: the lock is on the row being written (Alice's), not on the rows the count read. Bob's transaction locks
  Bob's row. Both counts saw 2, both commit, and no one is on call.

**Doesn't belong:**
- The `FOR UPDATE` on Alice's row: the `UPDATE` locks that row anyway, so the extra statement buys nothing.

**Not guaranteed:**
- At serializable, the database would abort one of the two. At read committed and repeatable read, both commit.

**Minimal fix:** adding `FOR UPDATE` to the `COUNT(*)` looks like the fix, but PostgreSQL rejects it: `FOR UPDATE` is
not allowed with aggregate functions.

**Rewrite:** lock every row the check counts, and count in the app.

```sql
BEGIN;
SELECT id FROM doctors WHERE on_call AND shift_id = 1234 FOR UPDATE; -- app counts the rows; fewer than 2 → ROLLBACK
UPDATE doctors SET on_call = false WHERE name = 'Alice' AND shift_id = 1234;
COMMIT;
```

Or run at serializable and retry on abort.

</details>

</details>

### Transaction snippet #3 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```sql
-- book room 1 from 12:00 to 13:00
BEGIN;
SELECT * FROM bookings
WHERE room_id = 1 AND start_time < '13:00' AND end_time > '12:00'
FOR UPDATE;                                                         -- 0 rows
INSERT INTO bookings (room_id, start_time, end_time) VALUES (1, '12:00', '13:00');
COMMIT;
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Phantom: the check returns no rows, so `FOR UPDATE` locks nothing. Two transactions both see 0 and both insert.

**Doesn't belong:**
- The `FOR UPDATE` itself: it protects nothing here and makes the code look safe.

**Not guaranteed:**
- Locking the room's row (the minimal fix below) is safe at read committed. At repeatable read, the check may still
  read a snapshot taken before the other booking committed.

**Minimal fix:** lock the room's row first: `SELECT id FROM rooms WHERE id = 1 FOR UPDATE;`. It works, but it also
blocks bookings that don't overlap, and every path that books must take the lock.

**Rewrite (PostgreSQL):** let the database reject overlaps, and drop the check.

```sql
CREATE EXTENSION btree_gist;                                                   -- needed for room_id WITH =
ALTER TABLE bookings ADD EXCLUDE USING gist (room_id WITH =, during WITH &&); -- during: a tstzrange column
INSERT INTO bookings (room_id, during) VALUES (1, '[2025-06-02 12:00, 2025-06-02 13:00)'); -- overlap → error
```

On other databases: serializable with retry.

</details>

</details>

### Transaction snippet #4 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```sql
-- transfer(:from, :to, 100)
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = :from;
UPDATE accounts SET balance = balance + 100 WHERE id = :to;
COMMIT;
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Deadlock: a 1→2 transfer locks row 1, then row 2; a 2→1 transfer locks row 2, then row 1. Each waits for the other,
  and the database aborts one.
- No funds check: the balance can go below 0.

**Not guaranteed:**
- The deadlock shows up only when opposite transfers overlap in time, so tests rarely catch it.

**Minimal fix:** `CHECK (balance >= 0)` on the table. It stops negative balances, but the deadlocks stay.

**Rewrite:** lock both rows in a fixed order, and make the debit conditional.

```sql
BEGIN;
SELECT id FROM accounts WHERE id = :low  FOR UPDATE;                     -- :low = the smaller of the two ids
SELECT id FROM accounts WHERE id = :high FOR UPDATE;                     -- same order everywhere: no wait cycle
UPDATE accounts SET balance = balance - 100 WHERE id = :from AND balance >= 100; -- 0 rows → ROLLBACK
UPDATE accounts SET balance = balance + 100 WHERE id = :to;
COMMIT;
```

Keep a retry on deadlock errors anyway: another code path may lock these rows in a different order.

</details>

</details>

### Transaction snippet #5 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Service
class PostService {
  @Transactional
  public synchronized void like(long postId) {
    Post p = postRepo.findById(postId).orElseThrow();
    p.setLikes(p.getLikes() + 1);
    postRepo.save(p);
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Lost update across app instances: `synchronized` locks inside one JVM. Two instances both read 42 and both write 43.
- Lost update even on one instance: the Spring proxy commits after the method returns, so the next thread enters
  `like` before the commit and reads the old value.
- One lock for all posts: likes on different posts wait for each other.

**Doesn't belong:**
- `postRepo.save(p)`: `p` is managed, and dirty checking writes it at flush anyway.

**Not guaranteed:**
- The single-instance race appears only under load, so the code passes local tests.

**Minimal fix:** move `synchronized` to a caller outside the transaction. That fixes one JVM only and breaks as soon
as a second instance runs.

**Rewrite:** let the database add 1.

```java
@Modifying
@Query("UPDATE Post p SET p.likes = p.likes + 1 WHERE p.id = :id")
void incrementLikes(@Param("id") long id);

@Transactional
public void like(long postId) {
  postRepo.incrementLikes(postId); // no Java lock needed
}
```

</details>

</details>

### Transaction snippet #6 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@PutMapping("/tickets/{id}")
@Transactional
public Ticket update(@PathVariable long id, @RequestBody TicketDto dto) { // Ticket has @Version long version
  Ticket t = ticketRepo.findById(id).orElseThrow();
  t.setTitle(dto.title());
  t.setBody(dto.body());
  t.setVersion(dto.version());                                            // "so the check uses the client's version"
  return ticketRepo.save(t);
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- The agent's version is not reliably compared. JPA says the app must not change the version field, so
  `setVersion` is out of bounds. Without a real comparison, another agent's save between the page load and this
  request gets overwritten.

**Doesn't belong:**
- `ticketRepo.save(t)`: `t` is managed; the flush writes it anyway.
- `@Transactional` on a controller, and returning the entity: keep transactions in the service layer and return a
  DTO.

**Not guaranteed:**
- What Hibernate does with a changed version field on a loaded entity is not something to build on; the spec leaves
  it out of bounds.

**Minimal fix:** delete `setVersion` and compare instead:
`if (t.getVersion() != dto.version()) throw new ResponseStatusException(HttpStatus.CONFLICT);`

**Rewrite:** the same check in a service, returning a DTO with the new version.

```java
@Transactional
public TicketDto update(long id, TicketDto dto) {
  Ticket t = ticketRepo.findById(id).orElseThrow();
  if (t.getVersion() != dto.version()) throw new StaleEditException(id); // both long; agent saw an older version
  t.setTitle(dto.title());
  t.setBody(dto.body());
  ticketRepo.flush();                                                    // the version is bumped at flush
  return TicketDto.from(t);                                              // so the DTO carries the new version
}
```

Without the `flush()`, the returned DTO would carry the old version, and the agent's next save would fail.

</details>

</details>

### Transaction snippet #7 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
// articleRepo.incrementViews:
//   @Modifying @Query("UPDATE Article a SET a.views = a.views + 1 WHERE a.id = :id")
@Transactional
public void publish(long id) {
  Article a = articleRepo.findById(id).orElseThrow(); // views = 42
  articleRepo.incrementViews(id);                      // database: 43
  a.setStatus(Status.PUBLISHED);
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- At commit, the flush writes `a`'s whole row, `views = 42` included, because `a` was loaded before the bulk update.
  The increment is lost.

**Not guaranteed:**
- With `@DynamicUpdate` on `Article`, only `status` is written and the increment survives. The same code is right or
  wrong depending on the entity mapping.

**Minimal fix:** `@Modifying(clearAutomatically = true)` alone looks right, but it makes things worse: after the
clear, `a` is detached, and `setStatus` is silently never saved. `em.refresh(a)` after the bulk update works, but
every caller must remember it.

**Rewrite:** change the entity first, then run the bulk update with flush before and clear after.

```java
// @Modifying(flushAutomatically = true, clearAutomatically = true)
@Transactional
public void publish(long id) {
  Article a = articleRepo.findById(id).orElseThrow();
  a.setStatus(Status.PUBLISHED);
  articleRepo.incrementViews(id); // flushes the status change, adds 1, then empties the persistence context
}                                 // nothing left to flush at commit: views stays 43
```

</details>

</details>

### Transaction snippet #8 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Service
class MoveService {
  public void move(long figureId, String to) {
    Figure f = lockFigure(figureId);  // call through 'this'
    rules.check(f, to);
    f.setPosition(to);
    figureRepo.save(f);
  }

  @Transactional
  public Figure lockFigure(long id) {
    return figureRepo.findWithLockById(id).orElseThrow(); // @Lock(PESSIMISTIC_WRITE)
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- `lockFigure` is called through `this`, so the proxy is skipped and its `@Transactional` does nothing.
- Even called through the proxy, its transaction would end when `lockFigure` returns, before the check and the write.
  The lock would be gone, and two players could still move the same figure.
- `figureRepo.save(f)` runs in its own transaction and merges a detached `f`, writing every column with values read
  earlier.

**Not guaranteed:**
- Without a transaction, the locked query may throw `TransactionRequiredException`, or run and release the lock at
  once, depending on how the repository is set up. Neither protects the move.

**Minimal fix:** add `@Transactional` to `move`. Now the locked read, the check and the write share one transaction,
and the lock holds until commit. It works, but it leaves a misleading `@Transactional` on `lockFigure` and a
redundant `save`.

**Rewrite:**

```java
@Transactional
public void move(long figureId, String to) {
  Figure f = figureRepo.findWithLockById(figureId).orElseThrow(); // SELECT ... FOR UPDATE, held until commit
  rules.check(f, to);
  f.setPosition(to);                                              // managed: written at flush, no save()
}
```

</details>

</details>

### Transaction snippet #9 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Transactional
public void transfer(long from, long to, BigDecimal amount) { // Account has @Version
  for (int i = 0; i < 3; i++) {
    try {
      Account a = accountRepo.findById(from).orElseThrow();
      Account b = accountRepo.findById(to).orElseThrow();
      a.withdraw(amount);
      b.deposit(amount);
      return;
    } catch (ObjectOptimisticLockingFailureException e) {
      // try again
    }
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- The catch never fires in the normal case: the version check runs at flush, at commit, after `return`.
- If it did fire (an earlier flush inside the loop), the transaction is already marked for rollback, and `findById`
  returns the same stale entities from the persistence context. The retry repeats the conflict.
- Opposite transfers can deadlock at flush: one writes A, then B; the other writes B, then A.

**Doesn't belong:**
- The retry loop inside the transaction: one transaction is one attempt.

**Not guaranteed:**
- The order in which Hibernate flushes the two `UPDATE`s is not set by this code. The setting
  `hibernate.order_updates=true` sorts them by id, which removes this deadlock.

**Minimal fix:** none inside this method; the loop has to move out.

**Rewrite:** a single attempt in the transaction, and the loop in another bean.

```java
// AccountTx: @Transactional public void transferOnce(long from, long to, BigDecimal amount) { ...the body... }

public void transfer(long from, long to, BigDecimal amount) { // TransferService: another bean, no transaction
  for (int attempt = 1; ; attempt++) {
    try { accountTx.transferOnce(from, to, amount); return; }   // through the proxy: a new transaction each time
    catch (ObjectOptimisticLockingFailureException e) { if (attempt == 3) throw e; }
  }
}
```

</details>

</details>

### Transaction snippet #10 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Transactional
public void goOffCall(long shiftId, long doctorId) {               // Doctor has @Version
  long onCall = doctorRepo.countByShiftIdAndOnCallTrue(shiftId);
  if (onCall < 2) throw new IllegalStateException("last doctor");
  Doctor d = doctorRepo.findById(doctorId).orElseThrow();
  d.setOnCall(false);
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- Write skew: Alice and Bob both count 2 and each takes their own row off call. `@Version` doesn't help: it checks
  only the row being written, and they write different rows.

**Not guaranteed:**
- At serializable, PostgreSQL would abort one of them. At the default level, both commit.

**Minimal fix:** `@Lock(PESSIMISTIC_WRITE)` on `countByShiftIdAndOnCallTrue` looks right, but PostgreSQL rejects
`FOR UPDATE` with aggregate functions.

**Rewrite:** lock the rows the check counts, and count in Java.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Doctor> findByShiftIdAndOnCallTrue(long shiftId);

@Transactional
public void goOffCall(long shiftId, long doctorId) {
  List<Doctor> onCall = doctorRepo.findByShiftIdAndOnCallTrue(shiftId); // locks every row the check counts
  if (onCall.size() < 2) throw new IllegalStateException("last doctor");
  doctorRepo.findById(doctorId).orElseThrow().setOnCall(false);
}
```

Optimistic alternative: `em.find(Shift.class, shiftId, LockModeType.OPTIMISTIC_FORCE_INCREMENT)` at the start, so
both transactions write the shift's version and the second one fails.

</details>

</details>

### Transaction snippet #11 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Transactional
public User register(String name) {
  if (userRepo.existsByUsername(name)) throw new NameTakenException(name);
  try {
    return userRepo.save(new User(name));  // User has @Column(unique = true) on username
  } catch (DataIntegrityViolationException e) {
    throw new NameTakenException(name);
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- `existsByUsername` then `save`: two registrations both see the name as free. The row doesn't exist yet, so there
  is nothing to lock.
- The catch may never fire: Hibernate can delay the `INSERT` until flush at commit, outside the try. The caller then
  gets a raw `DataIntegrityViolationException` instead of `NameTakenException`.

**Doesn't belong:**
- Nothing, if `existsByUsername` is meant as a fast path for a friendly message. It is not the protection.

**Not guaranteed:**
- With `IDENTITY` ids, `save` inserts at once and the catch works; with sequence ids it usually doesn't. Same code,
  different behavior depending on the id strategy.
- `@Column(unique = true)` only feeds schema generation. If the schema comes from migrations, the constraint may not
  exist at all, and duplicates go through silently.

**Minimal fix:** `saveAndFlush`, so the `INSERT` runs inside the try.

**Rewrite:** a real unique index (on `lower(username)` for case-insensitive names) plus `saveAndFlush`.

```java
@Transactional
public User register(String name) {
  try {
    return userRepo.saveAndFlush(new User(name)); // INSERT now; the unique index decides
  } catch (DataIntegrityViolationException e) {
    throw new NameTakenException(name);           // the transaction rolls back; nothing else may follow
  }
}
```

</details>

</details>

### Transaction snippet #12 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Transactional
public void placeOrder(Order o) throws PaymentException {
  orderRepo.save(o);
  mailer.sendConfirmation(o);   // sends right away
  paymentClient.charge(o);      // HTTP call; throws the checked PaymentException
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- `PaymentException` is checked, and by default Spring rolls back only on unchecked exceptions. A failed payment
  still commits the order.
- The email goes out before the payment, whatever happens next. A rollback can't recall it.
- The HTTP call runs inside the transaction and holds a database connection for its whole duration.
- No idempotency key: a retry after a timeout may charge twice.

**Doesn't belong:**
- The email inside the transaction.

**Not guaranteed:**
- After a timeout, you don't know whether the charge happened.

**Minimal fix:** `@Transactional(rollbackFor = PaymentException.class)`. The order no longer commits without payment,
but the early email, the held connection and the double charge remain.

**Rewrite:** short transactions around the call, and the email after commit.

```java
public void placeOrder(Order o) {                     // no transaction here
  long id = orderTx.createPending(o);                 // short transaction: status PENDING
  try {
    paymentClient.charge(id, o.getTotal());           // order id doubles as the idempotency key
    orderTx.markPaid(id);                             // short transaction; publishes an OrderPaid event
  } catch (PaymentException e) {
    orderTx.markFailed(id);
  }
}
// @TransactionalEventListener(phase = AFTER_COMMIT) on OrderPaid → send the email only after markPaid commits
```

A job must find orders stuck in PENDING after a crash and finish them.

</details>

</details>

### Transaction snippet #13 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
@Service
class CheckoutService {
  @Transactional
  public void checkout(Cart cart) {
    orderService.create(cart);
    seatService.reserve(cart.seatId()); // reserve: @Transactional(isolation = Isolation.SERIALIZABLE)
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- `reserve` joins the transaction `checkout` already started at the default level. Spring applies an isolation level
  only when it starts a new transaction, so `SERIALIZABLE` is silently ignored and the seat check can double-book.
- No retry: if it did run at serializable, an abort would need a retry around the whole transaction.

**Not guaranteed:**
- If `reserve` is ever called with no transaction around it, it does run at serializable. The same method gives
  different protection depending on the caller. Spring can be set to reject this mismatch
  (`validateExistingTransaction`), but that is off by default.

**Minimal fix:** `Propagation.REQUIRES_NEW` on `reserve`. It now runs at serializable, but in a separate transaction:
the order can commit while the reservation rolls back, or the other way round.

**Rewrite:** set the level on the method that starts the transaction, and retry from outside it.

```java
@Transactional(isolation = Isolation.SERIALIZABLE) // the method that starts the transaction sets the level
public void checkout(Cart cart) {
  orderService.create(cart);
  seatService.reserve(cart.seatId());
}
// caller in another bean: retry checkout() on serialization errors
```

Every other transaction that touches seats must run at serializable too, or it is not checked.

</details>

</details>
