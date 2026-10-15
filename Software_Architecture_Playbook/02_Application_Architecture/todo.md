
### Page 5000 of results is slow — why?
<details><summary>Show answer</summary>

**Verdict:** `OFFSET` makes the database read and throw away every row before the page. Switch to keyset pagination:
continue from the last row of the previous page.

**Reasoning:**
- page 5000, 20 per page → `ORDER BY created_at DESC LIMIT 20 OFFSET 99980`: the database walks 99,980 rows in order,
  discards them, returns 20 — every page is slower than the one before;
- keyset: `WHERE (created_at, id) < (:last_created_at, :last_id) ORDER BY created_at DESC, id DESC LIMIT 20` — the
  index on `(created_at, id)` jumps straight to the spot, so every page costs the same;
- `OFFSET` also shifts when rows change between requests: a new row on page 1 pushes everything down, and the next
  page repeats a row. Keyset continues from a fixed point, so it doesn't.

**Trade-off:**
- no jumping to page N — only next and previous: fine for infinite scroll and APIs, not for "go to page 37";
- the sort needs a unique tie-breaker (`id`), or rows with the same `created_at` are skipped or repeated;
- it needs an index on exactly the sort columns;
- the client keeps a cursor (the last row's values) instead of a page number;
- small tables, or only the first few pages ever read → `OFFSET` is fine and simpler.

From Gemini:
Page 5000 of results is slow — why?
Offset-based pagination (LIMIT 10 OFFSET 50000). The database must find, read, and discard the first 50,000 rows
before returning the next 10, resulting in wasted I/O. Fix it by using Cursor-based (Keyset) pagination
(WHERE id > last_seen_id LIMIT 10), which uses the index to jump directly to the target.

</details>