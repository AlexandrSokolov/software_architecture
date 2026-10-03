package com.monitor.controller;

import com.monitor.model.StatusResponse;
import com.monitor.model.JobStatsResponse;
import com.monitor.service.JobTypesMonitorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/sync")
public class JobSyncController {
  private final JobTypesMonitorService monitorService;

  public JobSyncController(JobTypesMonitorService monitorService) {
    this.monitorService = monitorService;
  }

  /**
   * Triggers the background processing task.
   */
  @PostMapping("/start")
  public ResponseEntity<StatusResponse> startJobSync() {
    boolean isStarted = monitorService.tryTriggerTask();

    String statsUrl = buildStatsUrl();

    if (isStarted) {
      // 202 ACCEPTED: The request is valid and processing has started in the background.
      return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new StatusResponse("Job sync process started successfully.", statsUrl));
    } else {
      // 409 CONFLICT: The client tried to start a task, but one is already running.
      return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new StatusResponse("A job sync process is already currently running.", statsUrl));
    }
  }

  /**
   * Retrieves the real-time status and statistics of the task.
   */
  @GetMapping("/stats")
  public ResponseEntity<JobStatsResponse> getJobStats() {
    // 200 OK: Returns the JSON snapshot of the thread-safe lists
    return ResponseEntity.ok(monitorService.getStats());
  }

  protected String buildStatsUrl() {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
      .path("/sync/stats")
      .toUriString();
  }
}
