
### Anemic Domain Models, idea

The term _anemic domain model_ was coined by Martin Fowler (2003) to describe designs
in which objects of the domain model are essentially data carriers, containing little or no business logic.

Characteristics
- Domain objects are just data containers:
    - They have attributes (e.g., name, price, status).
    - They expose getters and setters.
- Business logic lives outside the domain model, usually in:
    - Application services
    - Utility classes
- No encapsulation of invariants:
    - Rules like _"price must be positive"_ are enforced elsewhere, not inside the `Product` class.

### Why Anemic Domain Models considered an anti-pattern in Domain-Driven Design (DDD)?

- Breaks Object-Oriented principles:
    - Objects should combine state and behavior.
- Harder to maintain:
    - Logic scattered across services instead of being close to the data it manipulates.
- Poor expressiveness:
    - The model doesn’t reflect the domain well; it looks more like a database schema.

### Alternative to Anemic Domain Models

Rich Domain Model (preferred) - keeps business logic inside the domain objects,
making them self-contained and expressive.

### Anemic vs Rich Domain Model in DDD

| **Aspect**                  | **Anemic Domain Model**                                                                                                 | **Rich Domain Model**                                                                     |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| **Definition**              | Domain objects contain only data (fields) and simple getters/setters. Business logic lives in separate service classes. | Domain objects encapsulate both data and business logic, enforcing invariants internally. |
| **Encapsulation**           | Poor – objects expose state via getters/setters without enforcing rules.                                                | Strong – objects protect invariants and enforce rules within themselves.                  |
| **Business Logic Location** | In application services or utility classes.                                                                             | Inside domain entities and value objects.                                                 |
| **Expressiveness**          | Low – resembles a database schema rather than a domain model.                                                           | High – reflects real-world domain concepts and behaviors.                                 |
| **Maintainability**         | Harder – logic scattered across services, harder to track changes.                                                      | Easier – logic is close to the data it manipulates, reducing coupling.                    |
| **Testability**             | Requires testing services and domain objects separately.                                                                | Easier – domain objects can be tested in isolation with their logic.                      |
| **DDD Alignment**           | Violates DDD principles; considered an anti-pattern.                                                                    | Fully aligns with DDD principles.                                                         |
| **Example**                 | `Order` class with only getters/setters; `OrderService` calculates totals.                                              | `Order` class validates quantity and calculates totals internally.                        |
| **Pros**                    | Simple to implement; familiar for CRUD-based apps.                                                                      | Better domain integrity; more expressive and maintainable.                                |
| **Cons**                    | Leads to procedural design; weak encapsulation; harder to evolve.                                                       | Requires deeper domain understanding; more complex initial design.                        |

### Rich Domain Model in a functional programming (FP) context

In OOP, a rich domain model means state + behavior inside the same object.

In FP, you cannot bundle state and behavior in the same object because FP favors immutability and pure functions.
We avoid mutable state and side effects, while still keeping domain logic close to the domain types.

You achieve "richness" in FP by:
- Defining domain-specific types (e.g., Order, Money, Cus   tomerId).
- Grouping domain logic close to those types (e.g., in the same module or namespace).
- Using smart constructors to enforce invariants.
- Keeping functions pure and referentially transparent.

So, a "Rich Domain Model" in FP means types + pure functions + invariants enforced at creation,
not methods inside objects.

### Anemic vs Rich Domain Model in FP

With Anemic Domain Model if you just create plain data structures and scatter logic across unrelated utility classes,
you lose domain expressiveness and type safety.

Instead of anemic models, FP encourages:
- Algebraic Data Types (ADTs) for domain concepts.
- Modules or namespaces to group related functions.
- Smart constructors for validation.

### Example of Rich Domain Model in FP

