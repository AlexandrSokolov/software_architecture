## Claude

Here's how I'd group it. ★ marks topics that come up often in interviews; that's my judgment, not a count.

**Books:** Richardson, *Microservices Patterns*. Newman, *Building Microservices* (2nd ed.) and *Monolith to Microservices*. Evans, *Domain-Driven Design*. Nygard, *Release It!*. Hohpe & Woolf, *Enterprise Integration Patterns*. Skelton & Pais, *Team Topologies*. DDIA.

**1. Splitting the system** → `4_Microservices_and_Sagas`
- ★ Monolith vs microservices vs modular monolith: when to split — Newman, Richardson
- ★ Service boundaries: business capability, bounded context — Evans, Newman, Richardson
- ★ Database per service — Richardson
- ★ Strangler fig, migrating off a monolith — Newman (*Monolith to Microservices*)
- Distributed monolith (anti-pattern) — Newman

**2. Communication** → `4_Microservices_and_Sagas`
- ★ Sync vs async calls (REST/gRPC vs messaging) — Newman, Richardson
- ★ API gateway, backend for frontend — Richardson, Newman
- Service discovery, client-side vs server-side — Richardson

**3. Data consistency across services** → `4_Microservices_and_Sagas`
- ★★ Saga: orchestration vs choreography, compensating actions — Richardson, Newman
- ★ Transactional outbox, CDC (the dual-write fix) — Richardson, DDIA
- ★ 2PC/XA and why teams avoid it — DDIA, Richardson
- ★ Idempotent consumer — Richardson, Hohpe & Woolf
- Saga isolation problems (semantic lock, commutative updates) — Richardson

**4. Querying across services** → `4_Microservices_and_Sagas`
- API composition — Richardson
- CQRS read models — Richardson, DDIA (cards in `2_Event_Driven_Architecture`)

**5. Resilience** → `5_Resilience_and_Observability`
- ★ Timeouts, retries with backoff — Nygard, Newman
- ★ Circuit breaker — Nygard, Richardson
- ★ Cascading failures — Nygard
- Bulkhead, rate limiting, load shedding — Nygard

**6. Observability** → `5_Resilience_and_Observability`
- ★ Distributed tracing, correlation IDs — Newman, Richardson
- Health checks: liveness vs readiness — Richardson
- Log aggregation, metrics — Newman

**7. Testing and deployment**
- ★ Consumer-driven contract tests (Pact, Spring Cloud Contract) — Newman, Richardson
- Independent deploys, canary, blue-green — Newman
- Containers, Kubernetes, service mesh — Newman

**8. Security**
- Auth across services: OAuth2, JWT propagation — Newman, Richardson

**9. Teams**
- Conway's law, team ownership — Newman, Skelton & Pais

The top interview questions, in my judgment: saga, outbox/dual write, monolith vs microservices, circuit breaker, idempotency, API gateway.


## Merged iwth Gemini:

Merged and sorted by interview popularity. The popularity labels are Gemini's scale combined with my stars; both are judgment, not data. I also added the folder each group belongs to.

**1. Data consistency across services** — very high → `4_Microservices_and_Sagas`
- Topics: database per service; saga (orchestration vs choreography, compensating actions); 2PC/XA vs eventual consistency; saga isolation problems
- Books: Richardson, DDIA

**2. Dual write, outbox, CDC** — very high → `4_Microservices_and_Sagas`; dual write itself → `06_Architecture_Traps`
- Topics: the dual-write problem; transactional outbox; CDC and log tailing (Debezium); idempotent consumer
- Books: Richardson, DDIA, Hohpe & Woolf

**3. Resilience** — very high → `5_Resilience_and_Observability`
- Topics: cascading failures; timeouts and timeout budgets; retries with backoff and jitter; circuit breaker; bulkhead; rate limiting; load shedding
- Books: Nygard, Richardson, Newman

**4. Decomposition** — high → `4_Microservices_and_Sagas`
- Topics: monolith vs microservices vs modular monolith; bounded contexts; cohesion and coupling; distributed monolith; strangler fig
- Books: Newman (both books), Evans, Richardson

**5. Communication** — high → `4_Microservices_and_Sagas`
- Topics: sync vs async (REST/gRPC vs Kafka/RabbitMQ); API gateway; backend for frontend; service mesh. Schema evolution is already done in your 3.6 files.
- Books: Newman, Richardson

**6. Messaging in practice** — high → split three ways:
- `1_Enterprise_Integration_Patterns`: dead letter queue, poison pill
- `3_Batch_and_Stream_Processing`: ordering per partition, consumer lag
- `2_Event_Driven_Architecture`: event notification vs event-carried state transfer
- Books: Hohpe & Woolf; Shapira et al., *Kafka: The Definitive Guide*

**7. Workflows** — medium to high → `4_Microservices_and_Sagas`
- Topics: workflow engines (Temporal, Cadence, Step Functions); long-running processes; transient vs permanent failures
- Books: DDIA (Durable Execution, 187), Richardson

**8. Querying across services** — medium → `4_Microservices_and_Sagas`; CQRS → `2_Event_Driven_Architecture`
- Topics: API composition; CQRS read models; GraphQL federation; N+1 calls across services
- Books: Richardson, Newman; Giroux, *Production-Ready GraphQL*

**9. Observability** — medium → `5_Resilience_and_Observability`
- Topics: distributed tracing and correlation IDs; central logging; metrics; liveness vs readiness checks
- Books: Newman, Richardson; Google's *Site Reliability Engineering*

**10. Security** — medium to high → no folder yet
- Topics: JWT vs opaque tokens; OAuth2/OIDC; gateway as trust boundary; passing user identity through services; mTLS
- Books: Siriwardena & Dias, *Microservices Security in Action*; Newman

**11. State and scaling** — high (system design) → no folder yet
- Topics: stateless vs stateful services; sticky vs shared sessions; autoscaling; WebSockets at scale
- Books: Alex Xu, *System Design Interview*

**12. Testing** — medium → no folder yet
- Topics: consumer-driven contract tests (Pact, Spring Cloud Contract); fewer end-to-end tests; chaos engineering
- Books: Newman, Richardson; Rosenthal & Jones, *Chaos Engineering*

**13. Deployment** — low to medium → no folder yet
- Topics: Kubernetes; blue-green; canary; traffic shadowing
- Books: Burns, Beda & Hightower, *Kubernetes: Up & Running*; Newman

**14. Serverless** — low to medium → `4_Microservices_and_Sagas`
- Topics: functions as a service; cold starts; Lambda vs an always-on container
- Books: DDIA (21); Sbarski, *Serverless Architectures on AWS*

**15. Service discovery and config** — low → `4_Microservices_and_Sagas`
- Topics: client-side vs server-side discovery; registries; external config
- Books: Richardson; Carnell, *Spring Microservices in Action*

**16. Teams** — low → `4_Microservices_and_Sagas`
- Topics: Conway's law, team ownership
- Books: Newman; Skelton & Pais, *Team Topologies*

**Two of Gemini's groups aren't microservices topics.** They're general system design:
- **Sharding** (consistent hashing, hot keys) → `04_Distributed_Data/7_Partitioning`, row 12 of your plan
- **Caching** (cache-aside, write-through, stampede, CDN) → no folder. Only stampede and cache-vs-DB staleness are planned, in `06_Architecture_Traps`.

Security, state and scaling, testing, deployment and caching have no folder in your structure.