- The queue pushed you 100 messages ahead with auto-ack, and you crash. How many are lost?

### Auto-ack with 100 pushed ahead — how many lost?
<details><summary>Show answer</summary>

Maybe far more than 100. RabbitMQ pushes messages to consumers ahead of time:

- **Auto-ack** — the broker treats each message as done the moment it sends it, and the prefetch limit doesn't apply:
  it sends as fast as the client takes them. Thousands can sit in the client's buffer. Crash → all lost, plus the one
  being processed.
- **Manual ack, prefetch 100** — up to 100 unacked messages wait in the client. Crash → the connection closes, and the
  broker puts them back in the queue for another consumer. Duplicates, not loss.

Watch the names: Spring AMQP's `AcknowledgeMode.AUTO` (the default) is a manual ack that Spring sends after the
listener returns — safe. RabbitMQ's auto-ack is Spring's `NONE`.

Kafka doesn't push: `poll()` brings up to 500 records into memory, but nothing is confirmed until the offset is
committed — [unless auto-commit does it](#kafka-auto-commit--when-does-it-lose-messages).

</details>