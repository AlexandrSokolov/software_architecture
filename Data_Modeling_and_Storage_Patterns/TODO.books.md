# Master Reading List: Foundational Data Architecture Books

This list contains the industry-standard, authoritative texts for data modeling and systems
architecture. These books focus on timeless engineering principles rather than fleeting
frameworks or vendor-specific marketing.

## Tier 1: Microservices & Distributed Data (Event Sourcing, CQRS, Sharding)

### 1. "Designing Data-Intensive Applications" by Martin Kleppmann
* **Why it is a classic:** This is widely considered the modern Bible of backend engineering. Kleppmann brilliantly bridges the gap between database internals and distributed systems.
* **Patterns Covered:** Append-Only Logs, Sharding, Replication, Event Sourcing, and the fundamental trade-offs of NoSQL vs. Relational models.

### 2. "Domain-Driven Design: Tackling Complexity in the Heart of Software" by Eric Evans
* **Why it is a classic:** The "Blue Book." Evans defined how to model complex business domains. While Greg Young later coined the exact term "CQRS," Evans' concepts of Aggregates, Entities, and Bounded Contexts are the required prerequisites for doing CQRS and Event Sourcing correctly.
* **Patterns Covered:** CQRS foundations, Event-Driven Architecture, Aggregate-Oriented Design.

## Tier 2: Relational Complexity (Trees, EAV, Polymorphism)

### 3. "SQL Antipatterns: Avoiding the Pitfalls of Database Programming" by Bill Karwin
* **Why it is a classic:** Karwin takes the most common relational database mistakes (the "antipatterns") and provides the exact architectural design patterns to fix them. It is incredibly practical.
* **Patterns Covered:** Entity-Attribute-Value (EAV), Polymorphic Associations, and a brilliant breakdown of Adjacency Lists vs. Materialized Paths vs. Closure Tables.

### 4. "Joe Celko's Trees and Hierarchies in SQL for Smarties" by Joe Celko
* **Why it is a classic:** Joe Celko served on the ANSI SQL standards committee for a decade. If you need to store hierarchical data in a relational database, this is the undisputed, definitive mathematical authority on the subject.
* **Patterns Covered:** Deep dives into Nested Sets, Adjacency Lists, and Materialized Paths.

## Tier 3: Extreme Scale & NoSQL

### 5. "NoSQL Distilled" by Pramod J. Sadalage and Martin Fowler
* **Why it is a classic:** Martin Fowler is one of the foundational authors of modern software architecture. This book cuts through the hype and clearly defines the computer science behind why document databases differ from graph and column-family stores.
* **Patterns Covered:** Aggregate-Oriented Design, Embedded Documents, and NoSQL distribution models.

### 6. "The DynamoDB Book" by Alex DeBrie
* **Why it's included:** While newer, this is the universally accepted authoritative text on Single Table Design. AWS engineers themselves recommend this book. It is the best resource for understanding how to model relational data without `JOIN`s.
* **Patterns Covered:** Single Table Design, overloading Partition/Sort keys, Adjacency Lists in NoSQL.

## Tier 4: Analytical & Time-Bound Data (Data Warehousing)

### 7. "The Data Warehouse Toolkit" by Ralph Kimball and Margy Ross
* **Why it is a classic:** Ralph Kimball essentially invented modern dimensional modeling. Every data engineer building a data warehouse or BI platform today uses the vocabulary defined in this book.
* **Patterns Covered:** Star Schemas, Snowflake Schemas, and the definitive guide to Slowly Changing Dimensions (SCDs).

### 8. "Developing Time-Oriented Database Applications in SQL" by Richard T. Snodgrass
* **Why it is a classic:** Snodgrass is the academic father of temporal databases. It is a highly academic, rigorous book, but it is the absolute source of truth for handling past, present, and future data timelines.
* **Patterns Covered:** Bitemporal Modeling, Valid Time, and Transaction Time.