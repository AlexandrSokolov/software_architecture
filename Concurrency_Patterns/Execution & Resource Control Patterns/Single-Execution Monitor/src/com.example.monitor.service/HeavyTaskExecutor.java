package com.example.monitor.service;

import com.example.monitor.model.Item;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
public class HeavyTaskExecutor {

  private final SubtaskExecutor subtaskExecutor;

  public HeavyTaskExecutor(SubtaskExecutor subtaskExecutor) {
    this.subtaskExecutor = subtaskExecutor;
  }

  // Tomcat thread hands off to Coordinator Pool and returns immediately
  @Async("coordinatorPool")
  public void executeTask(
    Map<String, List<Item>> dataChunks,
    Consumer<Item> onSuccess,
    BiConsumer<Item, Exception> onFailure,
    Runnable onComplete) {

    int totalChunks = dataChunks.size();

    if (totalChunks == 0) {
      onComplete.run();
      return;
    }

    AtomicInteger remainingChunks = new AtomicInteger(totalChunks);

    // Coordinator thread iterates and submits chunks to the Worker Pool
    for (Map.Entry<String, List<Item>> entry : dataChunks.entrySet()) {
      List<Item> chunk = entry.getValue();

      subtaskExecutor.processChunk(
        chunk,
        onSuccess,
        onFailure,
        () -> {
          if (remainingChunks.decrementAndGet() == 0) {
            onComplete.run(); // All chunks done, trigger final cleanup
          }
        }
      );
    }
  }
}