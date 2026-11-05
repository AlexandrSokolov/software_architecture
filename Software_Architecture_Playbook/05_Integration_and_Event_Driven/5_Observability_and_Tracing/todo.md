
##



### A payment call times out — safe to retry?
<details><summary>Show answer</summary>

The card answers “when is a retry safe?”, and timeouts and retries already live in that folder (Release It!). 
Every retry policy you set up there runs into this question.

- **Verdict** — only if the server can spot a repeat: send a unique request ID, and the server ignores an ID it has
  already processed. That makes the call idempotent: repeating it has no extra effect.
- **Reasoning** — a timeout doesn't mean the payment failed. The request may have gone through and only the reply
  was lost, so a plain retry would charge the customer twice.
- **Trade-off** — the server must store the IDs it has seen and check them on every request. For a read, or a write
  that gives the same result when repeated (setting a status), a plain retry is fine.

</details>
