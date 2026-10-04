### How do you compute percentiles across machines?
<details><summary>Show answer</summary>

You can't combine the per-server p99 values — [averaging them gives an invented number](#why-not-average-per-server-p99s).
Combine the underlying data instead: each server keeps a histogram (counts per time range), you
[add the histograms](#how-do-you-merge-two-histograms), then read p99 once from the merged result.

Handle: merge the data, then compute — never compute, then merge.

</details>

### Why not average per-server p99s?
<details><summary>Show answer</summary>

Two servers:
- **A:** 100 requests, all ~100 ms → p99 = 100 ms.
- **B:** 100 requests, 90 at 100 ms and 10 at 2 s → p99 = 2 s.

Average of the two p99s: (100 ms + 2 s) / 2 = **1,050 ms**.

Truth: put all 200 requests in one sorted list — 190 at 100 ms, then 10 at 2 s. p99 = position ⌈0.99 × 200⌉ = 198,
inside the slow group → **2 s**.

The average is half the truth, and no request took 1,050 ms. A p99 is one point from each server's list; from two
points you can't rebuild the combined list — that depends on how many requests each server had and how they spread.

</details>

### How do you merge two histograms?
<details><summary>Show answer</summary>

Each server keeps counts per time range (bucket) instead of every raw timing. With the same buckets on every server,
merging is plain addition, bucket by bucket:

| Bucket      | Server A | Server B | Merged |
|-------------|----------|----------|--------|
| 0–200 ms    | 100      | 90       | 190    |
| 200 ms–1 s  | 0        | 0        | 0      |
| 1–2 s       | 0        | 10       | 10     |

The merged histogram is exactly the histogram of all requests together. To read p99: walk the buckets from fastest,
adding up counts until you reach 99% of the total (198 of 200) — you land in 1–2 s, so p99 is in that range. The
answer is as precise as the bucket width.

Smarter, compact versions of this idea (field term: *mergeable sketch*): HdrHistogram, DDSketch, t-digest.

</details>

### Hourly p99 from minute p99s?
<details><summary>Show answer</summary>

Same trap over time: averaging 60 minute-p99 values gives an invented number, exactly like averaging servers.
[Add the 60 minute histograms](#how-do-you-merge-two-histograms) into one, then read p99 once.

</details>

### Micrometer — percentiles or histogram?
<details><summary>Show answer</summary>

Two ways a Java service can report p99 with a Micrometer `Timer`:
- `publishPercentiles(0.99)` — each instance computes its own p99 and sends that number. Can't be combined across
  instances: the [averaging trap](#why-not-average-per-server-p99s).
- `publishPercentileHistogram()` — each instance sends bucket counts. Prometheus adds them across instances, then
  computes p99: `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))`.

For a cluster-wide p99, use the histogram.

</details>

## Gemini

The confusion around this topic usually clears up the moment you see the math fail in a real scenario. Here is exactly why you cannot average percentiles, and how histograms solve the problem.

### 1. Why Averaging Percentiles is a Math Trap

Imagine you have two servers, Server A (low traffic) and Server B (high traffic, struggling).

* **Server A** handles 100 requests. 99 finish in **10 ms**. 1 finishes in **100 ms**.
* Server A's p99 is **100 ms**.


* **Server B** handles 10,000 requests. 9,900 finish in **10 ms**. 100 finish in **5,000 ms**.
* Server B's p99 is **5,000 ms**.



If your monitoring dashboard simply averages the two p99s, it calculates: `(100 + 5000) / 2 = 2,550 ms`.

But look at the actual global traffic. You processed 10,100 total requests across the whole system. What is the true 99th percentile of all those requests?
The slowest 1% of 10,100 requests is 101 requests. Because 100 of those slowest requests sat at 5,000 ms, your true global p99 is actually **5,000 ms**.

By averaging the percentiles, your dashboard reported a fake number (2,550 ms) that hides the severity of Server B's problem. A percentile is a position in a sorted list; you cannot average positions.

### 2. The Naive Solution (Ship Everything)

To get the true global p99 of 5,000 ms, the central monitoring server (like Datadog) needs to sort all 10,100 timings in a single list.

If you do this at the scale of millions of requests per second, Server A and Server B would have to stream every single raw request timing over the network to the central server. This would overwhelm the network and require massive memory just to calculate a metric.

### 3. The Actual Solution (Mergeable Sketches / Histograms)

Instead of storing and shipping raw numbers, each server keeps a "tally sheet" of buckets. This tally sheet is the **histogram** (or "sketch").

Instead of recording: `[12ms, 15ms, 11ms, 5030ms...]`
**Server A** just keeps a tally:

* Bucket 10ms–20ms: 99 requests
* Bucket 100ms–200ms: 1 request

**Server B** keeps its own tally:

* Bucket 10ms–20ms: 9,900 requests
* Bucket 5000ms–6000ms: 100 requests

Every minute, Server A and Server B send their *tally sheets* to the central server, not their raw data. Sending a tally sheet uses almost zero network bandwidth.

**The Merge:**
The central server simply adds the buckets together to create one giant, global tally sheet:

* Global Bucket 10ms–20ms: 9,999 requests
* Global Bucket 100ms–200ms: 1 request
* Global Bucket 5000ms–6000ms: 100 requests

Now, the central server has the entire global distribution. It counts from the bottom up to find the 99th percentile and correctly lands in the 5000ms–6000ms bucket.

Data structures like **HdrHistogram** or **DDSketch** are just highly optimized, dynamic versions of these tally sheets. The term "mergeability" simply means that Bucket X from Server A can be mathematically added to Bucket X from Server B without losing accuracy.