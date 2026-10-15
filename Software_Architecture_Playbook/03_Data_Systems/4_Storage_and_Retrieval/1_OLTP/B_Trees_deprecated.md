# B-Trees — Internals (deprecated)

**Status.** Not part of regular review. Choosing and using a B-tree is covered in
[Choosing an Engine](3.4.02_Choosing_an_Engine.md) and [Index Design](3.4.04_Index_Design.md).

**Why kept.** These cards go one level deeper — how the tree is built and kept correct: page splits and merges, the
WAL for crash safety, latches for many threads, copy-on-write trees. None of it changes an architecture decision, but
the ideas show up far outside databases:
- write-ahead log — any system that must survive a crash in the middle of a change (message brokers, file systems);
- copy-on-write — Git commits, ZFS and Btrfs snapshots, immutable collections;
- short locks on small parts of a shared structure — concurrent data structures in any language.

### B-trees — which problems, which fixes?
<details><summary>Show answer</summary>

Update in place → page full → split → crash mid-split → WAL → many threads → latches → every change written twice →
copy-on-write:

- [Updating keys in place on disk](#updating-keys-in-place-on-disk--how) → B-tree.
- [A page is full](#page-full-new-key-arrives--what-then) → page split.
- [A crash hits mid-split](#crash-mid-page-split--what-then) → WAL.
- [Many threads change the tree](#many-threads-change-the-tree--what-breaks) → latches.
- [Every change is written twice](#can-a-b-tree-work-without-a-wal) → copy-on-write B-tree.
- **Costs and fit:**
  - [trade-offs](#b-tree--trade-offs);
  - [workloads](#which-workloads-fit-b-trees-best);
  - [databases](#which-databases-use-b-trees).
- **Corner cases:**
  - [B+ tree](#b-tree-vs-b-tree);
  - [random primary keys](#random-primary-keys-slow-inserts--why);
  - [mass deletes](#rows-deleted-index-didnt-shrink--why).

</details>

### What is a B-tree?
<details><summary>Show answer</summary>

**B-tree** — data split into fixed-size blocks on disk called **pages** (usually 4–16 KB), linked into a tree:
one page points to another by its page number.

- **Root page** — the top inner page; every lookup starts here.
- **Inner pages** — highway signs: keys mark the boundaries, and pointers to child pages tell which exit to take.
  No data. The pointer between keys 300 and 600 leads to the page for keys 300–599.
- **Leaf pages** — the destination: the rows themselves, or a reference to where each row is stored.
  No child pointers; the path ends here.

Example — table `users`, B-tree on `user_id`:

```text
==================================================
PAGE 1 (Root / Inner Page)
Role: Navigation (highway signs)
Size: 4 KB block on disk
==================================================
[Pointer to Page 2]  <-- IDs below 1000
[Key: 1000]
[Pointer to Page 3]  <-- IDs 1000–1999
[Key: 2000]
[Pointer to Page 4]  <-- IDs 2000 and above
==================================================

==================================================
PAGE 2 (Inner Page)
Role: Navigation (highway signs)
Size: 4 KB block on disk
==================================================
[Pointer to Page 5]  <-- IDs below 300
[Key: 300]
[Pointer to Page 6]  <-- IDs 300–599
[Key: 600]
[Pointer to Page 7]  <-- IDs 600–999
==================================================

==================================================
PAGE 6 (Leaf Page)
Role: Data (destination)
Size: 4 KB block on disk
==================================================
[Key: 312] | [Value: {"name": "Alice", "age": 28}]
[Key: 450] | [Value: {"name": "Bob", "age": 34}]
[Key: 587] | [Value: {"name": "Diana", "age": 41}]
-- No child pointers here. Just data. --
==================================================
```

Pages 3–4 are inner pages like page 2; pages 5 and 7 are leaves like page 6. Same shape, other ID ranges.

</details>

### Child pointers per page — what does it give?
<details><summary>Show answer</summary>

**Branching factor** — how many child pointers one page holds. Usually several hundred: an
[inner page](#what-is-a-b-tree) holds only keys and page numbers, no rows, so many fit in one 4 KB page.

**What it gives: few reads, even on a huge tree.** A lookup reads one page per level. Each level down multiplies
the reachable pages by the branching factor:

```text
branching factor: 500

page reads per lookup    leaf pages reachable
2                                        500
3                                    250,000
4                                125,000,000   <-- billions of rows
```

So even a big table needs only 3–4 levels: 3–4 page reads to find any key.

</details>

### How does a B-tree find a key?
<details><summary>Show answer</summary>

Start at the root page and go down one level at a time:
- **Inner page** — find the two keys around the one you want and follow the pointer between them.
- **Leaf page** — the key is here, with its value or row reference, or it does not exist.

Example — find `user_id = 450` in the [`users` B-tree](#what-is-a-b-tree):

```text
read page 1 (root):   450 < 1000         → page 2
read page 2 (inner):  300 ≤ 450 < 600    → page 6
read page 6 (leaf):   450 found          → {"name": "Bob", "age": 34}
```

[One page read per level](#child-pointers-per-page--what-does-it-give).

</details>

### How does a B-tree change a value?
<details><summary>Show answer</summary>

- Find the leaf page that holds the key — [same path as a read](#how-does-a-b-tree-find-a-key).
- Change the value inside that page.
- Write the whole page back to the same place on disk, over the old version.

Inner pages stay as they are: no key changed, so every pointer still leads to the right page.

Example — Bob's age 34 → 35:

```text
page 6 (leaf) before:  [Key: 450] | [Value: {"name": "Bob", "age": 34}]
page 6 (leaf) after:   [Key: 450] | [Value: {"name": "Bob", "age": 35}]   <-- written over the old page 6
```

If the key itself changes, the entry must move to another leaf: that is a
[removal](#how-does-a-b-tree-remove-a-key) plus an [add](#how-does-a-b-tree-add-a-key).

</details>

### How does a B-tree add a key?
<details><summary>Show answer</summary>

- Find the leaf page whose key range covers the new key — [same path as a read](#how-does-a-b-tree-find-a-key).
- **Room in the page** — add the key there, in sorted order. Done.
- **Page full** — split it into two half-full pages. The parent gets a new key and a pointer to the new page.
- **Parent full too** — it splits the same way. Splits can climb all the way up.
- **Root splits** — a new root is made above it. The tree grows one level, at the top.

Because the tree grows only at the top, every leaf stays at the same depth: the tree stays balanced.

Example — add `user_id = 500`; leaf page 6 is full (values left out):

```text
before
  page 2 (inner): [P5] 300 [P6] 600 [P7]
  page 6 (leaf):  312 | 450 | 587                  <-- full, no room for 500

after the split
  page 2 (inner): [P5] 300 [P6] 500 [P8] 600 [P7]  <-- new key 500 + pointer to page 8
  page 6 (leaf):  312 | 450
  page 8 (leaf):  500 | 587                        <-- new page
```

</details>

### How does a B-tree remove a key?
<details><summary>Show answer</summary>

- Find the leaf page that holds the key ([same path as a read](#how-does-a-b-tree-find-a-key)) and remove the entry.
- **Page still at least half full** — done.
- **Page less than half full** — take entries from a neighbor page, or merge the two pages into one. On a merge,
  the parent loses the key and pointer for the removed page.
- **Parent too empty** — same fix one level up. Can climb all the way up.
- **Root left with one child** — that child becomes the new root. The tree shrinks one level, at the top.

Example — remove 312 and 450; page 6 is left with one key and merges with page 7 (values left out):

```text
before
  page 2 (inner): [P5] 300 [P6] 600 [P7]
  page 6 (leaf):  312 | 450 | 587
  page 7 (leaf):  640 | 720

after removing 312, 450 and merging
  page 2 (inner): [P5] 300 [P6]          <-- key 600 and pointer to page 7 removed
  page 6 (leaf):  587 | 640 | 720
  page 7:         free, can be reused
```

It is [adding a key](#how-does-a-b-tree-add-a-key) in reverse: merge instead of split, shrink at the top instead of
grow.

Real databases are often lazier: many leave half-empty pages alone and drop a page only once it is fully empty.

</details>

### Page full, new key arrives — what then?
<details><summary>Show answer</summary>

Split the full page into two half-full pages and add a pointer to the new one in the parent page. This is a
[page split](#what-is-a-page-split).

</details>

### What is a page split?
<details><summary>Show answer</summary>

**Page split** — what a B-tree does when a key must go into a full page:

- the page becomes two pages, each about half full;
- the parent page gets a new key and a pointer to the new page;
- if the parent is full too, it splits the same way — up to the root;
- when the root splits, a new root is made above it — the tree grows one level taller.

The tree grows only at the root, so every leaf stays at the same depth and every lookup costs the same number of page
reads.

</details>

### Crash mid page split — what then?
<details><summary>Show answer</summary>

A split overwrites several pages: the two halves and the parent. If the crash lands after only some of them reach the
disk, the tree is broken — keys are lost, or a page is left that no parent points to (an **orphan page**).

Fix: before touching any page, append the change to a [WAL](3.4.02_LSM_Trees.md#what-is-a-wal). After a crash, replay
it to bring the pages back to a consistent state. B-tree databases also call it the **redo log**.

</details>

### Many threads change the tree — what breaks?
<details><summary>Show answer</summary>

A reader can walk into a page while a split is half done and see a broken tree — keys missing, or a pointer leading to
the wrong page. Fix: lock each page only for the moment it is read or changed. These short locks are
[latches](#what-is-a-latch).

</details>

### What is a latch?
<details><summary>Show answer</summary>

**Latch** — a very short lock on one page (or a few), held only while that page is read or changed:

- **Protects:** the tree's own pages and pointers from half-done changes.
- **Held for:** a moment — released as soon as the page is done.
- **Not a transaction lock:** row and key locks protect your data and are held until commit; latches protect the
  database's internal pages.

</details>

### Can a B-tree work without a WAL?
<details><summary>Show answer</summary>

Yes — never overwrite a page. Write each changed page to a new place on disk; a crash then can't leave a half-written
page, so there is nothing to replay. This is a [copy-on-write B-tree](#what-is-a-copy-on-write-b-tree).

</details>

### What is a copy-on-write B-tree?
<details><summary>Show answer</summary>

**Copy-on-write B-tree** — a B-tree that never overwrites a page (e.g. LMDB):

- **Write:**
  - copy the leaf page, change the copy, write it to a free place on disk;
  - copy each parent up to the root, so they point to the new pages;
  - switch to the new root in one step — that is the commit.
- **Crash:** the old tree stays untouched until the switch, so there is nothing to repair.
- **Readers:** keep reading the old version while a write runs — readers and the writer don't block each other.

</details>

### B-tree — trade-offs?
<details><summary>Show answer</summary>

- **Good at:**
  - steady reads — every lookup follows one path from root to leaf, the same few page reads each time;
  - each key lives in exactly one page — easy to lock a key or a range of keys for transactions.
- **Limits:**
  - slower writes — a small change rewrites a whole page, plus the WAL; pages sit in random places on disk;
  - wasted space — splits leave pages partly empty.
- **Best fit:** [read-heavy and transactional workloads](#which-workloads-fit-b-trees-best).

</details>

### Which workloads fit B-trees best?
<details><summary>Show answer</summary>

Many reads, transactions, and steady response times:

- **Business records** — orders, accounts, users; read and updated by key, often inside transactions.
- **Range queries that must stay fast** — "this customer's orders, newest first": one walk down the tree, then leaf
  pages in order.
- **Read-heavy data** — product catalogs, settings, user profiles; read far more often than written.

</details>

### Which databases use B-trees?
<details><summary>Show answer</summary>

- **Relational databases** — B-tree is the default index type:
  - PostgreSQL;
  - MySQL InnoDB — the table itself is a B+ tree sorted by primary key;
  - Oracle, SQL Server.
- **Embedded:**
  - SQLite;
  - LMDB — copy-on-write.
- **Document:** MongoDB — its WiredTiger engine stores collections and indexes as B+ trees.

</details>

### B-tree vs B+ tree?
<details><summary>Show answer</summary>

Most databases that say "B-tree" actually use a **B+ tree**:

- **B-tree** — any page, inner or leaf, can hold keys with their values.
- **B+ tree:**
  - values only in leaf pages; inner pages hold only keys and pointers → more keys per page, fewer levels;
  - inner pages can keep keys shortened — only enough of each key to tell two ranges apart → even more keys per page;
  - each leaf page points to its neighbour → a range scan walks along the leaves without climbing back up.

</details>
