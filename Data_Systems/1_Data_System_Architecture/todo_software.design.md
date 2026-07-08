### Timeline design #0 — the naive read
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

A social network stores users, posts, and follows in relational tables. The home timeline — recent posts by everyone
a user follows — is served by a single SQL join, run fresh on every request. 10 million users are online; each needs
their timeline kept current within 5 seconds. What breaks?

</details>

<details><summary>Show answer</summary>

**Read cost explodes.** Keeping timelines fresh by re-running the query means each client re-asks every 5 s (polling)
— 2 million timeline queries per second. Each query merges recent posts from ~200 followed accounts, so ~400 million
post lookups per second. That is the average; users who follow tens of thousands of accounts make one query alone
expensive.

The shape of the defect: **work is done at read time, and reads vastly outnumber writes** (5,800 posts/s vs millions
of timeline requests/s). The fix direction is to move the work to the write side.
[→ push instead of poll](#timeline-design-1--poll-vs-push)
[→ precompute the timeline](#timeline-design-2--read-time-vs-write-time)

</details>

</details>

### Timeline design #1 — poll vs push
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

Followers must see a new post within 5 seconds. One option: each client re-runs its timeline query every 5 seconds
while online. What is wrong with that, and what replaces it?

</details>

<details><summary>Show answer</summary>

**Polling pays the full query cost on every tick, per client, whether or not anything changed** — millions of
identical re-reads per second, most returning nothing new.

**Replace pull with push:** the server delivers a new post to online followers as it arrives, and the client
subscribes to its own timeline stream instead of asking repeatedly. Work happens once per post, not once per client
per interval.

The general move: **a steady high-frequency poll for rare changes → an event push on change.**
[→ precompute the timeline](#timeline-design-2--read-time-vs-write-time)

</details>

</details>

### Timeline design #2 — read-time vs write-time
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

Timeline reads massively outnumber posts, and the read query is expensive. Where should the timeline be computed —
when it is requested, or when a post is made? State the condition that decides it.

</details>

<details><summary>Show answer</summary>

**Decide by the read/write ratio.**

- **Reads ≫ writes (this case)** → compute at **write time**. Keep a stored timeline per user; when someone posts,
  insert that post into each follower's stored timeline — like dropping mail in a mailbox. Reads become a cheap cache
  lookup. This is **materialization**; the stored timeline is a materialized view.
  [→ what is materialization](#what-is-timeline-materialization)

- **Writes ≫ reads, or reads rare** → compute at **read time** (the naive join). Precomputing would do work for
  timelines nobody reads.

The trade is fixed and always the same: **materializing speeds up reads by doing more work on writes.** You are
moving cost from the frequent side to the rare side.
[→ the write cost it creates](#what-is-fan-out)

</details>

</details>

### Timeline design #3 — handling load spikes
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

Timelines are materialized on write. Normally that is ~1 million timeline writes per second. During a special event,
the post rate spikes far above normal. You cannot provision for the peak. How do you keep timelines fast to read
without dropping posts?

</details>

<details><summary>Show answer</summary>

**Decouple delivery from posting with a queue, and relax the freshness target under load.** The post is accepted
immediately; the fan-out writes to followers' timelines are enqueued and drained as capacity allows. During a spike,
posts take a little longer to appear in timelines — the 5-second target degrades gracefully — but nothing is lost.

Reads stay fast throughout, because they are served from the materialized cache regardless of how backed-up the write
queue is.

The principle: **when a write burst can't be absorbed in real time, buffer it and trade latency, not correctness.**

</details>

</details>

### Timeline design #4 — a user following tens of thousands
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

Timelines are materialized on write. One user follows tens of thousands of very active accounts, so their stored
timeline receives an enormous write rate. Do you pay for all those writes?

</details>

<details><summary>Show answer</summary>

**No — drop some of the writes.** A user following that many accounts cannot possibly read every post in their
timeline, so materializing all of them is wasted work. Write only a **sample** and show a partial timeline.

The reason this is safe: **the dropped data is never read.** Correctness the user can perceive is untouched.

Contrast this deliberately with the celebrity case — same mechanism, opposite verdict — where dropping writes is
*not* acceptable.
[→ the celebrity case](#timeline-design-5--a-celebrity-posts)

</details>

</details>

### Timeline design #5 — a celebrity posts
<details><summary><strong>Show details</strong></summary>

<details><summary>Show question</summary>

Timelines are materialized on write. A celebrity with over 100 million followers makes one post. Fanning that out
means over 100 million timeline inserts for a single post. Do you handle it the same way as an ordinary post?

</details>

<details><summary>Show answer</summary>

**No — and unlike the heavy-follower case, you cannot just drop the writes:** those followers *are* reading, so
skipping the fan-out would hide the post from millions of real readers.

**Split the strategy by sender.** Ordinary posts stay fully materialized (fan-out on write). Celebrity posts are
**stored once, separately, and merged into each follower's timeline at read time.** You pay a small read-time merge
instead of 100 million writes per post.

This is a **hybrid**: write-time materialization for the common case, read-time computation for the extreme tail. The
lesson across #4 and #5: the same mechanism breaks at both ends of a skewed distribution, and the right fix is opposite
at each end — drop when unread, merge-on-read when read.
[→ read-time vs write-time](#timeline-design-2--read-time-vs-write-time)

</details>

</details>

### What is timeline materialization?
<details><summary>Show answer</summary>

Precomputing and storing the result of a query so later reads are a cheap lookup instead of a fresh computation. The
stored result is a **materialized view**; here, each user's precomputed home timeline is one.

The trade is inherent: reads get faster, writes get more expensive, because the stored result must be updated every
time its inputs change. It pays off only when reads greatly outnumber writes.
[→ read-time vs write-time](#timeline-design-2--read-time-vs-write-time)

</details>

### What is fan-out?
<details><summary>Show answer</summary>

The factor by which one incoming request multiplies into downstream requests. One post from a user with 200 followers
triggers 200 timeline writes — a fan-out of 200.

It sets the write cost of materialization: average post rate × average followers = total timeline writes. It also
explains why skew hurts — a celebrity's fan-out is millions, so one post becomes millions of writes.
[→ the celebrity case](#timeline-design-5--a-celebrity-posts)

</details>