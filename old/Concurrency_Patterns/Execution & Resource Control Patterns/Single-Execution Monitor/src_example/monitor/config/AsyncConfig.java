package com.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

  public static final String WORKER_POOL_NAME = "workerPool";
  public static final String COORDINATOR_POOL_NAME = "coordinatorPool";

  // Pool 1: Dedicated solely to coordinating the overarching tasks.
  @Bean(name = COORDINATOR_POOL_NAME)
  public Executor coordinatorPool() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setThreadNamePrefix("Coordinator-");
    executor.initialize();
    return executor;
  }

  // Pool 2: The heavy lifters for the individual chunks.
  @Bean(name = WORKER_POOL_NAME)
  public Executor workerPool() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(16);
    executor.setThreadNamePrefix("Worker-");
    executor.initialize();
    return executor;
  }
}