Grouping `Order` Logic in a Domain Module:
```
com.example.domain.order
    ├── Order.java          // Domain type
    ├── OrderService.java   // Pure functions grouped by domain
```
```java
package com.example.domain.order;

public record Order(int quantity, double price) {

  public Order {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be positive");
    }
    if (price <= 0) {
      throw new IllegalArgumentException("Price must be positive");
    }
  }
}
public class OrderService {

  // Pure function: calculate total
  public double calculateTotal(Order order) {
    return order.quantity() * order.price();
  }

  // Pure function: apply discount
  public Order applyDiscount(Order order, double discountPercent) {
    if (discountPercent < 0 || discountPercent > 100) {
      throw new IllegalArgumentException("Discount must be between 0 and 100");
    }
    double discountedPrice = order.price() * (1 - discountPercent / 100);
    return new Order(order.quantity(), discountedPrice);
  }
}
```

### Rich Domain Model in FP vs OOP

- **OOP Rich Domain Model**: State and behavior inside the same class.
- **FP-Style Rich Domain Model**: Immutable data types + pure functions grouped by domain.

Benefits of FP-Style Rich Domain Model (Split Design):

| Aspect            | OOP Rich Domain Model                     | FP-Style Rich Domain Model                              |
|-------------------|-------------------------------------------|---------------------------------------------------------|
| **State**         | Encapsulated in objects                   | Immutable record                                        |
| **Encapsulation** | Strong (private fields, public methods)   | Type safety and module boundaries                       |
| **Mutation**      | Allowed inside object                     | No mutation; returns new values                         |
| **Behavior**      | Methods inside the object                 | Pure functions grouped in a service class               |
| **Testability**   | Test object methods                       | Test pure functions easily                              |
| **Composability** | Harder to compose                         | Functions can be chained or pipelined                   |
| **Dependencies**  | Harder to inject without polluting domain | Service can inject dependencies without changing domain |

When FP-style is better than OOP-style:
- Complex domain logic that needs external dependencies (e.g., tax rules, currency conversion).
- Testability: You can mock OrderService without touching Order.
- Functional pipelines: Easier to compose transformations.
- Concurrency: Pure functions are thread-safe by design.
FP-Style Rich Domain Model Cons:
- Logic split across classes.
- Requires discipline to keep domain grouping.

When OOP-style might be better:
- Small domain models with simple logic.
- DDD aggregates where invariants and behavior are tightly coupled.
- Readability: Everything in one place for small classes.
OOP Rich Domain Model Cons:
- Harder to test in isolation.
- Coupled state and behavior.

### Pure functions vs methods

- Methods inside the object
- Pure functions grouped in a service class by domain

With pure functions it is easier to:
- test
- reason about - no hidden side effects - `OrderService.applyDiscount()` returns a new `Order`
- compose - functions can be chained or pipelined

### Describe Composability (Pipeline) in FP vs OOP

With pure functions, you can easily chain transformations:
```java
Order order = new Order(3, 100.0);
OrderService service = new OrderService();

double finalTotal = Stream.of(order)
    .map(o -> service.applyDiscount(o, 10))
    .map(service::calculateTotal)
    .map(total -> total * 1.19) // apply tax
    .findFirst()
    .orElseThrow();

System.out.println("Final Total: " + finalTotal);
```
Why is this powerful?
- Each step is pure and returns a new value.
- Easy to add/remove steps without breaking encapsulation.
- Perfect for functional pipelines.

In OOP, methods are tied to the object, so chaining is harder unless you design for it:
```java
Order order = new Order(3, 100.0);
Order discountedOrder = order.applyDiscount(10);
double totalWithTax = discountedOrder.calculateTotal() * 1.19;

System.out.println("Final Total: " + totalWithTax);
```
To make it more composable, you’d need fluent APIs:
```java
double finalTotal = new Order(3, 100.0)
    .applyDiscount(10)
    .calculateTotal() * 1.19;
```
But:
- **You cannot easily inject external logic** (e.g., tax calculation service) without modifying the domain class.
- **Adding new steps** often means changing the domain class, which **breaks encapsulation**.

