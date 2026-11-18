# LSM-Trees — Internals (deprecated)

**Status.** Not part of regular review. Choosing an LSM engine is covered in
[Choosing an Engine](3.4.02_Choosing_an_Engine.md).

**Why kept.** These cards show how an append-only engine is built: a hash index over a log, segments, compaction,
tombstones, SSTables with sparse indexes, the memtable, Bloom filters, compaction strategies. None of it changes an
architecture decision, but the ideas are reused widely:
- append-only log plus compaction — Kafka compacted topics, snapshots in event sourcing;
- tombstones — deletes in any system that never overwrites data (Kafka, Cassandra, sync engines);
- Bloom filter — a cheap "definitely not here" check before a costly lookup (caches, web crawlers, CDNs);
- merging sorted files — sorting data bigger than memory (external merge sort, batch jobs).

### What is log-structured storage?
<details><summary>Show answer</summary>

A family of storage engines that only add to the end of a file (a **log** — not an application log with error
messages) and never change data already written:

- A new value for a key is appended.
- The old value stays in the file until a background job cleans it up.

</details>

### How does a hash index over a log work?
<details><summary>Show answer</summary>

All data lives in one file that only grows at the end. Memory holds a hash map: key → byte position in the file where
that key's latest value starts (e.g. `user42 → 1064`).

- **Write:**
    - append the key and value to the end of the file;
    - set the key's map entry to the new position.
- **Read:**
    - look up the key's position in the map;
    - jump to that position in the file and read the value — at most one disk read.

</details>

### Growing log — which problems, which fixes?
<details><summary>Show answer</summary>

Every write is appended and nothing is overwritten, so each key's old values stay in the file and it grows until the
disk is full. Same in every log-structured engine — hash index and LSM-tree alike.

Grows forever → split into segments → compact closed ones → mark deletes:

