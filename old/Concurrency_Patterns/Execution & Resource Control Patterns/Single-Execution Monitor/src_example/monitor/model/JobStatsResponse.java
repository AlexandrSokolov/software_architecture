package com.monitor.model;

import java.time.Instant;
import java.util.List;

public record JobStatsResponse(
  TaskStatus status,
  Instant startTime,
  Instant completeTime,
  int totalProcessed,
  List<ProcessedJob> processedJobs,
  List<FailedJob> failedJobs,
  List<FatalError> fatalErrors
) {
 }
