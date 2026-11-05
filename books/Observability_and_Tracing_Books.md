# Books — Observability and Tracing

Folder: `05_Integration_and_Event_Driven/5_Observability_and_Tracing/`

## Concepts — read first

- **_Observability Engineering_** — Majors, Fong-Jones, Miranda (O'Reilly, 2022).
  What observability is, why traces and structured events beat plain logs, alerting on SLOs. Start here.
- **_Site Reliability Engineering_ ("Google SRE book")** — Beyer, Jones, Petoff, Murphy (O'Reilly, 2016).
  Free to read at sre.google. How Google runs production systems. For this folder read two chapters:
  "Monitoring Distributed Systems" (four golden signals: latency, traffic, errors, saturation) and
  "Service Level Objectives".

## Tracing in depth

- **_Distributed Tracing in Practice_** — Parker, Spoonhower, Mace, Sigelman, Isaacs (O'Reilly, 2020).
  Sampling, passing the context, what to put in spans. Its tool parts are older than today's OpenTelemetry;
  the concepts still hold. → `5.5.1_Distributed_Tracing.md`

## Tools

- **_Learning OpenTelemetry_** — Young, Parker (O'Reilly, 2024).
  OTel itself: API, SDK, Collector, setup. → `5.5.3_Tracing_Tools.md`

## Also useful

- **_Building Microservices_, 2nd ed.** — Sam Newman (O'Reilly, 2021).
  Chapter "From Monitoring to Observability": correlation IDs, tracing. An easy start.
- **_Release It!_, 2nd ed.** — Michael Nygard (Pragmatic Bookshelf, 2018).
  Mainly a resilience book, but its transparency sections cover logs, metrics and health checks. No depth on tracing.
