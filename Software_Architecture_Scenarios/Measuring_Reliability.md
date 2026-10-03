### Availability from an outage log?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

Over a **30-day** month the service had **3 outages** lasting **20, 40, and 30 minutes**.

Compute its availability.

</details>

<details><summary>Show answer</summary>

Work out each piece from the log:

- Total time in window: 30 × 24 × 60 = **43,200 min**
- Total downtime: 20 + 40 + 30 = **90 min** → up-time = 43,200 − 90 = **43,110 min**
- **MTTR** = downtime ÷ number of outages = 90 ÷ 3 = **30 min**
- **MTBF** = up-time ÷ number of outages = 43,110 ÷ 3 = **14,370 min**

Plug in:

    Availability = MTBF / (MTBF + MTTR) = 14,370 / (14,370 + 30) = 0.99792 ≈ 99.79%

**Shortcut check:** the ÷3 cancels top and bottom, so availability = up-time / total = 43,110 / 43,200 = same 99.79%.
Roughly three nines.

**The lesson:** cut MTTR from 30 min to 10 min and availability climbs to ~99.93% — same failure count, higher
availability. Recovery speed moves the number, not just how often it breaks.

</details>

</details>

### Required MTBF for a target?
<details><summary><strong>Show details</strong></summary>

<details><summary>Show scenario</summary>

You must hit **99.9%** availability. Your **MTTR is 1 hour** — that's how long recovery takes and you can't cut it.

How long must the system run between failures (**MTBF**) to reach the target?

</details>

<details><summary>Show answer</summary>

Rearrange the availability formula to solve for MTBF:

    Availability = MTBF / (MTBF + MTTR)

    →  MTBF = Availability × MTTR / (1 − Availability)

Plug in (Availability = 0.999, MTTR = 1 h):

    MTBF = 0.999 × 1 / (1 − 0.999) = 0.999 / 0.001 = 999 hours ≈ 41.6 days

**Read it back:** with a 1-hour recovery, the system may fail **at most once every ~42 days** to stay at three nines.
Want four nines with the same 1-hour MTTR? Then (1 − 0.9999) = 0.0001 → MTBF ≈ 9,990 h ≈ 416 days — 10× rarer.

**The lesson:** the two levers trade against each other. If you can't make MTTR smaller, the only way to a higher
target is failing far less often — and each extra nine demands a 10× longer gap between failures.

</details>

</details>
