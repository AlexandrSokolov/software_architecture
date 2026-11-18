# 08_Operability / 1_Monitoring

**Question it answers:** Is the system healthy right now? Known failure modes, watched with metrics and alerts.

**Sources:** SRE with Java Microservices — ch. 2 (Application Metrics), ch. 4 (Charting and Alerting);
Prometheus: Up & Running (PromQL, alerting).

## Topics

- **Monitoring vs observability** — "is it healthy?" vs "why is it behaving like this?"; front-door card.
- **Black box vs white box monitoring** — probing from outside vs metrics from inside the app.
- **Metric types (concepts)** — counter, gauge, timer/histogram, distribution summary, long task timer;
  choosing the right one.
- **Dimensional vs hierarchical metrics** — tags/labels vs dotted names.
- **Cardinality** — why `userId` in a tag is dangerous; controlling cost.
- **Latency distributions** — percentiles vs histograms, why client-side percentiles can't be aggregated,
  SLO boundaries; coordinated omission.
- **SLIs for every Java service** — errors, latency, GC pauses, heap, CPU, file descriptors, long-running tasks.
- **Dashboards** — effective charts for SLIs (errors vs successes, top-k), `rate()` interval choice,
  when to stop creating dashboards.
- **PromQL basics** — `rate()`, `sum by`, `histogram_quantile()`; why `rate()` and not the raw counter.
- **Alerting** — alert on user-facing symptoms (SLOs), not causes; forecasting alerts.

## Not here

- SLO / error budget definitions → `03_System_Foundations/2_Nonfunctional_Requirements` (link, don't restate).
- How metrics get out of the app (Micrometer, OTel, exporters) → `3_Instrumentation`.
- Traces and logs → `2_Observability`.
