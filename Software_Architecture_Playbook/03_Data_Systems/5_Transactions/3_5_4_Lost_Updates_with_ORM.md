### ORM entity update — what are the stages?
<details><summary>Show answer</summary>

```java
@Transactional
void rename(long id, String name) {
  User u = em.find(User.class, id); // 1. fetch: plain SELECT, no lock; Hibernate keeps a copy of what it loaded
  u.setName(name);                  // 2. modify: changes the Java object only, no SQL
}                                   // 3. flush: compares u with the copy → UPDATE with every column, values from Java
                                    // 4. commit: transaction ends, u becomes detached
```

Flush runs at commit, or earlier before a query that needs the changes.

The gap: nothing guards the row between fetch and flush, and flush writes fixed values computed in Java — the
[ORM trap](#orm-trap--how-do-updates-get-lost). Each fix hooks into a stage:

- **Fetch — lock it:** [`PESSIMISTIC_WRITE`](#explicit-locking-with-orm--how) adds `FOR UPDATE`.
- **Flush — check it:** [`@Version`](#compare-and-set-with-orm--how) adds `AND version = ?`;
  [versionless](#hibernate-versionless-optimistic-locking--how) adds the old values.
- **Flush — narrow it:** [`@DynamicUpdate`](#hibernate-edits-to-different-fields-lost--fix) writes only the changed
  columns, which fixes only the different-fields case.
- **Flush or commit — the database catches it:** a
  [higher isolation level](#automatic-lost-update-detection-with-orm--how).
- **Skip fetch and modify:** a [bulk `UPDATE`](#atomic-writes-with-orm--how), where the database computes the new
  value.

Fetch, change in memory, flush, commit: a fix either locks the fetch, checks the flush, or skips the fetch.

</details>

### ORM trap — how do updates get lost?
<details><summary>Show answer</summary>

- **Every entity update is a read → modify → write in app code.**

    ```java
      @Transactional
      void increment() {
        Counter c = em.find(Counter.class, "foo"); // read: 42
        c.setValue(c.getValue() + 1);              // modify, in Java
      }                                            // commit: UPDATE counter SET value = 43 (a fixed value, not value + 1)
    ```

  Two transactions both read 42 and both write 43. `@Transactional` does not help: by default nothing locks the row
  or checks it between the read and the write. No error, one increment gone.

- **Hibernate writes the whole row.** Its `UPDATE` sets every column, and fills the unchanged ones with the values it
  read. So even changes to different fields lose each other:

    ```sql
      T1: UPDATE users SET name = 'Ann', email = 'old@x.com' WHERE id = 1;
      T2: UPDATE users SET name = 'Bob', email = 'new@x.com' WHERE id = 1; -- T2 only changed email; 'Bob' is the old
                                                                           -- name it read, so T1's 'Ann' is gone
    ```

</details>

### Hibernate overwrites untouched columns — fix?
<details><summary>Show answer</summary>

Cause: [Hibernate writes the whole row](#orm-trap--how-do-updates-get-lost).
Fix: `@DynamicUpdate` on the entity. Hibernate then builds each `UPDATE` with only the changed columns.

```java
@Entity
@DynamicUpdate // org.hibernate.annotations
class User {
  @Id Long id;
  String name;
  String email;
}
```

```sql
T1: UPDATE users SET name = 'Ann' WHERE id = 1;
T2: UPDATE users SET email = 'new@x.com' WHERE id = 1; -- name is not touched, 'Ann' stays
```

</details>

### Hibernate @DynamicUpdate — when does it fail?
<details><summary>Show answer</summary>

- **Both transactions change the same field.** `@DynamicUpdate` changes which columns are written, not how the value
  is computed. The counter from the [ORM trap](#orm-trap--how-do-updates-get-lost) still writes a fixed value:

    ```sql
      T1: UPDATE counter SET value = 43 WHERE key = 'foo';
      T2: UPDATE counter SET value = 43 WHERE key = 'foo'; -- one increment still lost
    ```

- **The entity is detached.** `merge()`, which Spring Data `save()` also calls for an existing entity, loads the row
  and copies every field of the detached object onto it. Fields holding old values now differ from the database, so
  they count as changed:

    ```java
      User u = userFromEarlierRequest;  // name = 'Old'
      u.setEmail("new@x.com");
      em.merge(u);                      // database has name = 'Ann' (T1 changed it meanwhile); 'Old' is copied over it
      // UPDATE users SET name = 'Old', email = 'new@x.com' WHERE id = 1 -- 'Ann' is gone
    ```

</details>

### Atomic writes with ORM — how?
<details><summary>Show answer</summary>

Normal ORM path (entity state tracking, also called dirty checking): Hibernate keeps a copy of each loaded entity.
At commit it compares the entity with that copy and sends an `UPDATE` if something changed. The new value was computed
in Java, so the `UPDATE` carries a fixed value (`SET value = 43`) — the [ORM trap](#orm-trap--how-do-updates-get-lost).

Fix: skip the entity and send the [atomic write](#atomic-write-operations---how) as a JPQL bulk `UPDATE`.
No entity is loaded. Hibernate turns the JPQL into one SQL statement, and the database computes `value + 1` itself:

```java
interface CounterRepository extends JpaRepository<Counter, String> {
  @Modifying
  @Query("UPDATE Counter c SET c.value = c.value + 1 WHERE c.key = :key")
  void increment(@Param("key") String key);
}
// SQL sent: UPDATE counter SET value = value + 1 WHERE key = 'foo'
```

What `@Modifying` changes: by default Spring runs a `@Query` as a read, through JPA's `getResultList()` or
`getSingleResult()`. Hibernate refuses to run an `UPDATE` that way and throws, so nothing reaches the database.
`@Modifying` makes Spring call JPA's `executeUpdate()` instead, which sends the `UPDATE`.

`executeUpdate()` needs an open transaction, otherwise it throws `TransactionRequiredException`.
So call the repository method from a `@Transactional` method.

</details>

### Atomic writes with ORM — what can go wrong?
<details><summary>Show answer</summary>

Hibernate keeps every entity loaded in the current transaction in memory (the persistence context). A
[bulk `UPDATE`](#atomic-writes-with-orm--how) goes straight to the database and does not touch that copy:

```java
Counter c = em.find(Counter.class, "foo"); // 42, kept in memory
repo.increment("foo");                      // database: 43
c.getValue();                               // still 42
```

Worse: if you then change any other field of `c`, Hibernate
[writes the whole row back](#orm-trap--how-do-updates-get-lost), `value = 42` included (default, unless
`@DynamicUpdate`). The increment is lost again.

Fix:
- `em.refresh(c)` — reload this one entity from the database.
- `@Modifying(clearAutomatically = true)` — empty the persistence context after the update, so the next `find`
  reads from the database. The old `c` still holds 42, so get a fresh one with `find`. Add
  `flushAutomatically = true` so pending changes are saved before the context is emptied.

</details>

### Explicit locking with ORM — how?
<details><summary>Show answer</summary>

Without a lock, the read is a plain `SELECT`.
Two players' transactions both read the robot at `b3`, both check their move against `b3`, and both write.
Each check passed against a position that was already out of date, and one move overwrites the other.

Fix: [explicit locking](#explicit-locking--how), the pessimistic way. `@Lock(LockModeType.PESSIMISTIC_WRITE)` makes
Hibernate add `FOR UPDATE` to the read:

```java
interface FigureRepository extends JpaRepository<Figure, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Figure> findByNameAndGameId(String name, long gameId);
}
// SQL sent: SELECT ... FROM figure WHERE name = 'robot' AND game_id = 222 FOR UPDATE

@Transactional
void move(long gameId, String to) {
  Figure f = repo.findByNameAndGameId("robot", gameId).orElseThrow(); // row locked
  // check that the move is valid
  f.setPosition(to);
}                                                                     // commit: UPDATE, lock released
```

What it fixes: the second transaction now waits at its own locked read until the first commits.
Then it reads the new position and checks its move against that.
A path that reads without the lock does not wait, so [every path must lock](#explicit-locking--what-can-go-wrong).

The lock lives until the transaction ends, so the read, the check and the write must run in one `@Transactional` method.
If the read ran in its own transaction, the lock would be released before the write.

`@Lock` only passes JPA's lock mode to the query. Without Spring Data you pass it yourself:
`em.find(Figure.class, id, LockModeType.PESSIMISTIC_WRITE)`.

</details>

### Compare-and-set with ORM — how?
<details><summary>Show answer</summary>

Optimistic locking: add a version field, and Hibernate turns every entity update into a
[compare-and-set](#compare-and-set--how) on it.

```java
@Entity
class WikiPage {
  @Id Long id;
  String content;
  @Version int version; // Hibernate checks and increments it
}
```

```sql
UPDATE wiki_page SET content = ?, version = 6 WHERE id = 1234 AND version = 5;
```

0 rows updated → Hibernate throws `OptimisticLockException`, which Spring wraps as
`ObjectOptimisticLockingFailureException`. Catch it and retry the whole transaction: read again, change again, save.
The retry must wrap the `@Transactional` method, because the failed transaction is already rolled back.

</details>

### Hibernate versionless optimistic locking — how?
<details><summary>Show answer</summary>

No version column. Hibernate does a [compare-and-set](#compare-and-set--how) on the old values themselves:
it puts them into the `UPDATE`'s `WHERE`.

```java
@Entity
@DynamicUpdate                                      // needed: the SQL depends on which columns changed
@OptimisticLocking(type = OptimisticLockType.DIRTY) // org.hibernate.annotations
class WikiPage {
  @Id Long id;
  String content;                                   // no @Version field
}
// UPDATE wiki_page SET content = 'new' WHERE id = 1234 AND content = 'old'
```

- `DIRTY` — the old values of the changed columns go into the `WHERE`.
- `ALL` — the old values of every column go there, so any concurrent change to the row is a conflict.

0 rows updated → the same exception as [with `@Version`](#compare-and-set-with-orm--how), and the same retry.
The old values come from what Hibernate loaded in the current transaction, which
[limits where it works](#hibernate-optimistic-locking--version-or-versionless).

</details>

### Hibernate optimistic locking — version or versionless?
<details><summary>Show answer</summary>

Default to `@Version`.

- **The check value can travel.** The client gets version 5 with the page and sends it back with the save, so the
  save checks against 5 even in a new request. Versionless takes the old values from what it loaded in the current
  transaction. A save in a new request reloads the entity, gets the content as it is now, the check passes, and the
  update is lost. So versionless works only when the read and the write run in one transaction.
- **Standard:** `@Version` is JPA; versionless is Hibernate-only.

Pick versionless when:
- the schema is legacy and you can't add a version column;
- you want edits to different fields to both succeed. `DIRTY` checks only the changed columns, while a version
  rejects any concurrent change to the row.

</details>

### Automatic lost update detection with ORM — how?
<details><summary>Show answer</summary>

The entity code stays as it is. Raise the isolation level so the database does the
[detection](#automatic-lost-update-detection--how):

```java
@Transactional(isolation = Isolation.REPEATABLE_READ) // PostgreSQL detects lost updates at this level
void increment() {
  Counter c = em.find(Counter.class, "foo");
  c.setValue(c.getValue() + 1); // plain ORM code, unchanged
}
```

When the database aborts with a serialization error (SQL state `40001`), retry the whole transaction, the same way as
[with `@Version`](#compare-and-set-with-orm--how). Spring maps `40001` to `CannotAcquireLockException`, but behind JPA
a failed commit has also come out as `JpaSystemException`, so check what your version throws. Works only where the
database detects lost updates at that level, so [not on MySQL](#automatic-lost-update-detection--which-databases).

</details>