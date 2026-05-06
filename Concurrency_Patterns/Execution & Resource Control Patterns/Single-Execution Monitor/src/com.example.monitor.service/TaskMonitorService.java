package com.example.monitor.service;

import com.example.monitor.model.FailedItem;
import com.example.monitor.model.Item;
import com.example.monitor.model.TaskStatsResponse;
import com.example.monitor.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TaskMonitorService {

  // 1 Permit = Single-Execution Balking implementation across threads
  private final Semaphore executionGuard = new Semaphore(1);
  private final HeavyTaskExecutor executor;

  // Real-time thread-safe state
  private volatile TaskStatus status = TaskStatus.notStarted;
  private volatile Instant startTime = null;
  private volatile Instant completeTime = null;

  private final AtomicInteger processedItems = new AtomicInteger(0);
  private final List<FailedItem> failedItems = new CopyOnWriteArrayList<>();

  public TaskMonitorService(HeavyTaskExecutor executor) {
    this.executor = executor;
  }

  public boolean tryTriggerTask() {
    if (executionGuard.tryAcquire()) {

      // 1. Reset State atomically before background processing starts
      this.status = TaskStatus.isActive;
      this.startTime = Instant.now();
      this.completeTime = null;
      this.processedItems.set(0);
      this.failedItems.clear();

      // 2. Fetch/Generate the data to process (Simulated 4 chunks here)
      Map<String, List<Item>> dataChunks = generateMockData();

      // 3. Pass behavior to the executor via Method References
      executor.executeTask(
        dataChunks,
        this::handleSuccess,
        this::handleFailure,
        this::markCompleted
      );

      return true;
    }
    return false;
  }

  public TaskStatsResponse getStats() {
    return new TaskStatsResponse(
      status, startTime, completeTime, processedItems.get(), List.copyOf(failedItems)
    );
  }

  // --- Private Callbacks for Event-Driven updates ---

  private void handleSuccess(Item item) {
    processedItems.incrementAndGet();
  }

  private void handleFailure(Item item, Exception ex) {
    failedItems.add(new FailedItem(String.valueOf(item.id()), ex.getMessage()));
  }

  private void markCompleted() {
    this.completeTime = Instant.now();
    // Infer final status strictly based on failures
    this.status = failedItems.isEmpty() ? TaskStatus.isStopped : TaskStatus.failed;
    // Open the gates for the next task run
    executionGuard.release();
  }

  // Helper for simulation
  private Map<String, List<Item>> generateMockData() {
    return Map.of(
      "Chunk1", IntStream.range(1, 25).mapToObj(Item::new).toList(),
      "Chunk2", IntStream.range(25, 50).mapToObj(Item::new).toList(),
      "Chunk3", IntStream.range(50, 75).mapToObj(Item::new).toList(),
      "Chunk4", IntStream.range(75, 100).mapToObj(Item::new).toList()
    );
  }
}