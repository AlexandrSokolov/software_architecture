import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class SubtaskExecutor {

  @Async("workerPool")
  public void processJobType(
    Integer jobTypeId,
    Consumer<ProcessedJob> onSuccess,
    BiConsumer<Item, Exception> onFailure,
    Runnable onChunkComplete) {

    try {
      // Memory for Requirement 3: Tracking what we just processed
      Set<Integer> previousPageIds = new HashSet<>();

      // Requirement 1 & 2: Loop until empty
      while (true) {
        if (Thread.currentThread().isInterrupted()) break;

        // Always fetch the "First Page"
        List<Item> page = jobsSearchService.searchJobsFstPage(jobTypeId);

        // Exit condition: No more jobs left for this type!
        if (page.isEmpty()) {
          break;
        }

        // Extract IDs from the current page
        Set<Integer> currentPageIds = page.stream()
          .map(Item::id)
          .collect(Collectors.toSet());

        // Requirement 3 (Safety Check): Did any of these jobs appear in the LAST page?
        // If yes, our status update failed, and we are stuck in an infinite loop.
        boolean isStuck = currentPageIds.stream().anyMatch(previousPageIds::contains);

        if (isStuck) {
          String errorMsg = "FATAL: Infinite loop detected for JobType " + jobTypeId +
            ". Jobs failed to change status and reappeared.";
          System.err.println(errorMsg);
          // Abort fetching any more pages for this job type
          break;
        }

        // Process the current page
        for (Item item : page) {
          try {
            // 1. Do the heavy work
            // 2. CHANGE THE JOB STATUS IN THE DB HERE

            ProcessedJob job = new ProcessedJob(item.id(), item.id() + 5000);
            onSuccess.accept(job);

          } catch (Exception e) {
            onFailure.accept(item, e);
          }
        }

        // Update our memory before the next fetch
        previousPageIds = currentPageIds;
      }

    } finally {
      // Guaranteed to release the Semaphore in the Monitor,
      // even if we aborted due to the infinite loop check!
      onChunkComplete.run();
    }
  }
}