Key Benefit of FP Approach
- Functions are first-class citizens → easy to compose, reuse, and test.
- No need to modify Order for new behaviors.
- Works well with streams, lambdas, and higher-order functions.

### Adding a Tax Calculation Service for FP-style vs OOP-style Rich Domain Models

Key Difference

- FP approach: Adding tax is just another function in the pipeline. No domain class changes.
- OOP approach: Either modify Order (breaking SRP) or add boilerplate to integrate external services.

#### FP-Style Rich Domain Model with Tax Service:
```java
// Domain type
public record Order(int quantity, double price) {
  public Order {
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    if (price <= 0) throw new IllegalArgumentException("Price must be positive");
  }
}
// Tax calculation service
public class TaxService {
  private final double taxRate;

  public TaxService(double taxRate) {
    this.taxRate = taxRate;
  }

  public double applyTax(double amount) {
    return amount * (1 + taxRate);
  }
}
// Domain logic grouped by domain
public class OrderService {
  public double calculateTotal(Order order) {
    return order.quantity() * order.price();
  }

  public Order applyDiscount(Order order, double discountPercent) {
    if (discountPercent < 0 || discountPercent > 100) {
      throw new IllegalArgumentException("Discount must be between 0 and 100");
    }
    double discountedPrice = order.price() * (1 - discountPercent / 100);
    return new Order(order.quantity(), discountedPrice);
  }
}
```
Usage (FP pipeline style):
```java
Order order = new Order(3, 100.0);
OrderService orderService = new OrderService();
TaxService taxService = new TaxService(0.19); // 19% tax

double finalTotal = taxService.applyTax(
    orderService.calculateTotal(
        orderService.applyDiscount(order, 10)));

System.out.println("Final Total with Tax: " + finalTotal);
```
Usage (FP Stream pipeline style):
```java
Order order = new Order(3, 100.0);
OrderService orderService = new OrderService();
TaxService taxService = new TaxService(0.19); // 19% tax

double finalTotal = Stream.of(order)
    .map(o -> orderService.applyDiscount(o, 10))   // Apply discount
    .map(orderService::calculateTotal)            // Calculate total
    .map(taxService::applyTax)                    // Apply tax
    .findFirst()
    .orElseThrow();

System.out.println("Final Total with Tax: " + finalTotal);
```
Benefits:
- Tax logic is completely separate and composable.
- No modification to Order or OrderService needed to add tax.
- Easy to inject different tax strategies (e.g., VAT, regional tax).
- Perfect for functional pipelines.

#### OOP Rich Domain Model with Tax Service

To add tax calculation, you have two options:
Option 1: Add method to Order
```java
public class Order {
  private final int quantity;
  private final double price;

  public Order(int quantity, double price) {
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    if (price <= 0) throw new IllegalArgumentException("Price must be positive");
    this.quantity = quantity;
    this.price = price;
  }

  public double calculateTotal() {
    return quantity * price;
  }

  public Order applyDiscount(double discountPercent) {
    if (discountPercent < 0 || discountPercent > 100) {
      throw new IllegalArgumentException("Discount must be between 0 and 100");
    }
    double discountedPrice = price * (1 - discountPercent / 100);
    return new Order(quantity, discountedPrice);
  }

  // Adding tax logic here couples tax to Order
  public double calculateTotalWithTax(double taxRate) {
    return calculateTotal() * (1 + taxRate);
  }
}
```
Usage:
```java
Order order = new Order(3, 100.0);
Order discountedOrder = order.applyDiscount(10);
double finalTotal = discountedOrder.calculateTotalWithTax(0.19);
System.out.println("Final Total with Tax: " + finalTotal);
```
Option 2: External TaxService
You can keep tax separate, but then you lose composability because
Order methods don’t integrate easily with external services without extra boilerplate.

Key Difference:
- FP approach: Adding tax is just another function in the pipeline. No domain class changes.
- OOP approach: Either modify Order (breaking SRP) or add boilerplate to integrate external services.