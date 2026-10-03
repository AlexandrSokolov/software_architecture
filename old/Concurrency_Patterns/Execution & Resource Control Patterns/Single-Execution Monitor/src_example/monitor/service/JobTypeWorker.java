package com.monitor.service;

import com.restclients.dse.dto.DseObjectDTO;
import com.services.JobsSearchService;
import com.services.MapsSyncService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.monitor.config.AsyncConfig.WORKER_POOL_NAME;

@Service
class JobTypeWorker {

  private static final Logger LOG = LogManager.getLogger();

  @Autowired
  JobsSearchService jobsSearchService;

  @Autowired
  MapsSyncService mapsSyncService;

  // Execution strictly isolated to the Worker Pool
  @Async(WORKER_POOL_NAME)
  public void processJobType(
    Integer jobTypeId,
    BiConsumer<DseObjectDTO, Integer> onSuccess,
    BiConsumer<DseObjectDTO, Exception> onJobFailure,
    BiConsumer<Integer, String> onFatalError,
    Runnable onChunkComplete) {

    try {
      // Tracking what we have processed
      Set<Integer> handledIds = new HashSet<>();

      while (true) {
        if (Thread.currentThread().isInterrupted()) break;

        // Always fetch the "First Page"
        List<DseObjectDTO> page = jobsSearchService.searchJobsFstPage(jobTypeId);

        // Exit condition: No more jobs left for this type!
        if (page.isEmpty()) {
          break;
        }

        // Did any of these jobs appear in the previous pages?
        // If yes, our status update failed, and we are stuck in an infinite loop.
        Set<Integer> previouslyHandledButFoundAgain = page.stream()
          .map(DseObjectDTO::getOrdinalNumber)
          .filter(handledIds::contains)
          .collect(Collectors.toSet());

        if (!previouslyHandledButFoundAgain.isEmpty()) {
          throw new IllegalStateException("FATAL: Infinite loop detected for JobType " + jobTypeId +
            ". Jobs failed to change status and reappeared. Ordinal numbers: " +
            previouslyHandledButFoundAgain.stream()
              .map(String::valueOf)
              .collect(Collectors.joining(";")));
        }

        page.forEach(job -> {
          try {
            handledIds.add(job.getOrdinalNumber());
            onSuccess.accept(
              job,
              mapsSyncService.syncInvoiceApproval(job.getInstanceId()));
          } catch (Exception e) {
            onJobFailure.accept(job, e);
          }
        });
      }
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      onFatalError.accept(jobTypeId, e.getMessage());
    }
    finally {
      onChunkComplete.run();
    }
  }

}
