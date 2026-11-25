# 09_Operability / 4_Infrastructure — pitfalls from practice: Docker, health checks, graceful shutdown

**Question it answers:** What breaks when the service runs in a container — health checks, start order, cold
starts, shutdown — and how do I prove the shutdown is really graceful?

**Source:** `developer-pitfalls.md` (draft v4) §6.25–6.28, §11.2, §12, §14.4–14.5, §14.7 (isolation), §14.8;
login-tracking assessment (`docker-compose.yml`, `docker-compose.debug.yml`, `infrastructure/docker/http-check.sh`,
Dockerfiles, README "Timeouts and graceful shutdown").

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.

---

## 1. Images and Compose

- [ ] **Runtime image only, jar built on the host** — no build tools in the image; non-root user; no `EXPOSE`
      (the port comes from the environment).
- [ ] **JVM options from `JAVA_TOOL_OPTIONS`** — heap as a share of the container memory limit, both decided
      together in compose.
- [ ] **Pin images** — `:latest` gives a different version on every pull; versions in `.env`.
- [ ] **Env-specific values in `.env`** — images, ports, topic names; `docker-compose.yml` the same for everyone.
- [ ] **Fixed `container_name` across compose projects** — the same file from two folders collides.
- [ ] **`depends_on` short form = `service_started`** — can't mix with the long form once one entry needs
      `service_healthy`.
- [ ] **Debug override** — a second compose file merged by service name; JDWP = full remote control, never in
      production; env values are replaced, lists (ports) are merged.
- [ ] **`docker compose pause`** — TCP connects, nothing answers: the perfect "hanging service" for read-timeout
      and shutdown demos.
- [ ] **Kafka in Docker: two listeners** → `08_Integration/6_Message_Brokers/Kafka/todo_Pitfalls_Kafka.md` §6.

## 2. Health checks

- [ ] **Without a healthcheck Docker knows only "process alive"** (`Up`), not "ready".
- [ ] **A healthcheck is a command inside the container** — no built-in HTTP check; `eclipse-temurin` has neither
      curl nor wget → `unhealthy`. Did: shared bash `/dev/tcp` script mounted into all containers + an `x-`
      YAML anchor for timing.
- [ ] **Check readiness, not full health** — no external dependencies in the check.
- [ ] **Plain Docker never restarts an `unhealthy` container** — the status is for `compose ps` and
      `depends_on: service_healthy`.
- [ ] **Healthcheck timing must cover a cold start** — Keycloak warm ~35 s, fresh ~80 s (augmentation);
      `start_period` too short → "dependency failed to start". Did: `start_period: 120s` for Keycloak.
- [ ] **Liveness vs readiness** — liveness depending on Kafka/DB restarts every instance in an outage and fixes
      nothing; external dependencies at most in readiness.
- [ ] **Separate management port** — own embedded server and thread pool: health answers even when business
      requests exhaust Tomcat's threads (otherwise an overloaded pod looks dead and gets restarted). Security
      side → `11_Security/todo_Pitfalls_Security.md`.
- [ ] **Inspect** — `docker inspect <c> --format '{{json .State.Health}}'`.

## 3. Start order

- [ ] **Compose waits, the application doesn't** — `service_healthy` keeps the container from starting; a local
      convenience. Kubernetes has no start order → the service must survive missing dependencies →
      `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md` §4.

## 4. Graceful shutdown

- [ ] **The logic is spread over three layers** — listener container, Spring lifecycle, runtime (Docker /
      Kubernetes); each must wait longer than the one inside it.
- [ ] **Timeout chain** — worst case per record → listener `shutdownTimeout` → `timeout-per-shutdown-phase` →
      runtime grace period (Docker `stop_grace_period` default 10 s, Kubernetes 30 s).
      Did: 37.25 s → 40 s → 45 s → 50 s, one README table.
- [ ] **ExecutorService analogy** — `shutdown()` = stop polling, finish the current task; `awaitTermination` =
      listener timeout; `shutdownNow()` = SIGKILL. Unprocessed records are not lost: not committed → re-polled.
- [ ] **Default stop processes the whole poll** — `immediate-stop: true`.
- [ ] **Listener shutdown timeout has no Boot property** — `ContainerCustomizer`.
- [ ] **The worst case includes every blocking step** — publish (`max.block.ms` + `delivery.timeout.ms`), DB,
      token → `06_Resilience/todo_Pitfalls_Kafka_Consumer_Resilience.md` §2.
- [ ] **Changing `stop_grace_period` applies only to a recreated container** — check
      `docker inspect <c> --format '{{.Config.StopTimeout}}'`.
- [ ] **Proving it** — slow stub (pause + 503), send an event, `time docker compose stop`; the log shows the
      record finished and published before `partitions revoked`; exit code **143** (SIGTERM, clean) not **137**
      (SIGKILL); after restart lag 0, no reprocessing.
- [ ] **What is lost without it** — no data (at-least-once), but duplicates and half-done side effects.

## 5. Cold start from Git

- [ ] **`git clone` into a new folder, build, `compose up`** — only committed files count; `.env` in Git (or
      `.env.example` + README step); uncommitted fixes (healthcheck timings) mask problems.

---

## Covered elsewhere

- Containers, Kubernetes, probes, deployment strategies → `todo_Infrastructure.md`.
- Secrets in `.env` and in containers → `11_Security/todo_Pitfalls_Security.md`.
