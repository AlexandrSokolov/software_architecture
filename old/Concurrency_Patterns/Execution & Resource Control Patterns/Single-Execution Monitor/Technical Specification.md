# Single-Execution Task Monitor

## I. Overview

The system provides a **controlled execution environment** for a single, resource-intensive **Heavy Task**.

Its responsibilities are:
- Enforcing **mutual exclusion** at the application level
- Ensuring **non-blocking execution**
- Providing **real-time observability** into task state, timing, and results

At any given time, **only one Heavy Task may be running**.

---

## II. Functional Requirements

### 1. Mutual Exclusion

- Only **one instance** of the Heavy Task may be active at any time.
- The system must implement the **Balking Pattern**:
  - If a task is already running, subsequent start requests must be **rejected**.
  - Requests must **not be queued**.

---

### 2. Execution Lifecycle

#### Triggering
- The task is initiated via an HTTP **POST** request.

#### State Reset
- On a **successful trigger**, all statistics and counters from previous runs must be **fully reset**.

#### Asynchronicity
- Task execution must be **asynchronous** with respect to the HTTP request lifecycle.
- The HTTP request must return immediately after task startup.

---

### 3. Observability & Reporting

#### Status Tracking

The system must track exactly four states:

- `notStarted`
- `isActive`
- `isStopped` (includes full and partial success)
- `failed`

#### Time Tracking

- `startTime` — when the task begins execution
- `completeTime` — when execution ends (success or failure)

#### Error Aggregation

- If the task completes with partial success:
  - Successfully processed items must still be reported.
  - Failed items must be reported with explicit failure reasons.
- On full success:
  - `failedItems` must be present and explicitly empty.

---

## III. Interface Specification (API)

### POST `/api/task`

#### Scenario: System Idle

**Action**
- Clears previous statistics
- Transitions state to `isActive`
- Starts the Heavy Task in a background thread

**Response**
- `202 Accepted`

**Headers**
```
Location: /api/task/stats
```

---

#### Scenario: System Busy

**Action**
- Rejects execution request

**Response**
- `409 Conflict`

**Body**
```json
{
  "status": "Already-Running",
  "statisticsUrl": "/api/task/stats"
}
```

---

### GET `/api/task/stats`

**Response**
- `200 OK`

**Body**
```json
{
  "status": "isActive | isStopped | failed | notStarted",
  "startTime": "ISO-8601",
  "completeTime": "ISO-8601 | null",
  "processedItems": 150,
  "failedItems": [
    { "id": "item_1", "reason": "Timeout" },
    { "id": "item_2", "reason": "Invalid Format" }
  ]
}
```

---

## IV. Technical Design

### 1. Concurrency Management

#### Monitor / Guard

- Implemented at the **service layer**
- Uses `java.util.concurrent.locks.ReentrantLock`

#### Balking Implementation

- The POST handler uses `lock.tryLock()`
- If the lock cannot be acquired:
  - The request is rejected immediately (`409 Conflict`)

---

### 2. State Management (In-Memory)

#### Thread Safety

- All task state and statistics are maintained **purely in memory**.
- The statistics/state object must be:
  - Declared as `volatile`, **or**
  - Managed via an `AtomicReference`

This guarantees visibility of updates made by the background task to the GET endpoint.

#### Atomic Reset

- Resetting state must occur:
  - Inside the **lock’s critical section**
  - **Before** the background thread is started

This ensures a clean execution context for every run.

---

### 3. Execution Environment

#### Threading Model

- The Heavy Task must execute outside the web container threads.
- Supported approaches include:
  - Spring `@Async`
  - A dedicated `ThreadPoolTaskExecutor`

#### Failure Safety

- Task execution must be wrapped in a `try / catch / finally` block.
- The `finally` block must:
  - Set `completeTime`
  - Transition the state out of `isActive`

This must occur regardless of:
- Successful completion
- Partial success
- Catastrophic failure
