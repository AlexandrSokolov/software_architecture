package com.example.monitor.controller;

import com.example.monitor.model.BusyResponse;
import com.example.monitor.model.TaskStatsResponse;
import com.example.monitor.service.TaskMonitorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/task")
public class TaskController {

  private final TaskMonitorService monitorService;
  private static final String STATS_URL = "/api/task/stats";

  public TaskController(TaskMonitorService monitorService) {
    this.monitorService = monitorService;
  }

  @PostMapping
  public ResponseEntity<?> triggerTask() {
    boolean started = monitorService.tryTriggerTask();

    if (started) {
      return ResponseEntity
        .accepted()
        .location(URI.create(STATS_URL))
        .build();
    } else {
      return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new BusyResponse("Already-Running", STATS_URL));
    }
  }

  @GetMapping("/stats")
  public ResponseEntity<TaskStatsResponse> getStats() {
    return ResponseEntity.ok(monitorService.getStats());
  }
}