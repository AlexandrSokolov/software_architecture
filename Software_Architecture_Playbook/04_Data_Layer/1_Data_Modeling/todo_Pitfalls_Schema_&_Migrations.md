# 04_Data_Layer / 1_Data_Modeling — pitfalls from practice: schema and migrations

**Question it answers:** Who owns the schema, how do migrations fail silently, and why must the schema follow
the reads and the retention rule, not only the writes?

**Source:** `developer-pitfalls.md` (draft v4) §13.1–13.2, §13.10–13.13, §2.17; login-tracking assessment
(track-login-service: Liquibase, PostgreSQL).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Schema ownership

- [ ] **One owner of the schema** — Liquibase (or Flyway) creates it; Hibernate `ddl-auto: validate` only checks
      that entities match and fails at startup on a mismatch.
- [ ] **An applied changeset is never edited** — checksums in `databasechangelog`; every change is a new file.
- [ ] **Migrations as a separate deploy step (production)** — under a DDL user; the application user has DML
      only.

## 2. Migrations that fail silently

- [ ] **Open-source Liquibase ignores the column attribute `checkConstraint`** — `addCheckConstraint` is paid;
      the changelog looks right, the table has no constraint.
      Did: plain `sql` step with `ALTER TABLE ... ADD CONSTRAINT ... CHECK`; no `dbms` restriction (on an
      unsupported database the migration must fail, not be skipped).
- [ ] **Verify behaviour, not the changelog text** — insert an invalid value, it must fail.
      Did: `LiquibaseSchemaIT` (unknown client rejected).
- [ ] **Pool settings also hit the migrations** — `statement_timeout` on the Hikari pool applies to Liquibase
      (same DataSource); a long `CREATE INDEX` breaks the startup.
      Did: own connection for Liquibase (`spring.liquibase.url/user/password`).
- [ ] **Test trap:** Boot reads `spring.liquibase.url` while evaluating conditions — an unresolvable `${DB_URL}`
      fails there; a literal URL in test config, the real one comes from `@ServiceConnection`.
- [ ] **Per-role timeout in production** — `ALTER ROLE ... SET statement_timeout`; one long change:
      `SET LOCAL statement_timeout = 0` inside its transaction (not for `CREATE INDEX CONCURRENTLY`).

## 3. The schema follows the reads, not only the writes

- [ ] **A table designed for inserting only** — PK + one index; the first real queries become full scans.
- [ ] **Indexes for the real queries** — column order = filter first, then sort, e.g.
      `(customer_id, login_timestamp DESC)`; every index costs write speed and space.
      Link: `04_Data_Layer/2_Storage_and_Retrieval` (index design).
- [ ] **Retention by time partitions** — drop a whole partition instead of a huge `DELETE` (locks, bloat, hits
      the statement timeout).
- [ ] **Verify with `EXPLAIN ANALYZE`** on realistic data volume.
- [ ] **Ask early how the data will be read and kept** — Did: reads not specified → only PK + `customer_id`
      index, listed in Known limitations.

## 4. Keys and stored values

- [ ] **Random UUIDv4 primary key** — inserts spread over the whole B-tree (page splits, cache misses, WAL);
      UUIDv7 is time-ordered — often the producer's decision, not yours.
      Link: `07_Distributed_Data/4_Consistency_and_Consensus/todo_Consistency_and_Consensus.md` §4 (ID generation).
- [ ] **Natural key from the event** (`messageId` as PK) — idempotency by design →
      `05_Consistency/1_DB_Consistency/todo_Pitfalls_Idempotent_Insert.md`.
- [ ] **Enum storage** — `@Enumerated(STRING)` stores `name()`; renaming constants changes stored values and
      breaks CHECK constraints. Keep the JSON form separate (`@JsonValue`) →
      `08_Integration/1_Encoding_and_Evolution/todo_Pitfalls_Contracts_&_JSON.md`.
- [ ] **Data minimization** — store only the vital fields; Did: `username` arrives but is not stored
      → `11_Security/todo_Pitfalls_Security.md` (personal data).
- [ ] **Column sized by the domain** — `varchar(45)` = longest textual IPv6; same limit in the DTO.

---

## Not here

- Statement and connection timeouts as part of the caller's budget → `06_Resilience/todo_Pitfalls_HTTP_Calls.md`.
- Duplicate handling in code → `05_Consistency/1_DB_Consistency/todo_Pitfalls_Idempotent_Insert.md`.
