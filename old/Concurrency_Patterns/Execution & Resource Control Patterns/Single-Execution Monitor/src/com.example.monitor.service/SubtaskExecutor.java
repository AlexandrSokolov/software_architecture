package com.example.monitor.service;

import com.example.monitor.model.Item;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
public class SubtaskExecutor {

  // Execution strictly isolated to the Worker Pool
  @Async("workerPool")
  public void processChunk(
    List<Item> chunk,
    Consumer<Item> onSuccess,
    BiConsumer<Item, Exception> onFailure,
    Runnable onChunkComplete) {

    try {
      for (Item item : chunk) {
        if (Thread.currentThread().isInterrupted()) break;

        try {
          // Simulate heavy processing
          Thread.sleep(100);

          if (item.id() % 10 == 0) {
            throw new RuntimeException("Simulated processing exception");
          }
          onSuccess.accept(item);

        } catch (Exception e) {
          onFailure.accept(item, e);
        }
      }
    } finally {
      // Signals back to the AtomicInteger in the coordinator
      onChunkComplete.run();
    }
  }
}