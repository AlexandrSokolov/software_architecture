
### The top 5 commonly considered most important behavioral design patterns

- Strategy – Defines a family of algorithms and makes them interchangeable
- Observer – Allows objects to be notified of state changes in other objects
- Command – Encapsulates a request as an object, allowing parameterization and queuing
- Iterator – Provides a way to access elements of a collection sequentially without exposing its structure
- [State](state.pattern.md) – Allows an object to alter its behavior when its internal state changes

### the Flyweight pattern, purpose

### the Flyweight pattern, solution in Java

The ability of static factory methods to return the same object from repeated invocations allows classes 
to maintain strict control over what instances exist at any time. 
Classes that do this are said to be _instance-controlled_. 
There are several reasons to write _instance-controlled_ classes. Instance control allows a class to:
- guarantee that it is a singleton or 
- guarantee that it is noninstantiable 
- it allows an immutable value class to make the guarantee that no two equal instances exist: 
  `a.equals(b)` if and only if `a == b`. 

This is the basis of the Flyweight pattern [Gamma95]. Enum types (Item 34) provide this guarantee.

### 