
## Search engine next to the database — how do you keep it in sync?
Via Change Data Capture (CDC) or the Transactional Outbox pattern. You stream database changes (e.g., using Debezium
to read the WAL) into a message broker like Kafka, which then updates the search engine (Elasticsearch) asynchronously.
Never try to write to both synchronously from the app.

