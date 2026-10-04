### Design a news feed — where do you do the work?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

A social app: 300 million users. Each opens the app about 20 times a day and posts about once a week. Most follow a
few hundred accounts; a few accounts have tens of millions of followers.

Each user must see the newest posts from everyone they follow. Where do you do the work — when a post is written,
or when the feed is read?

</details>

<details><summary>Show answer</summary>

**Verdict:** both — push for normal users, pull for celebrities above a follower threshold, merge at read time
([hybrid](../Software_Architecture_Playbook/03_Data_Systems/2_Nonfunctional_Requirements/3.2.8_Read_vs_Write_Trade-Off.md#celebrities-and-normal-users--one-approach-for-both)).

**Reasoning** — each of the
[two load numbers](../Software_Architecture_Playbook/03_Data_Systems/2_Nonfunctional_Requirements/3.2.8_Read_vs_Write_Trade-Off.md#which-load-numbers-decide-the-timeline-design)
moves the work one way:
- **Read/post ratio is huge** (about 140 to 1) → shift work to writes: precompute feeds for normal users.
- **Followers per user has extreme outliers** → shift work back to reads for them: pull celebrity posts at read time
  from a shared cache, since every reader needs the same list.

Pushes run in background workers off a queue; mailboxes are capped; inactive users are skipped.

**Trade-off:** more complex than pure push or pure pull — two code paths, a merge on every read, a threshold to tune
— and followers see new posts a few seconds late. Pure pull wins when reads are rare or the user base is small enough
that the join stays cheap; pure push wins when no account has a huge following, e.g. a company-internal network.

</details>

</details>

### Followers see new posts 5 minutes late — why?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

The feed uses push with background workers. Normally a new post shows up in followers' feeds within 2 seconds.
Today users complain it takes up to 5 minutes. Posting itself is still instant. What do you check?

</details>

<details><summary>Show answer</summary>

**Verdict:** propagation delay grown too large — check the push queue's backlog first (field term: *consumer lag*),
then replica lag.

**Reasoning:**
- Posting is instant because the push runs later. The delay a follower sees = time the push job waits in the queue
    + time to write the mailboxes. 5 minutes means jobs are waiting.
- **Queue backlog** — typical cause: a huge job ahead of everyone. An account that just crossed the follower
  threshold, or a threshold set too high, puts millions of mailbox writes in front of all other posts
  ([celebrity problem](../Software_Architecture_Playbook/03_Data_Systems/2_Nonfunctional_Requirements/3.2.8_Read_vs_Write_Trade-Off.md#one-user-30-million-followers--what-breaks)).
- **Replica lag** — something reads from a database replica that has fallen behind: the workers (they don't see the
  new post or the current follower list yet), or the feed (the mailbox has the post ID, but the replica doesn't have
  the post, so it is skipped until the replica catches up).

**Fix:** a separate queue for big jobs, so one celebrity post can't block normal ones; review the threshold; add
workers if the backlog grows every day. For replica lag, read just-posted data from the leader.

**Trade-off:** more workers cost money; a second queue adds moving parts; reading from the leader puts load back on
it. A few seconds of delay is normal for push — the goal is seconds, not zero.

</details>

</details>

### Feed p99 jumps whenever a celebrity posts — why?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

The feed uses the hybrid approach. Whenever one of a few big accounts posts, feed-load p99 across the cluster jumps
from 150 ms to 3 s for several minutes, while the median barely moves. Why, and what do you change?

</details>

<details><summary>Show answer</summary>

**Verdict:** one big post is stealing capacity from feed reads — either it is still being pushed (threshold too
high), or its cache entry is being rebuilt by millions of readers at once.

**Reasoning:**
- **Push storm** — the follower threshold that separates normal users from celebrities is set too high, so these
  accounts are still pushed. Millions of mailbox writes saturate the network, the workers, and the cache nodes that
  serve feed reads, and reads wait behind them. Only reads that land on the busy nodes slow down — so p99 jumps while
  the median stays flat.
- **Rebuild storm on the pull side** — the account is pulled correctly, but each new post deletes its cached post list.
  Millions of readers miss the cache at the same moment and all query the database to rebuild it (field term:
  *cache stampede*).

**Fix:**
- Push storm: lower the threshold — the check runs on every post, so accounts above it switch to pull automatically;
  also limit how fast push workers write, so writes can't starve reads.
- Stampede: on a new post, update the cached list in place instead of deleting it; or let one request rebuild it while
  the others wait for that result.

**Trade-off:** a lower threshold moves work to reads (more lists to merge per feed); slower pushes mean longer
delays before followers see posts; updating the cache in place adds work to every celebrity post.

</details>

</details>