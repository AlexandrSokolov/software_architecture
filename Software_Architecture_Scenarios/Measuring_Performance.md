### Compute percentiles from timings
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

1,000 request timings, sorted ascending:
- positions 1–950 → near **100 ms**
- positions 951–1000 → between **2 s and 10 s**

Find p50, p99, and p999. Show the position each one lands on, and the value there.

</details>

<details><summary>Show answer</summary>

**Two steps, kept separate:**
1. Percentile → position (exact): `rank = ceil( (P / 100) × N )`, N = 1000. `ceil` = round up.
   P is the percentile number: p50 → P=50 → 0.50; p99 → 0.99; p999 → 0.999.
2. Position → value: read the sorted list at that position.

**p50:** `rank = ceil(0.50 × 1000) = 500`
Position 500 is in the fast block (1–950) → **~100 ms**. Typical wait.

**Getting a value out of the slow block.** For p99 and p999 the position lands in the slow block (951–1000).
To turn a position into a second-value, assume the 50 slow requests are evenly spread from 2 s to 10 s.
That's **linear interpolation** — exactly the method NumPy and most monitoring tools use by default.

Line through the slow block: position 951 → 2 s, position 1000 → 10 s. Span 8 s over 49 steps:

`value(k) = 2 + (k − 951) / 49 × 8`

**p99:** `rank = ceil(0.99 × 1000) = 990`
`value(990) = 2 + (990 − 951)/49 × 8 = 2 + 39/49 × 8 ≈ 2 + 6.4 = 8.4 s`
Position 990 is the 40th of the 50 slow requests → 4/5 up the band → **≈ 8.4 s**. The tail is now visible.

**p999:** `rank = ceil(0.999 × 1000) = 999`
`value(999) = 2 + (999 − 951)/49 × 8 = 2 + 48/49 × 8 ≈ 9.8 s`
Position 999 is the 2nd-to-last request → near the top of the band → **≈ 10 s**. The near-worst case.

**The shape:**
- p50 → fast group (~100 ms).
- p99 → slow group, 4/5 up (~8.4 s).
- p999 → slow group, near max (~10 s).
  Higher percentile → deeper into the tail → worse timing.

Trigger: `rank = ceil(P/100 × N)` gives the position; interpolate across the slow band to get the value.

</details>

</details>

### Scenario — 1,000 timings, which stat?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

1,000 request timings:
- 950 requests near **100 ms**
- 50 requests between **2 s and 10 s**

A dashboard shows the **mean ≈ 400 ms**. Is it healthy?

</details>

<details><summary>Show answer</summary>

**Verdict:** not healthy — the mean is hiding a real problem. 50 users (5%) are having a terrible time and the
dashboard can't see them.

**Why the mean lies here:** the 50 multi-second requests pull the average up to ~400 ms — a value almost *no* request
actually saw (most were 100 ms, none were 400). It describes neither the typical user (100 ms) nor the suffering one
(2–10 s).

**What to report instead:**
- **median (p50) ≈ 100 ms** — the honest typical wait. Sort the 1,000 timings and take the middle (500th): it sits
  deep in the fast group, so it correctly reports "typical request = 100 ms." Useful, but it says nothing about the tail.
- **p99 ≈ 8 s** — this is what makes the 50 slow ones visible. p99 is the value 99% of requests come in under: sort,
  walk to the 990th request. The slow group is requests 951–1000, so the 990th is *inside* it — the p99 value is a
  slow-request time. The dashboard now literally shows "p99 = 8 s," and 8 s is impossible to miss.
- **careful with p95 — it misses the problem here.** p95 is the 950th request. The slow block starts at the 951st, so
  p95 lands one position *before* the tail, still at ~100 ms. Watch p95 and the dashboard reads 100 ms → green, while
  5% of users suffer. With exactly 5% slow, you need a percentile past 95 to reach them — p99 does, p95 doesn't.

**The lesson:** which percentile you watch decides whether you see the problem at all. Same data, p95 says "healthy,"
p99 says "on fire." Pick the percentile deep enough to land inside the group you care about.

Trigger: a few big outliers make the mean lie — check the median for the typical and a high-enough percentile for the
tail.

</details>

</details>

### SLO check #1 — does it pass?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

**SLO:** p95 < 500 ms **and** p99 < 800 ms.

20 recent request timings (ms):

`80, 90, 95, 100, 105, 110, 115, 120, 120, 130, 140, 150, 160, 180, 200, 240, 300, 900, 950, 1000`

Compute p95 and p99. Is the SLO met?

</details>

<details><summary>Show answer</summary>

**How to read a percentile position:** sort ascending, then pN is at position ⌈N/100 × n⌉. Here n = 20, already
sorted.

- **p95** → ⌈0.95 × 20⌉ = position **19** → value = **950 ms**.
- **p99** → ⌈0.99 × 20⌉ = position **20** → value = **1000 ms**.

**Verdict: SLO FAILED — both breached.**
- p95 = 950 ms, target < 500 ms → fail.
- p99 = 1000 ms, target < 800 ms → fail.

The last three requests (900/950/1000 ms) are a slow tail heavy enough to push both percentiles over. Note the median
is only 130 ms — a "typical wait" dashboard would look fine while the SLO is clearly broken. That gap is the point.

</details>

</details>

### SLO check #2 — does it pass?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

**SLO:** p95 < 300 ms.

20 recent request timings (ms):

`110, 120, 130, 140, 150, 160, 165, 170, 175, 180, 185, 190, 195, 200, 210, 220, 240, 260, 280, 4000`

Compute p95. Is the SLO met? Anything you'd flag?

</details>

<details><summary>Show answer</summary>

- **p95** → ⌈0.95 × 20⌉ = position **19** → value = **280 ms**. Target < 300 ms → **SLO PASSED.**

**But flag it — the SLO is passing while the service has a real problem.** There is one request at **4000 ms** (4 s).
It sits at position 20, *just past* the p95 cutoff at position 19, so p95 never sees it. With 20 samples, p95 covers
only the first 19 — the single worst request (5% of traffic) is invisible to this metric by exactly one position.

- **p99** → position 20 → **4000 ms**. If the SLO had included a p99 target, it would fail hard.

**The lesson (interview-grade):** a passing p95 is not "all good." When the slow fraction is near or below `1 − p`,
the percentile you chose can sit right on the edge and miss it. Always check a deeper percentile (p99) or the raw max
before declaring health — and set SLOs at a percentile deep enough to catch the tail you care about.

</details>

</details>
