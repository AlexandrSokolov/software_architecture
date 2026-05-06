

### Original

```java
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TaskMonitorService {

    private final ReentrantLock lock = new ReentrantLock();
    private final HeavyTaskExecutor executor;

    // Real-time thread-safe state
    private volatile TaskStatus status = TaskStatus.notStarted;
    private volatile Instant startTime = null;
    private volatile Instant completeTime = null;
    private final AtomicInteger processedItems = new AtomicInteger(0);
    
    // CopyOnWriteArrayList prevents ConcurrentModificationException on the GET endpoint
    private final List<FailedItem> failedItems = new CopyOnWriteArrayList<>();

    public TaskMonitorService(HeavyTaskExecutor executor) {
        this.executor = executor;
    }

    public boolean tryTriggerTask() {
        if (lock.tryLock()) {
            try {
                // Double-check inside the lock
                if (this.status == TaskStatus.isActive) {
                    return false; 
                }

                // 1. Atomic State Reset
                this.status = TaskStatus.isActive;
                this.startTime = Instant.now();
                this.completeTime = null;
                this.processedItems.set(0);
                this.failedItems.clear();

                // 2. Hand off to background thread
                executor.executeTask(this);
                return true;

            } finally {
                // We release the lock immediately so the POST thread doesn't hang.
                // The volatile 'status' prevents new executions.
                lock.unlock();
            }
        }
        return false;
    }

    public TaskStatsResponse getStats() {
        return new TaskStatsResponse(
            status, startTime, completeTime, processedItems.get(), List.copyOf(failedItems)
        );
    }

    // --- Callbacks for the background thread ---

    public void incrementSuccess() {
        processedItems.incrementAndGet();
    }

    public void addFailure(String id, String reason) {
        failedItems.add(new FailedItem(id, reason));
    }

    public void finishTask(boolean success) {
        this.completeTime = Instant.now();
        this.status = success ? TaskStatus.isStopped : TaskStatus.failed;
    }
}
```

Alexander, your intuition here is structurally perfect. 
You are asking exactly the right question: 
"Why manage an extra status flag for coordination when a Lock is literally designed to represent 'busy'?"

Here is why I couldn't do it that way in the previous code, and how we can achieve exactly what you want by changing the Java primitive.

The Java Problem: ReentrantLock is "Thread-Owned"
In Java, a ReentrantLock is strictly bound to the thread that acquires it.
If the Tomcat HTTP Thread calls lock.tryLock(), then the Tomcat HTTP Thread must call lock.unlock().

If we pass the execution to an @Async thread and tell the async thread to unlock it when it finishes, 
Java will throw an IllegalMonitorStateException. You cannot pass a ReentrantLock between threads.

The HTTP Problem: Why we can't just make the check @Async
If we move the tryLock() check entirely into the @Async thread, the Tomcat HTTP thread fires off the request and immediately returns 202 Accepted to the client. It doesn't wait to find out if the lock was acquired.
Because of this, you lose the ability to return your 409 Conflict (Busy) response to the user.


The Solution: Use a Semaphore instead of a Lock

To do exactly what you are suggesting—where the "Lock" is held for the entire duration 
of the async task and serves as the single source of truth without extra flags—we must use a Semaphore with 1 permit.


A Semaphore does not care which thread acquires or releases it. The HTTP thread can acquire it, and the Async thread can release it.

Look at how much cleaner this makes the code. It removes the double-check and perfectly aligns with your logic:

