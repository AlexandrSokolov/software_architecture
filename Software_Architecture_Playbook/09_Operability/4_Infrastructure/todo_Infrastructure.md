# 08_Operability / 4_Infrastructure

**Question it answers:** How is my service packaged, deployed, changed and kept running without downtime?
Developer side only, no cluster administration.

**Sources:** SRE with Java Microservices — ch. 1 (Delivery), ch. 5 (Safe, Multicloud Continuous Delivery);
Cloud Native Spring in Action.

## Topics

- **Containers** — images, Docker Compose for local multi-service setups; JVM in a container (memory limits).
- **Kubernetes basics (developer view)** — pod, deployment, service, config and secrets, resource
  requests/limits.
- **Health probes** — readiness vs liveness; what goes wrong when they are mixed up.
- **Graceful shutdown and draining** — finishing in-flight requests and messages on deploy.
- **No single-machine dependency** — take any node down without downtime; a single Compose host is one machine,
  decide that on purpose.
- **Packaging for the cloud** — IaaS vs container schedulers.
- **Delivery pipelines** — CI/CD stages; infrastructure as code.
- **Deployment strategies** — delete + none, highlander, rolling, blue/green, canary; automated canary analysis.

## Not here

- Monitoring of the cluster itself (ServiceMonitor, Prometheus Operator) — deferred.
- Retries, circuit breakers, load balancing between services → `05_Reliability/3_Resilience`.
- Old and new code running side by side during a rolling deploy → `07_Integration/1_Encoding_&_Evolution`.
