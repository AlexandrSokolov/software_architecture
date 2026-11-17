# Observability для Java/Spring — Learning Path

## Литература (в рекомендуемом порядке)
1. **Prometheus: Up & Running, 2nd ed.** — Julien Pivotto, Brian Brazil (O'Reilly, 2023)
2. **SRE with Java Microservices** — Jonathan Schneider (O'Reilly, 2020)
3. **Practical OpenTelemetry** — Daniel Gomez Blanco (Apress, 2023)
4. **Learning OpenTelemetry** — Ted Young, Austin Parker (O'Reilly, 2024)
5. **Observability with Grafana** — Rob Chapman, Peter Holmes (Packt)
6. **Cloud Native Spring in Action** — Thomas Vitale (Manning, 2022) — глава про observability
7. **Observability Engineering, 2nd ed.** — Charity Majors, Liz Fong-Jones, George Miranda (O'Reilly, 2026)

---

**Принцип:** одно маленькое Spring Boot-приложение (REST, пара эндпоинтов — один медленный и иногда падающий), которое по очереди инструментируем разными способами. Всё — в Docker Compose. Подходы идут от классических к современным.

---

## Модуль 0. Минимум теории
- [ ] Три сигнала: **метрики, трейсы, логи** — что даёт каждый
- [ ] Модели доставки: **pull** (Prometheus сам забирает) vs **push** (OTLP, приложение отправляет)
- [ ] Типы метрик: counter, gauge, histogram/timer
- [ ] Метки (labels/tags) и почему опасна высокая кардинальность (например, `userId` в теге)
- [ ] Что такое **OTLP** и **OpenTelemetry** как стандарт: API, SDK, протокол, Collector

**Проверь себя:** почему нельзя класть ID пользователя в тег метрики?

---

## Модуль 1. Prometheus + Grafana сами по себе (без Java)
- [ ] Docker Compose: Prometheus + Grafana
- [ ] `prometheus.yml`: `scrape_configs`, `targets`, интервал опроса
- [ ] UI Prometheus: Targets, формат `/metrics` (открыть в браузере)
- [ ] Базовый PromQL: `rate()`, `sum by (...)`, `histogram_quantile()`
- [ ] Grafana: datasource Prometheus, панель вручную, импорт готового дашборда

**Проверь себя:** чем `rate()` отличается от просто значения counter?

---

## Модуль 2. JMX exporter (метрики без кода, «старая школа»)
- [ ] Обычное Java-приложение (без Spring)
- [ ] Подключить `jmx_prometheus_javaagent` через `-javaagent`
- [ ] Конфиг правил (`rules` в YAML) — какие MBean'ы и как превращать в метрики
- [ ] Посмотреть JVM-метрики (heap, GC, threads) в Prometheus/Grafana
- [ ] Понять, где он нужен сегодня: Kafka, Tomcat, legacy, чужие приложения

**Проверь себя:** что JMX exporter **не** умеет (бизнес-метрики, HTTP-запросы)?

---

## Модуль 3. Prometheus Java client напрямую (без Micrometer)
- [ ] `prometheus-metrics-core` — Counter и Histogram руками
- [ ] Самому поднять HTTP-эндпоинт `/metrics`
- [ ] Ощутить: много ручной работы + жёсткая привязка к Prometheus

**Зачем:** чтобы потом понимать, что именно Micrometer делает за тебя.

---

## Модуль 4. Классика Spring: Micrometer + Prometheus
- [ ] `spring-boot-starter-actuator` + `micrometer-registry-prometheus` → `/actuator/prometheus`
- [ ] Что появилось «бесплатно»: JVM, `http.server.requests`, Hikari, Tomcat, логгеры
- [ ] Свои метрики через `MeterRegistry`: Counter, Timer, Gauge, DistributionSummary
- [ ] Аннотации `@Timed`, `@Counted`
- [ ] Common tags (`application`, `env`), гистограммы и перцентили
- [ ] Как Micrometer переводит имена: `http.server.requests` → `http_server_requests_seconds_*`
- [ ] Grafana: импорт дашборда «JVM (Micrometer)» + свой дашборд по бизнес-метрике

**Проверь себя:** что поменять в коде, чтобы вместо Prometheus был Datadog? (Ответ: ничего — только registry.)

---

## Модуль 5. OpenTelemetry Collector
- [ ] Добавить Collector в Compose
- [ ] Структура конфига: **receivers → processors → exporters**, `pipelines`
- [ ] Приём OTLP (порты 4317 gRPC / 4318 HTTP) → экспорт в Prometheus + `debug` exporter
- [ ] Зачем Collector: развязка приложения и бэкенда, batching, фильтрация, обогащение

**Проверь себя:** почему лучше слать в Collector, а не сразу в бэкенд?

---

## Модуль 6. OpenTelemetry Java agent (zero-code)
- [ ] Убрать Micrometer-registry, запустить с `-javaagent:opentelemetry-javaagent.jar`
- [ ] Настройка через env: `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_METRICS_EXPORTER` и т.д.
- [ ] Данные → Collector → метрики в Prometheus, трейсы в Jaeger/Tempo
- [ ] Сравнить имена метрик: semantic conventions OTel (`http.server.request.duration`) vs Micrometer
- [ ] Первые **трейсы**: вызов между двумя сервисами, распространение контекста (`traceparent`)
- [ ] `@WithSpan` для своих span'ов
- [ ] Ограничения: не работает с GraalVM native image, возможны конфликты с другими агентами, overhead при старте

**Проверь себя:** что агент дал автоматически, чего не было в модуле 4?

---

## Модуль 7. OpenTelemetry Spring Boot starter (от сообщества OTel)
- [ ] `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` вместо агента
- [ ] Настройка через `otel.*` в `application.yml`
- [ ] Сравнить с агентом: что инструментируется, а что нет
- [ ] Когда выбирают: GraalVM native image, уже используется другой агент

**Проверь себя:** чем стартер принципиально отличается от агента по механизму работы?

---

## Модуль 8. Нативный стартер Spring Boot 4 (путь от Spring)
- [ ] `org.springframework.boot:spring-boot-starter-opentelemetry` — что подтягивает (OTel API, `micrometer-registry-otlp`, `micrometer-tracing-bridge-otel`)
- [ ] Настройка через `management.*` (`management.opentelemetry...`, `management.otlp...`)
- [ ] **Observation API**: `@Observed`, `ObservationRegistry` — одно наблюдение → метрика + span
- [ ] Micrometer Tracing: трейсы между двумя сервисами
- [ ] Экспорт логов через OTLP, traceId в логах
- [ ] ⚠️ Не смешивать со стартером сообщества: `spring-boot-starter-opentelemetry` ≠ `opentelemetry-spring-boot-starter`

**Проверь себя:** где здесь Micrometer, а где OpenTelemetry? Кто генерирует данные, кто доставляет?

---

## Модуль 9. Все сигналы вместе в Grafana (LGTM)
- [ ] Образ `grafana/otel-lgtm` (Loki + Grafana + Tempo + Mimir) или те же сервисы по отдельности
- [ ] Переходы: трейс → логи (trace-to-logs), метрика → трейс (**exemplars**)
- [ ] Сценарий отладки: медленный эндпоинт → всплеск на графике → конкретный трейс → лог с ошибкой

**Проверь себя:** дойдёшь ли за пару кликов от всплеска latency до строки лога?

---

## Модуль 10. Итог — своя таблица сравнения
- [ ] Заполнить по своим экспериментам:

| Подход | Изменения в коде | Сигналы (M/T/L) | Pull / Push | Привязка к вендору | Native image | Когда выбирать |
|---|---|---|---|---|---|---|
| JMX exporter | | | | | | |
| Prometheus client | | | | | | |
| Micrometer + Prometheus | | | | | | |
| OTel Java agent | | | | | | |
| OTel starter (сообщество) | | | | | | |
| Spring Boot 4 starter | | | | | | |

- [ ] Сформулировать: «в новом Spring Boot-проекте я выберу ___, потому что ___»

---

## Сознательно откладываем
- Глубокая теория метрик, SLI/SLO, error budgets
- Alertmanager и продвинутый alerting
- Kubernetes (ServiceMonitor, Prometheus Operator)
- Масштабирование хранения (Thanos, long-term storage)
- Профилирование (Pyroscope), eBPF-инструментация

---

## Полезные ссылки
- OpenTelemetry Java agent: https://opentelemetry.io/docs/zero-code/java/agent/
- OpenTelemetry Spring Boot starter: https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/
- Spring blog — OpenTelemetry with Spring Boot (Boot 4): https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/
- Micrometer: https://docs.micrometer.io/micrometer/reference/
- Prometheus: https://prometheus.io/docs/introduction/overview/
- JMX exporter: https://github.com/prometheus/jmx_exporter
- OTel Collector: https://opentelemetry.io/docs/collector/
