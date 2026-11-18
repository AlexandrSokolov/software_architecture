# 08_Operability / 3_Instrumentation

**Question it answers:** How does telemetry get from my Java/Spring app to Prometheus, Grafana, Tempo, Loki —
and which approach do I choose?

**Sources:** your observability learning path (modules 0, 2–8, 10); SRE with Java Microservices — ch. 2
(Micrometer parts), ch. 3 (types of tracing instrumentation); Practical OpenTelemetry; Learning OpenTelemetry;
Cloud Native Spring in Action (observability chapter).

## Topics

- **Delivery models** — pull (Prometheus scrapes `/metrics`) vs push (OTLP).
- **OpenTelemetry as a standard** — API, SDK, OTLP protocol, Collector, semantic conventions.
- **Types of tracing instrumentation** — manual, agent, framework, service mesh, blended.
- **JMX exporter** — metrics without code; where it's still used (Kafka, Tomcat, legacy).
- **Prometheus Java client** — what doing it by hand costs; what Micrometer does for you.
- **Micrometer** — `MeterRegistry`, `@Timed`/`@Counted`, common tags, naming conversion
  (`http.server.requests` → `http_server_requests_seconds_*`), meter filters, binders, platform vs application
  metrics; vendor switch = registry only.
- **OTel Collector** — receivers → processors → exporters; why send to the Collector, not straight to the
  backend.
- **OTel Java agent** — zero-code; env configuration; limits (native image, other agents, startup cost).
- **OTel Spring Boot starter (community)** — agent vs starter mechanism; when to choose it.
- **Spring Boot 4 starter** — `spring-boot-starter-opentelemetry`, Observation API (`@Observed`: one
  observation → metric + span), Micrometer Tracing bridge; not to confuse with the community starter.
- **Backends** — Prometheus, Tempo/Jaeger, Loki, Grafana; `grafana/otel-lgtm` for local practice.
- **Choosing an approach** — the comparison table from module 10: "in a new Spring Boot project I'd pick ___
  because ___".

## Existing files to move here

- `5.5.3_Tracing_Tools.md`.

## Not here

- What to measure and alert on → `1_Monitoring`.
- How to read traces and debug → `2_Observability`.
