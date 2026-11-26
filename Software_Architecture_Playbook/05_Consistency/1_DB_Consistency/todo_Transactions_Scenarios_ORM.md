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

### Transaction snippet #14 — what's wrong?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show code</summary>

```java
interface PageRepository extends JpaRepository<Page, Long> {
  @Query("UPDATE Page p SET p.views = p.views + 1 WHERE p.id = :id")
  void incrementViews(@Param("id") long id);
}

@Service
class PageService {
  public void view(long id) {   // no @Transactional
    pageRepo.incrementViews(id);
  }
}
```

</details>

<details><summary>Show answer</summary>

**Defects:**
- No `@Modifying`: Spring runs the query as a read (`getResultList()` or `getSingleResult()`), and Hibernate refuses
  to run an `UPDATE` that way. Nothing reaches the database.
- No transaction: `executeUpdate()` needs one, otherwise it throws `TransactionRequiredException`.

**Not guaranteed:**
- The second defect hides when some caller up the stack is `@Transactional`: the same method works from one place and
  fails from another.

**Minimal fix:** add `@Modifying`. The query now goes through `executeUpdate()`, and the next error is
`TransactionRequiredException`.

**Rewrite:** both pieces.

```java
@Modifying
@Query("UPDATE Page p SET p.views = p.views + 1 WHERE p.id = :id")
void incrementViews(@Param("id") long id);

@Transactional
public void view(long id) {
  pageRepo.incrementViews(id);
}
```

Once it runs, watch the persistence context: an already loaded `Page` keeps the old value
([what can go wrong](5.1.05_Lost_Updates_and_Write_Skew_with_ORM.md#atomic-writes-with-orm--what-can-go-wrong)).

</details>

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
