У dual write нет одного «автора», но есть три человека, на которых все ссылаются. Их и стоит читать.

**Кто ввёл тему**

- **Martin Kleppmann — сама проблема dual write.** Он популяризировал термин в докладе на Craft Conference 2015 и статье по нему: Using logs to build a solid data infrastructure (or: why dual writes are a bad idea). Его определение: Dual writes is simple: it's your application code's responsibility to update data in all the right places. Подход популярен, потому что его легко построить и поначалу он более-менее работает, но it has some fundamental problems. The first problem is race conditions.
- **Chris Richardson — паттерн outbox и его реализации.** Он описал outbox на microservices.io и в книге «Microservices Patterns» (Manning, 2018). Подробно паттерн разобран in chapter 3 of Microservices Patterns, Manning, 2018. У него же два способа доставки из outbox: The Polling publisher periodically polls the database, trading simplicity for increased load and traffic и The Transactional log tailing is a more efficient method which reads the database's commit log and propagates that instead.
- **Gunnar Morling — практика: outbox плюс CDC.** Бывший руководитель проекта Debezium, Java Champion. Подробно пишет о том, как реализовать outbox безопасно и эффективно.

**Что читать — по приоритету, с учётом того, что собеседование завтра**

*Сегодня, около часа:*
1. **microservices.io: страницы Transactional outbox, Polling publisher, Transaction log tailing.** Каждая короткая, это прямо ваш вокабуляр для собеседования.
2. **Статья Kleppmann 2015 года** (martin.kleppmann.com) — откуда проблема и почему она коварная. Хорошие диаграммы с race condition.
3. **Morling, «Revisiting the Outbox Pattern»** (morling.dev, 2024) — практические детали и подводные камни.

*Потом, для глубины:*
- **Kleppmann, «Designing Data-Intensive Applications».** Это главная книга по теме. В первом издании (2017) chapter 11 analyses dual writes and log-based integration. Уже вышло второе издание: Martin Kleppmann and Chris Riccomini, O'Reilly Media, March 2026. Берите его.
- **Richardson, «Microservices Patterns»:** глава 3 (messaging, outbox) и глава 4 (sagas). Готовится и второе издание, оно сейчас в раннем доступе (MEAP).

**Вопросы, которые стоит уметь объяснить после чтения**

- Что такое dual write и почему не решить его через 2PC.
- Polling publisher или CDC (Debezium): плюсы и минусы.
- At-least-once → idempotent consumer, или inbox на стороне получателя.
- Порядок сообщений: per aggregate, ключ partition в Kafka.
- Несколько экземпляров worker'а: `SELECT … FOR UPDATE SKIP LOCKED`.
- Очистка outbox-таблицы.
- Payload или ссылка — то, что мы только что разобрали.

Большую часть этого вы уже делали в Deka, просто без названий. Чтение даст вам термины, а опыт у вас уже есть.

Sources:
- [Using logs to build a solid data infrastructure — Martin Kleppmann](https://martin.kleppmann.com/2015/05/27/logs-for-data-infrastructure.html)
- [Confluent blog: why dual writes are a bad idea](https://www.confluent.io/ja-jp/blog/using-logs-to-build-a-solid-data-infrastructure-or-why-dual-writes-are-a-bad-idea/)
- [Transactional outbox — arc42 quality (references)](https://quality.arc42.org/approaches/transactional-outbox)
- [Distributed Application Architecture Patterns — Transactional Outbox](https://jurf.github.io/daap/consistency-patterns/transactional-outbox)
- [Gunnar Morling — Change Data Streaming Patterns (speaker deck)](https://speakerdeck.com/gunnarmorling/change-data-streaming-patterns-with-debezium-and-apache-flink?slide=13)
- [Designing Data-Intensive Applications, 2nd Edition — Kleppmann](https://martin.kleppmann.com/2026/03/24/designing-data-intensive-applications-2e.html)