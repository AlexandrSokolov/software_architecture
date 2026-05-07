package com.monitor.service;

import com.config.ConfigurationSerice;
import com.restclients.dse.dto.DseObjectDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.monitor.config.AsyncConfig.COORDINATOR_POOL_NAME;

@Service
public class JobBatchCoordinator {

  @Autowired
  JobTypeWorker jobTypeWorker;

  @Autowired
  ConfigurationSerice configurationSerice;

  @Async(COORDINATOR_POOL_NAME)
  public void executeTask(
    BiConsumer<DseObjectDTO, Integer> onSuccess,
    BiConsumer<DseObjectDTO, Exception> onJobFailure,
    BiConsumer<Integer, String> onFatalError,
    Runnable onComplete) {

    int totalChunks = configurationSerice.getJobTypeId2TechName().size();

    if (totalChunks == 0) {
      onComplete.run();
      return;
    }

    AtomicInteger remainingChunks = new AtomicInteger(totalChunks);

    configurationSerice.getJobTypeId2TechName().forEach((jobTypeId, jobTypeTechName) ->
      jobTypeWorker.processJobType( // Renamed from processChunk
        jobTypeId,
        onSuccess,
        onJobFailure,
        onFatalError,
        () -> {
          if (remainingChunks.decrementAndGet() == 0) {
            onComplete.run(); // All chunks done, trigger final cleanup
          }
        }
      )
    );
  }

}
