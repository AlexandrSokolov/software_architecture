### the "Programming 101"

geminit mentioned it:

It’s great to connect with a senior engineer. Since you’re coming from a Java background, 
we can skip the "Programming 101" and get into the architectural weeds.

### the classic Request DTO vs Response DTO problem

public class UserDto {

    //common for both “new” and “existing” users

    @Data
    public static class UserBase {
        private String name;
        private int age;
    }

// NewUser is for objects not persisted yet.

    @Data
    public static class NewUser extends UserBase {
        // nothing extra for "new"
    }

    // User represents a persisted domain object, which has an ID.
    @Data
    public static class User extends UserBase {
        private int id;
    }
}

### Builder with optional attribute

    public <A> RestClientBuilder<T> ifPresent(
…
}

    public RestClientBuilder<T> withAuth(final ClientRequestFilter authFilter) {
      clientRequestFilters.add(authFilter);
      return this;
    }

    restClient = RestClient.builder(this.serverUrl, getParameterClass())
      .ifPresent(authFilter, RestClient.RestClientBuilder::withAuth)
      .build();

https://github.com/AlexandrSokolov/rest/blob/master/spring/multiple_rest_clients/rest_client/src/main/java/com/savdev/rest/commons/RestClient.java
https://github.com/AlexandrSokolov/rest/blob/master/spring/multiple_rest_clients/rest_client/src/main/java/com/savdev/rest/commons/BaseRestClientService.java




Got it. Here’s a short, structured map of the most common things people call “design patterns”, grouped by scope and architectural style.

1. Classical Object-Oriented Design Patterns (GoF)

Creational (Factory, Builder, Singleton)
Structural (Adapter, Decorator, Facade)
Behavioral (Observer, Strategy, Command)

Scope: classes & objects
Level: code-level design

2. Enterprise Application Patterns

Repository, Unit of Work
Data Mapper, Active Record
Service Layer, Transaction Script

Scope: business applications
Level: application architecture

3. Architectural Patterns (Macro-level)

Layered Architecture
Hexagonal / Ports & Adapters
Clean Architecture
MVC / MVP / MVVM
Event-Driven Architecture

Scope: whole system
Level: high-level structure

4. Microservices Design Patterns

API Gateway
Service Discovery
Circuit Breaker
Saga
Strangler Fig
Sidecar

Scope: distributed systems
Level: system & infrastructure

5. Distributed Systems & Reliability Patterns

Retry + Backoff
Bulkhead
Idempotency
Leader Election
Consensus (Raft-style patterns)

Scope: fault tolerance
Level: infra & runtime behavior

6. Integration & Messaging Patterns

Publish/Subscribe
Message Queue
Event Sourcing
CQRS
Reactive Streams
Backpressure

Scope: communication
Level: cross-service interaction

7. Concurrency & Parallelism Patterns

Producer–Consumer
Futures / Promises
Thread Pool
Actor Model

Scope: execution model
Level: runtime & performance

8. UI / UX Interaction Patterns

MVC family
Presentation Model
State Machine UI
Command-based UI

Scope: user interfaces
Level: presentation layer

9. Cloud-Native Patterns

Twelve‑Factor App principles
Immutable Infrastructure
Blue–Green / Canary deployment
Auto-scaling patterns

Scope: cloud environments
Level: deployment & ops

Mental shortcut

GoF = how objects talk
Enterprise = how business code is structured
Architectural = how systems are shaped
Microservices / Cloud = how systems survive in production