# 08_Operability / 2_Observability

**Question it answers:** Why is the system behaving like this? Debugging unknown problems, especially across
several services.

**Sources:** SRE with Java Microservices — ch. 3 (Debugging with Observability); Observability Engineering;
Learning OpenTelemetry (concepts).

## Topics

- **Three pillars… or two?** — logs, traces, metrics: what each gives; which telemetry for which question.
- **Logs** — structured logs, correlation ID, `traceId` in every log line.
- **Distributed trace anatomy** — trace, span, parent/child, attributes, context propagation (`traceparent`,
  W3C Trace Context).
- **Tracing async and event flows** — context in message headers; producer/consumer spans (existing
  `5.5.2_Event_Observability.md` moves here).
- **Sampling** — none, rate-limiting, probabilistic, boundary (head vs tail); what sampling hides from anomaly
  detection.
- **Correlation between signals** — metric → trace (exemplars), trace → logs.
- **Tracing a monolith** — is it worth it.
- **Debugging scenario** — latency spike → trace → failing span → log line (multi-service practice project).
- **High-cardinality events** — "unknown unknowns", why observability needs rich context (Observability
  Engineering).

## Existing files to move here

- `5.5.1_Distributed_Tracing.md`, `5.5.2_Event_Observability.md`.

## Not here

- Agents, starters, Collector, tracing backends setup → `3_Instrumentation`.
- Health metrics and alerts → `1_Monitoring`.
