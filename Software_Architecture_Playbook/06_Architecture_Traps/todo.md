Caching with Redis (cache-aside, TTL, eviction, stampedes) lives in `06_Architecture_Traps/`.

Cache stampede: a popular cache key expires, thousands of requests miss at the same moment, and all of them hit the database to rebuild the same value, which can take the database down. Fixes: one request rebuilds while the others wait or get the old value, refresh before expiry, or randomize expiry times.

Cache vs DB staleness: the data lives in two places. The app updates the database and must also delete the cache key. If that step fails, or two requests interleave, the cache serves old data until it expires.

## 

Order saved in one store, event in another — what breaks?
Atomicity and consistency (the dual-write problem). If the first write succeeds but the second fails (e.g., due to a
network timeout or crash), your systems are permanently out of sync. Fixing this requires distributed transactions
(Sagas) or the Transactional Outbox pattern.

### Orders plus a stream of order events — one database or two?
<details><summary>Show answer</summary>

- **Verdict:** one — keep the events in the orders database until their volume forces a split.
- **Reasoning:**
    - the order and its event are saved in one transaction → both or neither, no lost events;
    - an append-only events table with a time-ordered key is the cheapest kind of B-tree insert;
    - two databases mean two writes per change; one can succeed while the other fails.
- **Trade-off:**
    - when events far outnumber orders, they move to an LSM store or Kafka; the order and its event then no longer
      commit together → you need an outbox table or change data capture to send events reliably.

</details>

## 

Cache patterns (cache-aside, stampede, stale cache) go to 06_Architecture_Traps.

##
