package com.monitor.service;

import com.monitor.model.*;
import com.restclients.dse.dto.DseObjectDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

@Service
public class JobTypesMonitorService {

  // 1 Permit = Single-Execution Balking pattern
  private final Semaphore executionGuard = new Semaphore(1);
  @Autowired
  JobBatchCoordinator coordinator;

  // Real-time thread-safe state
  private volatile TaskStatus status = TaskStatus.notStarted;
  private volatile Instant startTime = null;
  private volatile Instant completeTime = null;

  // Thread-safe collections for concurrent workers
  private final Collection<ProcessedJob> processedJobs = new ConcurrentLinkedQueue<>();
  private final Collection<FailedJob> failedJobs = new ConcurrentLinkedQueue<>();
  private final Collection<FatalError> fatalErrors = new ConcurrentLinkedQueue<>();


  public boolean tryTriggerTask() {
    if (executionGuard.tryAcquire()) {

      // 1. Reset State atomically before background processing starts
      this.status = TaskStatus.isActive;
      this.startTime = Instant.now();
      this.completeTime = null;
      this.processedJobs.clear();
      this.failedJobs.clear();
      this.fatalErrors.clear();

      // 2. Pass behavior to the coordinator via Method References
      coordinator.executeTask(
        this::handleSuccess,
        this::handleJobFailure,
        this::handleFatalError,
        this::markCompleted
      );

      return true;
    }
    return false; // Tells the REST controller to return 409 Conflict
  }

  public JobStatsResponse getStats() {
    // Return an immutable snapshot of the current state
    return new JobStatsResponse(
      status, startTime, completeTime,
      processedJobs.size(),
      List.copyOf(processedJobs),
      List.copyOf(failedJobs),
      List.copyOf(fatalErrors)
    );
  }

  // --- Private Callbacks for Event-Driven updates ---

  private void handleSuccess(DseObjectDTO job, Integer syncId) {
    processedJobs.add(new ProcessedJob(job.getOrdinalNumber(), job.getInstanceId(), syncId));
  }

  private void handleJobFailure(DseObjectDTO job, Exception ex) {
    failedJobs.add(new FailedJob(job.getOrdinalNumber(), job.getInstanceId(), ex.getMessage()));
  }

  private void handleFatalError(Integer jobTypeId, String errorMsg) {
    fatalErrors.add(new FatalError(jobTypeId, errorMsg));
  }

  private void markCompleted() {
    this.completeTime = Instant.now();

    // Infer final status strictly based on failures
    if (failedJobs.isEmpty() && fatalErrors.isEmpty()) {
      this.status = TaskStatus.isStopped; // Fully successful
    } else {
      this.status = TaskStatus.failed;    // Partial or total failure
    }

    // Open the gates for the next overall task run
    executionGuard.release();
  }
}
