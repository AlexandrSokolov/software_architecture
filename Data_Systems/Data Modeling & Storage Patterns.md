# Document 1: Data Modeling & Storage Patterns

## Append-Only Audit Logging & Error Categorization

This document defines the data architectural standards for tracking transaction and import
lifecycles across enterprise integration touchpoints. It covers underlying storage mechanics,
schema optimization, and structural pattern-matching for bulk remediation.

### 1. The Single-Table, Append-Only Audit Log
When tracking the processing lifecycle of items (e.g., entity imports, sync events), systems must
utilize a **Single-Table, Append-Only Audit Log** rather than splitting transactions into separate
success and failure tables.

#### The Storage Mechanics of `NULL` Columns
A common architectural concern is that maintaining columns like `error_message` or `error_payload`
in a shared table creates data bloat for successful items where these cells remain empty. Modern
database engines and cloud-native storage layers completely eliminate this issue:

* **Variable-Length Fields:** Defining columns as `VARCHAR` or `TEXT` means the engine does not
  pre-allocate blocks of empty memory. The physical storage footprint matches only what is written.
* **The Null Bitmap:** Relational engines (PostgreSQL, MySQL) track `NULL` cells via a highly
  optimized bit-array header at the beginning of each row. If a cell is empty, the engine toggles
  a single bit to `0` and allocates zero bytes of physical disk space or RAM for that data.
* **Sparse SaaS Stores:** Cloud data tables are structurally schema-flexible. Empty values are
  omitted entirely as key-value pairs at the storage layer, resulting in a zero-byte footprint.

#### The Timeline of Truth vs. Split-Table Limitations
Separating data into tables like `Successful_Items` and `Failed_Items` introduces data integrity
risks and high operational querying penalties:

* **The Retry Loop:** If an item fails on attempt 1 and succeeds on attempt 2, a split-table layout
  forces the system to either delete the error history (destroying the historical audit trail) or
  keep it (creating messy data duplication across tables).
* **The Current-State Penalty:** To answer "Is this entity currently broken right now?", a split-table
  architecture requires executing complex `UNION` queries or multi-pass API calls to compare
  timestamps across distinct data stores.

#### The Append-Only Standard
Every processing attempt must be written as a fresh, sequential row. Finding the absolute current
state of any item becomes a highly performant, single-index scan:

```sql
WITH RankedAttempts AS (
    SELECT 
        item_id, status, error_message, created_at,
        -- Ranks each item's attempts, prioritizing the newest execution at #1
        ROW_NUMBER() OVER (PARTITION BY item_id ORDER BY created_at DESC) as rank
    FROM import_log
)
SELECT item_id, status, error_message, created_at
FROM RankedAttempts
WHERE rank = 1 AND status = 'FAILED';
```

### 2. Error Categorization & Signature Grouping
To transform raw error logs into a functional operations control center, unparsed error messages
must be extracted into structured, indexed, and groupable columns. This allows teams to identify
the exact blast radius of an incident and perform targeted bulk remediation.

#### The Database Schema Matrix
The single log table should isolate specific metadata attributes alongside the technical payload:

* **`log_id` (VARCHAR PK):** Unique identifier for the specific execution attempt.
* **`item_id` (VARCHAR Indexed):** The business entity identifier (e.g., `INVOICE-9921`).
* **`workspace_id` (VARCHAR):** Identifies the originating business unit or client environment.
* **`recipe_id` (VARCHAR):** Identifies the specific workflow execution that failed.
* **`error_category` (VARCHAR Indexed):** High-level logical bucket (e.g., `API_TIMEOUT`).
* **`error_type` (VARCHAR):** Exact exception class or code (e.g., `Net::ReadTimeout`, `HTTP_503`).
* **`target_system` (VARCHAR):** The application that failed (e.g., `Salesforce`, `NetSuite`).
* **`raw_error_json` (TEXT/JSON):** The complete unparsed JSON or stack trace for developers.

#### The Decoupled Rules Engine Pattern
Never hardcode string-parsing algorithms directly inside core business workflows. Route raw error
strings through a centralized Rules Engine that evaluates payloads against an external lookup table.

1. **Deterministic Prioritization:** The engine evaluates structured headers first (HTTP status
   codes like 502, 504), then executes regex string-matching against the unparsed error body.
2. **AI Fallback Vector:** For novel errors completely unrecognized by the regex matrix, the system
   flags the row as `UNCLASSIFIED_SYSTEM_ERROR` and passes the payload asynchronously to an LLM
   to suggest a new category classification.
3. **Continuous Training Loop:** When vendors change their error vocabularies, engineers append
   a single regex row to the configuration table. The classification engine instantly adapts
   across all pipelines without a code redeployment.