- [Cleaning a log while writes still arrive](#clean-a-log-while-writes-arrive--how) → segments.
- [Closed segments full of old values](#what-is-compaction) → compaction.
- [Deleting a key when nothing is overwritten](#delete-a-key-from-an-append-only-log--how) → tombstone.

</details>

### Clean a log while writes arrive — how?
<details><summary>Show answer</summary>

Cut the log into **segments** — separate files:

- writes go only to the newest segment;
- when it reaches a set size, it is closed and a new segment starts;
- closed segments never change again → a background job can [rewrite them](#what-is-compaction) while writes go on.

</details>

### What is compaction?
<details><summary>Show answer</summary>

**Compaction** — shrinking the log by **merging** closed segments into a new one that keeps only the latest value of
each key. Runs in the background.

- **Merge:**
  - read one or more closed segments;
  - a key found more than once → keep only its latest value;
  - write the result as one new segment — several small segments become one.
- **Sorted segments** ([SSTables](#what-is-an-sstable)) merge like merge sort:
  - read all files side by side, each from its first key; always take the smallest key next;
  - the output comes out sorted → it is a valid SSTable itself;
  - memory holds only the current record of each file → files bigger than memory merge fine.
- **Switch:** reads move to the new segment, then the old ones are deleted; until the switch, reads use the old files.

</details>

### Delete a key from an append-only log — how?
<details><summary>Show answer</summary>

Append a special "deleted" record for the key — a **tombstone**:

- reads go from newest segment to oldest, so they meet the tombstone before any older value → key not found;
- compaction drops the key's older values;
- the tombstone itself is dropped only when no older segment can still hold the key — otherwise the old value would
  come back.

</details>

### Hash index over a log — trade-offs?
<details><summary>Show answer</summary>

- **Good at:**
    - fast writes — only appending to the end of a file;
    - fast reads — one map lookup, then at most one disk read.
- **Limits:**
    - every key must fit in memory — a hash map on disk is slow (many random reads and writes);
    - no range queries — keys are not sorted, so "all keys from A to F" means looking up each key one by one.
- **Best fit:** a small set of keys whose values change often (e.g. a play counter per video URL).

</details>

### Hash index limits — how are they fixed?
<details><summary>Show answer</summary>

Keep each segment sorted by key — an [SSTable](#what-is-an-sstable). Sorted order fixes both limits:

- **Every key in memory** → memory holds only one key every few KB, a
  [sparse index](#how-do-you-find-a-key-inside-an-sstable). To find `user42`, jump to `user30` and read forward until
  you hit it or pass it. In a file kept in write order that fails: the key could be anywhere, so every key's exact
  position must be kept.
- **No range queries** → jump to the start key and read forward; "A to F" is one read through one part of the file.

</details>

### What is an SSTable?
<details><summary>Show answer</summary>

**SSTable** (Sorted String Table) — a segment file on disk whose records are sorted by key, each key once per file.

</details>

### How do you find a key inside an SSTable?
<details><summary>Show answer</summary>

Memory holds an index of only some keys (**a sparse index**), e.g. one every few KB, with their byte positions. 
The file is sorted, so the gaps don't matter:

- **One key:**
    - find the two index keys it falls between (`user42` sits between `user30` and `user58`);
    - jump to the first one's position and read forward until you hit the key — or pass it, so it's not there.
- **A range:** jump to the start key, then read forward — the file is already in key order.

</details>

### Writes arrive in random key order — how do you produce a sorted file?
<details><summary>Show answer</summary>

Writes come as `user42`, `user7`, `user99`. Two ways that fail:

- **Append each write** → the file is in write order, not sorted.
- **Insert each write at its sorted place** → that overwrites the middle of the file; the cheap append is gone.

Fix — sort in memory first, in a **memtable**: a sorted map in memory (like Java's `TreeMap`); keys go in any order
and come out sorted.

- When it grows past a few MB, write it to disk in key order as a new [SSTable](#what-is-an-sstable) — a whole file
  written in one go, so still only appending.
- A fresh memtable takes new writes meanwhile; the old one is freed once the file is written.

</details>

### The memtable lives in memory — how do you survive a crash?
<details><summary>Show answer</summary>

Write each change to a file on disk first, and rebuild the memtable from that file after a crash.
This file is a [WAL](#what-is-a-wal).

</details>

### What is a WAL?
<details><summary>Show answer</summary>

**WAL** (write-ahead log) — a file on disk that the database only appends to.
Every change goes into it *before* it is applied anywhere else; that's the "write-ahead".

- **After a crash** — replay the WAL from the start to rebuild what was lost (here: the memtable).
- **Not sorted** — it doesn't need to be; its only job is recovery.
- **Cleanup** — once the memtable is written to disk as an SSTable, its part of the WAL is no longer needed and is
  deleted.

</details>

### What is an LSM-tree?
<details><summary>Show answer</summary>

**LSM-tree** (Log-Structured Merge-tree, first published in 1996) — a storage engine that combines:

- **[Memtable](#writes-arrive-in-random-key-order--how-do-you-produce-a-sorted-file)** — sorts writes in memory and
  flushes them as a new SSTable.
- **[WAL](#what-is-a-wal)** — keeps the memtable's writes on disk, so a crash loses nothing.
- **[SSTables](#what-is-an-sstable)** — sorted files on disk, each with a sparse index.
- **[Compaction](#what-is-compaction)** — merges SSTables in the background, keeping only the latest values.

**Read** — search from newest to oldest; the first hit wins:

- the memtable;
- then each SSTable, newest first;
- not in the oldest one either → the key doesn't exist.

</details>

### Looking up a missing key checks every file — how do you avoid it?
<details><summary>Show answer</summary>

Keep a small structure in memory for each SSTable that can say "this key is definitely not in this file". Files that
say so are skipped without a disk read. This structure is a [Bloom filter](#what-is-a-bloom-filter).

</details>

### What is a Bloom filter?
<details><summary>Show answer</summary>

**Bloom filter** — a small structure in memory that answers, for a key, "definitely not here" or "maybe here". It never
says "not here" for a key that is there.

- **Built from** — an array of bits, all 0 at the start, and a few hash functions.
- **Add a key** — hash it with each function and set the bit at each result to 1 (e.g. `user42` → bits 1, 4, 7).
- **Check a key** — hash it the same way:
    - any of those bits is 0 → definitely not added;
    - all are 1 → maybe added — other keys may have set the same bits (a **false positive**), so read the file to be sure.

</details>

### File count keeps growing — which ones do you merge, and when?
<details><summary>Show answer</summary>

[Compaction](#what-is-compaction) needs a rule for which files to merge and when — the
[compaction strategy](#compaction-strategies). The two common ones are size-tiered and leveled.

</details>

### Compaction strategies
<details><summary>Show answer</summary>

Both use size limits; the difference is how they handle key ranges — overlapping vs non-overlapping.

- **Size-tiered (overlapping keys):** merges files once several reach a similar size. The same key can sit in many
  files at once, so a read may have to check several files to find the newest value.
- **Leveled (non-overlapping keys):** files sit in levels, each bigger than the one before. Within a level, files don't
  share key ranges — a key is in at most one file per level. When a level hits its size limit, one file is merged only
  with the next-level files that cover the same keys. Reads check at most one file per level.

Trade-offs:
- **Size-tiered:** cheap writes, slow reads, more wasted disk (old versions stay until a merge happens to reach them).
- **Leveled:** expensive writes, fast reads, little wasted disk.

</details>

### LSM-tree — trade-offs?
<details><summary>Show answer</summary>

- **Good at:**
    - fast writes — a write goes to memory and reaches disk later as a whole sorted file, written in one go;
    - small files on disk — merging drops old values and leaves no empty gaps, and sorted data compresses well.
- **Limits:**
    - slower reads — a read checks the memtable and several files; Bloom filters help single-key reads, not ranges;
    - background merging competes with live requests for the disk — some responses get slow;
    - under heavy writes, merging can fall behind — files pile up, reads slow down, the disk fills.
- **Best fit:** many writes, fewer reads — event logs, metrics, sensor data, message history.

</details>

### Which databases use LSM-trees?
<details><summary>Show answer</summary>

- **Libraries you embed in your own program:**
    - LevelDB;
    - RocksDB (started as a fork of LevelDB).
- **Distributed databases:**
    - Cassandra, ScyllaDB;
    - HBase, Google Bigtable.
- **Databases running on RocksDB or a RocksDB-like engine:**
    - CockroachDB (Pebble, written in Go after RocksDB's design);
    - YugabyteDB, TiKV;
    - MyRocks (MySQL with RocksDB in place of InnoDB).
- **Search:** Lucene / Elasticsearch — a similar idea: files written once, merged in the background.

</details>

### Which workloads fit LSM-trees best?
<details><summary>Show answer</summary>

Many more writes than reads; reads by key or by a recent range of keys:

- **Metrics and sensor data** — new points every second from many sources; read as "the last hour of this server".
- **Event and activity logs** — clicks, audit records, app events; written once in large volume, read later.
- **Message history** — every message is a new write; a read loads the latest messages of one chat.
- **Local state in stream processing** — counts and running totals updated on every event; Kafka Streams keeps them in
  RocksDB by default, [embedded in the app's own process](#what-is-an-embedded-database).
- **Large tables where disk space costs the most** — LSM files compress well; Facebook built MyRocks — RocksDB
  [embedded inside MySQL](#what-is-an-embedded-database) — to save space.

</details>

### Need a database without running a server — what do you use?
<details><summary>Show answer</summary>

Run the database as a library inside your own program instead of as a separate server. This is an
[embedded database](#what-is-an-embedded-database).

</details>

### What is an embedded database?
<details><summary>Show answer</summary>

A database that is a library inside your program, not a separate server:

- **How it runs:**
    - in the same process as your code — no network, no server to run;
    - you use it through normal function calls, not queries over the network;
    - it keeps data in files on the local disk.
- **Examples** — "embedded" says how it runs, not how it stores data:
    - RocksDB — LSM-tree; also the storage layer that
      [bigger databases run inside themselves](#which-databases-use-lsm-trees) (TiKV, Kafka Streams);
    - SQLite, LMDB — B-tree;
    - DuckDB — stored by column, for analytics;
    - KùzuDB — graph.
- **Fits when:**
    - a mobile app keeps the user's own data on the phone;
    - backend data fits on one machine and few transactions run at the same time;
    - each tenant is small and fully separate — one database per tenant.

</details>