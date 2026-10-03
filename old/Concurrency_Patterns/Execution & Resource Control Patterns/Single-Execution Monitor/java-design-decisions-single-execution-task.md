# Java Design Decisions for the Single-Execution Task Solution

This document outlines the **critical Java design decisions** made for this solution, explaining both **what was implemented** and **why those choices were made**.

---

## 1. Concurrency Guard: `Semaphore(1)` instead of `ReentrantLock`

### What we did

We implemented the **Balking Pattern** using a `java.util.concurrent.Semaphore` initialized with **one permit**, completely replacing the previously considered `ReentrantLock`.

### Why we did it

- **Thread ownership constraints**  
  In Java, a `ReentrantLock` must be unlocked by the **same thread** that acquired it. In this architecture, the Tomcat HTTP thread starts the task, while a background `@Async` thread completes it. Using a `ReentrantLock` in this scenario would result in an `IllegalMonitorStateException`.

- **No double-checking required**  
  A `Semaphore` does not enforce thread ownership. Any thread may release the permit. This allowed us to eliminate error-prone checks such as `if (status == isActive)`. The `tryAcquire()` call itself becomes both the synchronization mechanism and the **single source of truth** for determining whether the system is busy.

---

## 2. Threading Model: Dual Pools to Prevent Starvation

### What we did

We defined two explicit Spring-managed thread pools:

- `coordinatorPool` (small, e.g. 2 threads)
- `workerPool` (larger, typically 8–16 threads)

Tasks are routed explicitly to these pools using `@Async("poolName")`.

### Why we did it

- **Avoiding the "Senior Trap" (thread starvation)**  
  With a single thread pool, a coordinator thread could consume one thread, split work into multiple chunks, and then block while waiting for worker threads. If the pool size were insufficient, this would lead to deadlock: the coordinator waiting for workers, and workers waiting for the coordinator to release its thread.

- **Tomcat protection**  
  The Tomcat request thread submits the overall job to the `coordinatorPool` and immediately returns `202 Accepted` to the client. This fully decouples web traffic from background processing and prevents request-thread exhaustion.

---

## 3. Synchronization: Non-Blocking Scatter-Gather using `AtomicInteger`

### What we did

Instead of using blocking synchronization primitives such as `CountDownLatch.await()` or `CompletableFuture.allOf().join()`, we introduced an `AtomicInteger` initialized to the total number of work chunks. Each completed chunk calls `decrementAndGet()`.

### Why we did it

- **Zero idle threads**  
  Blocking primitives force a coordinator thread to wait idly until all workers complete.

- **Self-gathering workers**  
  With an `AtomicInteger`, workers effectively "gather" themselves. The last worker to finish decrements the counter to zero and naturally triggers the final `onComplete.run()` callback. This approach is extremely CPU-efficient and eliminates unnecessary thread blocking.

---

## 4. Communication: Event-Driven Callbacks via Functional Interfaces

### What we did

We passed standard Java functional interfaces from the `TaskMonitorService` down to the `SubtaskExecutor`, including:

- `Consumer<Item>`
- `BiConsumer<Item, Exception>`
- `Runnable`

### Why we did it

- **Strong decoupling**  
  Executors are completely unaware of HTTP semantics, semaphores, or task state tracking. They simply report outcomes: success, failure, or completion.

- **Simplified final state resolution**  
  Because failures are tracked dynamically via callbacks such as `this::handleFailure`, the final `markCompleted` method does not need explicit success or failure input. It determines the final `TaskStatus` solely by checking whether the `failedItems` collection is empty.

---

## 5. Domain Modeling: `Item` Record Encapsulation

### What we did

We wrapped the raw iteration index into an `Item(int id)` record before passing it through the system.

### Why we did it

- **Future-proofing**  
  While the item currently contains only an identifier, future requirements may include payloads, status flags, or metadata. By anchoring the architecture to an `Item` domain object early, method signatures across executors and callbacks remain stable as the domain evolves.

---

This combination of design decisions results in a **resilient, enterprise-grade Spring Boot architecture** that maximizes throughput while strictly enforcing the **single-execution constraint**.
