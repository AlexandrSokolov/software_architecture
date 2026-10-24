## 

### What is an event-driven architecture?
<details><summary>Show answer</summary>

Services talk by sending messages (events) through a broker. The sender encodes a message, hands it to the broker,
and doesn't wait; the broker stores it and delivers it to the recipients, which decode it.

</details>

## Gemini 1

check if the next 4 cards do not belong to 6_Encoding_and_Evolution

Message brokers — who encodes, who decodes? Unlike RPC, the sender and recipient are logically decoupled by an intermediary.   The sender (publisher) encodes the message and sends it to a queue or topic.   The broker stores the bytes temporarily (or indefinitely) without enforcing a specific data model.   The recipient (consumer) reads the bytes and decodes them.   Because it is asynchronous, the sender does not wait for a response and does not need to know the recipient's
IP address (no service discovery is needed).   


Message brokers — which compatibility is required? Both backward and forward compatibility are required:   Backward — if a broker stores messages indefinitely (e.g., event sourcing), new consumers might read
messages written by old code months or years ago.   Forward — old and new consumers process messages simultaneously. Also, if a consumer republishes a
message, it must preserve unknown fields to avoid dropping data written by a newer sender.   



Distributed actor frameworks — what are they? A programming model that scales single-process concurrency across multiple nodes by integrating a message broker
directly into the framework (e.g., Akka, Erlang/OTP).   Logic is encapsulated in independent actors that share no local state. They communicate solely by
sending and receiving asynchronous messages, processing one message at a time to avoid thread issues.   


Distributed actors — which compatibility is required? When actors run on different nodes, their messages are transparently encoded into bytes, sent over the network,
and decoded on the other side.   To perform rolling upgrades without downtime, nodes running old versions and new versions will send messages to
each other. Therefore, the encoding format used by the framework must provide both forward and backward
compatibility.

## Claude 1

The next topics appeared from Encoding and Evolution - Event-Driven Architectures

**`05_Integration_and_Event_Driven/2_Event_Driven_Architecture/`**
- **"Broker between services — what does it decouple?"** The one card this excerpt gives. Handles:
    - **Time** — the broker buffers messages and redelivers them.
    - **Who** — no service discovery, one message can reach many recipients, and the sender doesn't care who consumes.
- **"Event sourcing — what must the broker keep?"** It must keep messages forever instead of deleting them after they're read. Add this when you write the Event Sourcing and CQRS cards (Ch. 3, p. 101), which file here too.
- **Gap: costs of event-driven design** (harder tracing, duplicates, ordering, eventual consistency). This excerpt doesn't cover them. Fill in from Ch. 12 or Stopford.

**Elsewhere by topic**
- **Queue vs topic** (one consumer gets it vs all subscribers do) → `05_Integration_and_Event_Driven/1_Enterprise_Integration_Patterns/`. Keep it separate from Kafka consumer groups, which go in `3_Batch_and_Stream_Processing/`, per your "competing consumers ≠ partitioned consumption" rule.
- **Request-response over a broker** (waiting on a reply channel) → `05_Integration_and_Event_Driven/1_Enterprise_Integration_Patterns/`, the request-reply pattern.
- **Broker landscape, and delete-after-read vs keep** — the book compares brokers in Ch. 12:
    - Messaging Systems (489) → `05_Integration_and_Event_Driven/1_Enterprise_Integration_Patterns/`
    - Log-Based Message Brokers (495) → `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/`
- **Durable execution beyond compatibility** (exactly-once, no shared DB transaction, external APIs still need idempotency with unique IDs) → `05_Integration_and_Event_Driven/4_Microservices_and_Sagas/`, per your study plan.
- **Workflow engines for ETL** (Airflow, Dagster) → `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/`, next to Ch. 11's Distributed Job Orchestration. BPMN engines (Camunda) and Temporal stay in `4_Microservices_and_Sagas/`.
- **Idempotency in depth** → `05_Integration_and_Event_Driven/3_Batch_and_Stream_Processing/`, where the book covers it (Ch. 12, exactly-once and idempotency). The idempotent receiver pattern goes in `1_Enterprise_Integration_Patterns/`. The payment scenario card in Ch. 5 should link to both.
- **Timeouts and unknown outcomes** → `04_Distributed_Data/9_Trouble_with_Distributed_Systems/`. The flawed-RPC card and the payment scenario should link there.
- **Schema registry, AsyncAPI, actors** → already in `03_Data_Systems/6_Encoding_and_Evolution/`. Nothing to move.