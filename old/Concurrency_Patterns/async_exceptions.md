The Mechanics of @Async Exceptions
When you call a method annotated with @Async, Spring intercepts that call.

The Invoker (Coordinator Thread): Hands the task to the worker pool and immediately returns. It has already moved on to the next line of code.

The Worker Thread: Starts executing your while loop.

If the Worker Thread throws an Exception:
Because the method returns void, there is no return channel back to the Coordinator Thread. The Coordinator is long gone.
By default, Spring catches the exception using its internal SimpleAsyncUncaughtExceptionHandler. This handler simply writes the exception message and method signature to your application logs, and then the worker thread silently dies.

The TaskMonitorService will have no idea that the chunk crashed. It will just see that the chunk finished (because your finally { onChunkComplete.run(); } still executes), but it will assume everything went fine.


How to Fix It (The Solution)
You are exactly right on the first instinct: Just break the loop and log the error. But because we have a TaskMonitorService tracking the overall health of this job, we also need to tell the monitor that a fatal error occurred.

Instead of throw new IllegalStateException(...), you should handle the fatal error locally, notify the monitor via a callback, and break the loop gracefully.

Here is how you adapt your code:

1. Add a Fatal Error Callback
   Pass one more parameter to your executor: a BiConsumer<Integer, String> onFatalError. This allows the worker to tell the Monitor, "Hey, JobType X just crashed completely."

2. Update the Worker Logic

```java
@Async(WORKER_POOL_NAME)
public void processChunk(
  Integer jobTypeId,
  BiConsumer<DseObjectDTO, Integer> onSuccess,
  BiConsumer<DseObjectDTO, Exception> onFailure,
  BiConsumer<Integer, String> onFatalError, // <-- New Callback
  Runnable onChunkComplete) {

  try {
    Set<Integer> handledIds = new HashSet<>();

    while (true) {
      if (Thread.currentThread().isInterrupted()) break;

      List<DseObjectDTO> page = jobsSearchService.searchJobsFstPage(jobTypeId);

      if (page.isEmpty()) {
        break;
      }

      Set<Integer> previouslyHandledButFoundAgain = page.stream()
        .map(DseObjectDTO::getInstanceId)
        .filter(handledIds::contains)
        .collect(Collectors.toSet());

      if (!previouslyHandledButFoundAgain.isEmpty()) {
        // 1. Build the error message
        String errorMsg = "FATAL: Infinite loop detected for JobType " + jobTypeId +
          ". Jobs failed to change status and reappeared. IDs: " + 
          previouslyHandledButFoundAgain.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(";"));
        
        // 2. Log it immediately for Ops/Devs
        log.error(errorMsg);
        
        // 3. Notify the Monitor so it knows the overall job has failed parts
        onFatalError.accept(jobTypeId, errorMsg);
        
        // 4. Gracefully break the loop instead of throwing an uncatchable exception
        break; 
      }

      page.forEach(job -> {
        try {
          handledIds.add(job.getInstanceId());
          // The REST call happens here
          onSuccess.accept(job, mapsSyncService.syncInvoiceApproval(job.getInstanceId()));            
        } catch (Exception e) {
          // If the REST call fails for ONE item, it is caught here.
          // Because it failed, the status won't change, and it will be caught 
          // by the infinite loop checker on the next page fetch!
          onFailure.accept(job, e);
        }
      });
    }
  } catch (Exception unexpectedError) {
    // Catch anything else (like the jobsSearchService REST call failing completely)
    log.error("Unexpected error in worker thread for JobType {}", jobTypeId, unexpectedError);
    onFatalError.accept(jobTypeId, unexpectedError.getMessage());
  } finally {
    onChunkComplete.run();
  }
}
```

By doing this, you keep full control over your application state. 
No exceptions disappear into the void, the logging is explicit, and your monitoring dashboard will accurately reflect the disaster.