- [What is the State Pattern? Key idea](#what-is-the-state-pattern-its-intent-and-key-benefits)

### What is the State Pattern? Its Intent and key benefits

The State Pattern is a behavioral design pattern that allows an object 
to change its behavior when its internal state changes. 
It appears as if the object changed its class.

#### Intent: 
Encapsulate state-specific behavior and delegate state-related tasks to different state objects.

#### Key Benefit: 
Avoids complex conditional logic (if/else or switch) by using polymorphism

### Criteria for deciding between simple if/else logic and the State Pattern

Rule of Thumb, consider the State Pattern using if:
- many states - you see a large conditional block that checks the same variable repeatedly  
- complex behavior per state (multiple methods, significant logic)
- adding a new state means touching multiple places in code - Open/Closed Principle violation

### Real-world domain use cases where the State Pattern shines

1. Vending Machine.

   States: Idle, HasMoney, Dispensing, OutOfStock.

   Why State Pattern?

   Each state has different behavior for actions like insertCoin(), selectProduct(), dispense(). 
   Avoids messy if/else logic.

2. Traffic Light System

   States: Red, Green, Yellow.

   Why State Pattern?

   Each state defines what happens on nextSignal(). 
   Easy to add new states (e.g., blinking mode) without breaking existing code.

3. Document Workflow

   States: Draft, Moderation, Published, Archived.

   Why State Pattern?

   Each state controls allowed actions (edit(), publish(), archive()). Perfect for CMS or approval systems.

4. ATM Machine

   States: Idle, CardInserted, Authenticated, TransactionInProgress.

   Why State Pattern?

   Each state defines what happens on insertCard(), enterPin(), withdrawCash(). Makes banking logic modular and secure.

### The State Pattern Structure

Core Components:
- **Context**: Maintains a reference to the current state and delegates requests to it.
- **State Interface**: Defines the behavior that all concrete states must implement.
- **Concrete States**: Implement behavior specific to a particular state.

### The State Pattern Core Components example

```java
// 1. State Interface
interface DoorState {
  void handle(Door context);
}
// 2. Concrete States
class OpenState implements DoorState {
  @Override
  public void handle(Door context) {
    System.out.println("Closing the door...");
    context.setState(new ClosedState());
  }
}
class ClosedState implements DoorState {
  @Override
  public void handle(Door context) {
    System.out.println("Opening the door...");
    context.setState(new OpenState());
  }
}
// 3. Context
class Door {
  private DoorState state;
  public Door() {
    state = new ClosedState(); // initial state
  }
  public void setState(DoorState state) {
    this.state = state;
  }
  public void pressHandle() {
    state.handle(this);
  }
}
// Usage
public class Main {
  public static void main(String[] args) {
    Door door = new Door();
    door.pressHandle(); // Opening the door...
    door.pressHandle(); // Closing the door...
  }
}
```

### How to pass a context to the state implementation?

Each `DoorState` method receives the `Door` as a method parameter (to call `setState(...)`), 
but it doesn’t store the `Door` - so there’s no persistent back-reference.

That’s a reasonable design for the State pattern: 
- the context knows its current state - `Door` stores `DoorState` as its instance variable
- the state needs temporary access to the context to perform a transition. It avoids tight coupling and lifetime issues.



## What issue might be met with the State Pattern?

A common pain point with the State Pattern:
lots of _"denied"_ methods can make each concrete state feel bloated when only a small subset of actions is valid:  
```java
public interface TaskState {
  void start(Task task);
  void block(Task task, String reason);
  void resolve(Task task);
  void complete(Task task);
  void cancel(Task task);

  String name();
}

class NewState implements TaskState {
  @Override public void start(Task task) {
    task.setState(new InProgressState());
    System.out.println("Task started → InProgress");
  }
  @Override public void block(Task task, String reason) { deny("block"); }
  @Override public void resolve(Task task) { deny("resolve"); }
  @Override public void complete(Task task) { deny("complete"); }
  @Override public void cancel(Task task) {
    task.setState(new CancelledState());
    System.out.println("Task cancelled → Cancelled");
  }
  @Override public String name() { return "New"; }

  private void deny(String action) {
    throw new IllegalStateException("Cannot " + action + " when state=New");
  }
}
```

### Rules to avoid lots of denied methods

Naively implemented State Pattern can become noisy with many “deny” methods. To avoid this:
- Centralize deny logic (defaults or base class), or
- Restrict the interface per state (capabilities), or
- Drive transitions from data (table-driven), or
- Use enums/sealed types for compact, exhaustive handling.

### Solution to avoid lots of denied methods

- [Split the interface by capabilities (Interface Segregation)](#split-the-interface-by-capabilities-interface-segregation)
- [Enum-based State with overridden behavior](#enum-based-state-with-overridden-behavior)
- [Table-driven FSM (Transition Map)](#table-driven-fsm-transition-map)
- [Sealed types & pattern matching](#sealed-types--pattern-matching)

Which approach to pick?

You prefer OO encapsulation: Use Interface defaults or Adapter base class (1/2).
You want minimal API exposure per state: Use capability interfaces (3).
Your states are stable & simple: Use enum with per-constant behavior (4).
Transitions are rule-like and might be stored/configured: Use table-driven FSM (5).
You’re on modern Java and like compiler-checked exhaustiveness: Use sealed types (6).
You need strong auditing or CQRS-like action modeling: Use Command (7).

### Split the interface by capabilities (Interface Segregation)

:
```java
interface Startable { void start(Task task); }
interface Blockable { void block(Task task, String reason); }
interface Resolvable { void resolve(Task task); }
interface Completable { void complete(Task task); }
interface Cancelable { void cancel(Task task); }

class NewState implements Startable, Cancelable { /* only start/cancel */ }
class InProgressState implements Blockable, Completable, Cancelable { /* ... */ }
class BlockedState implements Resolvable, Cancelable { /* ... */ }

class Task {
    private Object state; // could be a marker/union type

    public void start() {
        if (state instanceof Startable s) s.start(this);
        else deny("start");
    }
    // similarly for other actions
}
```

Pros: 
- Compile-time guidance on what’s allowed per state.

Cons: 
- More casting and more interfaces to manage; slightly more complex context.

### Enum-based State with overridden behavior

If your state set is stable, enum with per-constant method bodies is succinct:

```java
enum TaskStateEnum {
  NEW {
    @Override void start(Task t) { t.setState(IN_PROGRESS); }
    @Override void cancel(Task t) { t.setState(CANCELLED); }
  },
  IN_PROGRESS {
    @Override void block(Task t, String r) { t.setBlockedReason(r); t.setState(BLOCKED); }
    @Override void complete(Task t) { t.setState(COMPLETED); }
    @Override void cancel(Task t) { t.setState(CANCELLED); }
  },
  BLOCKED {
    @Override void resolve(Task t) { t.setBlockedReason(null); t.setState(IN_PROGRESS); }
    @Override void cancel(Task t) { t.setState(CANCELLED); }
  },
  COMPLETED, CANCELLED;

  void start(Task t) { deny("start", t); }
  void block(Task t, String r) { deny("block", t); }
  void resolve(Task t) { deny("resolve", t); }
  void complete(Task t) { deny("complete", t); }
  void cancel(Task t) { deny("cancel", t); }
  void deny(String a, Task t) {
    throw new IllegalStateException(a + " not allowed in " + t.getState());
  }
}
```
- Pros: Very compact, easy to read.
- Cons: Less extensible (harder to add states from other modules).

### Table-driven FSM (Transition Map)

FSM - Finite State Machine.

Move denial/allowance out of code into a transition table. Keep State classes only when behavior differs.
```java
enum Action { START, BLOCK, RESOLVE, COMPLETE, CANCEL }
enum State { NEW, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED }

class FSM {
    private static final Map<State, Map<Action, State>> T = Map.of(
        State.NEW, Map.of(Action.START, State.IN_PROGRESS, Action.CANCEL, State.CANCELLED),
        State.IN_PROGRESS, Map.of(Action.BLOCK, State.BLOCKED, Action.COMPLETE, State.COMPLETED, Action.CANCEL, State.CANCELLED),
        State.BLOCKED, Map.of(Action.RESOLVE, State.IN_PROGRESS, Action.CANCEL, State.CANCELLED)
        // COMPLETED/CANCELLED → no transitions
    );

    static State next(State s, Action a) {
        State ns = T.getOrDefault(s, Map.of()).get(a);
        if (ns == null) throw new IllegalStateException(a + " not allowed in " + s);
        return ns;
    }
}

class Task {
    private State state = State.NEW;
    void apply(Action a) { state = FSM.next(state, a); }
}
```
- Pros: No per-state deny boilerplate; rules are data.
- Cons: If behavior per transition has side effects, you’ll still need handlers (e.g., strategy per (state, action)).

### Sealed types & pattern matching

Use sealed interfaces/classes so the compiler knows the state universe, 
and use switch with pattern matching to route actions without per-state deny stubs.

```java
sealed interface TaskState permits New, InProgress, Blocked, Completed, Cancelled {}
record New() implements TaskState {}
record InProgress() implements TaskState {}
record Blocked(String reason) implements TaskState {}
record Completed() implements TaskState {}
record Cancelled() implements TaskState {}

class Task {
  private TaskState state = new New();

  public void start() {
    switch (state) {
      case New n -> state = new InProgress();
      case Completed c, Cancelled c -> deny("start");
      case InProgress ip, Blocked b -> deny("start");
    }
  }

  public void block(String reason) {
    switch (state) {
      case InProgress ip -> state = new Blocked(reason);
      default -> deny("block");
    }
  }
  // etc...
}
```
- Pros: No boilerplate in states; compiler ensures all states are handled.
- Cons: Logic lives in the context, not in the state objects (more “switch” style, less OO).


## How to apply the State pattern when the “state” is external?

You can apply the State pattern even when the “state” is external 
(e.g., a task’s current step in a graph-driven workflow stored in a DB).

The trick is to make the Context a stateless orchestrator that:
- reads the current step from storage,
- maps that step (data) to a state handler (object), and 
- executes behavior. 

The workflow graph (allowed transitions) can be kept in configuration or a database.

### Core Components of the State pattern when the “state” is external

- Task (external data): { id, currentStep, payload, version } — lives in DB (or you get it via REST).
- Handlers - the behavior tied to that state lives in State objects (handlers)-  implement:
  - `String key()`
  - `boolean matches(Task task)` - step recognition based solely on data
  - `void execute(TaskEntity task)` - may mutate data and trigger side-effects
- Step handler registry: ordered list of handlers; finds the single handler that matches the task’s current data. 
- Workflow graph: maps `handler.key()` -> allowed next handler keys.
  The graph remains declarative and is easy to change without touching code (you can put it in JSON/DB)
- Engine (Context): 
  - fetches task 
  - identifies current handler via matches 
    (or picks the handler for current step, if it stored in DB and does not get calculated)
  - runs it
  - validates the next step against the graph
  - persists (or updates with a rest call)

### Task component example

```java
// TaskEntity.java
import java.util.*;

public final class TaskEntity {
  private final UUID id;
  private int version;
  private final Map<String, Object> data = new HashMap<>();
  public TaskEntity(UUID id, int version) {
    this.id = id;
    this.version = version;
  }
}
public interface TaskRepository {
  TaskEntity get(UUID id);
  void save(TaskEntity task);
}
```

### Graph & Registry Contracts

```java
// WorkflowGraph.java
import java.util.Set;

public interface WorkflowGraph {
  Set<String> allowedNext(String fromKey);
}
```
```java
// TaskStepHandler.java
public interface TaskStepHandler {
   /** Unique key for this handler (used in workflow graph). */
   String key();
   /** Returns true if this handler matches the current task data ("current step" identification). */
   boolean matches(TaskEntity task);
   /** Optional hook. */
   default void onEnter(TaskEntity task) {
   }
   /**
    * Execute step logic. May mutate task.data.
    * After this returns, the engine will recompute which handler matches next.
    */
   void execute(TaskEntity task);

   /** Optional hook. */
   default void onExit(TaskEntity task, String nextKey) {
   }
}
```
```java
// StateHandlerRegistry.java

import java.util.List;

public interface StateHandlerRegistry {
  /** Ordered handlers (more specific matchers should come first). */
  List<TaskStepHandler> handlers();

  /**
   * Find the single handler that matches the task.
   * Throws if zero or multiple matches found to avoid ambiguity.
   */
  TaskStepHandler findMatching(TaskEntity task);
}
```

### Context component

Engine (Context)

```java
public final class WorkflowEngine {
  private final TaskRepository repo;
  private final WorkflowGraph graph;
  private final StateHandlerRegistry registry;

  public WorkflowEngine(TaskRepository repo, WorkflowGraph graph, StateHandlerRegistry registry) {
    this.repo = repo;
    this.graph = graph;
    this.registry = registry;
  }

  public void run(UUID taskId) {
    TaskEntity task = repo.get(taskId);

    // Identify current step from data
    TaskStepHandler current = registry.findMatching(task);
    String currentKey = current.key();

    current.onEnter(task);

    // Execute step behavior (may mutate task.data)
    current.execute(task);

    // Identify next step from (possibly) updated data
    TaskStepHandler next = registry.findMatching(task);
    String nextKey = next.key();

    // Validate transition: allow self-transition (no-op) or graph-approved
    boolean selfTransition = currentKey.equals(nextKey);
    Set<String> allowed = graph.allowedNext(currentKey);
    if (!selfTransition && !allowed.contains(nextKey)) {
      throw new IllegalStateException(
        "Transition " + currentKey + " -> " + nextKey + " is not allowed by the workflow graph");
    }

    current.onExit(task, nextKey);

    // Persist only if we actually moved to a different step (based on keys)
    if (!selfTransition) {
      task.setVersion(task.getVersion() + 1);
      repo.save(task);
    }
  }
}
```

### Handlers

Handlers (data-driven matches)
```java

// DraftHandler.java
import java.util.Map;

public final class DraftHandler implements TaskStepHandler {
    @Override public String key() { return "Draft"; }

    @Override
    public boolean matches(TaskEntity task) {
        Map<String, Object> d = task.getData();
        boolean invalid = Boolean.TRUE.equals(d.get("invalid"));
        boolean readyForReview = Boolean.TRUE.equals(d.get("readyForReview"));
        // Draft if NOT invalid and NOT ready for review
        return !invalid && !readyForReview;
    }

    @Override
    public void execute(TaskEntity task) {
        Map<String, Object> d = task.getData();
        String title = (String) d.get("title");
        String content = (String) d.get("content");
        boolean valid = title != null && !title.isBlank()
                     && content != null && !content.isBlank();

        if (!valid) {
            d.put("invalid", true);           // will match Rejected
        } else {
            d.put("readyForReview", true);    // will match Review
            d.remove("approved");             // ensure no stale decision
        }
    }
}
```
```java

// ReviewHandler.java
import java.util.Map;

public final class ReviewHandler implements TaskStepHandler {
    @Override public String key() { return "Review"; }

    @Override
    public boolean matches(TaskEntity task) {
        Map<String, Object> d = task.getData();
        boolean readyForReview = Boolean.TRUE.equals(d.get("readyForReview"));
        Object approvedObj = d.get("approved"); // null means no decision yet
        return readyForReview && approvedObj == null;
    }

    @Override
    public void execute(TaskEntity task) {
        // In a real system, the reviewer sets "approved" externally.
        // This execute can be a no-op or send notifications, etc.
        // The engine will re-evaluate matches; if 'approved' stays null, it's a self-transition.
    }
}

```
```java

// ChangesRequestedHandler.java
import java.util.Map;

public final class ChangesRequestedHandler implements TaskStepHandler {
    @Override public String key() { return "ChangesRequested"; }

    @Override
    public boolean matches(TaskEntity task) {
        Map<String, Object> d = task.getData();
        Boolean approved = (Boolean) d.get("approved");
        boolean changesApplied = Boolean.TRUE.equals(d.get("changesApplied"));
        return Boolean.FALSE.equals(approved) && !changesApplied;
    }

    @Override
    public void execute(TaskEntity task) {
        Map<String, Object> d = task.getData();
        boolean changesApplied = Boolean.TRUE.equals(d.get("changesApplied"));
        if (changesApplied) {
            // Author has applied changes: re-open for review
            d.put("readyForReview", true);
            d.remove("approved");         // clear the previous decision
        }
        // If not applied yet, remain in ChangesRequested (self-transition)
    }
}

```
```java

// ApprovedHandler.java
import java.util.Map;

public final class ApprovedHandler implements TaskStepHandler {
    @Override public String key() { return "Approved"; }

    @Override
    public boolean matches(TaskEntity task) {
        Map<String, Object> d = task.getData();
        return Boolean.TRUE.equals(d.get("approved"));
    }

    @Override
    public void execute(TaskEntity task) {
        // Terminal step. Could emit events, archive, etc.
    }
}

```

### Graph (data-driven)

```java

// InMemoryWorkflowGraph.java
import java.util.*;

public final class InMemoryWorkflowGraph implements WorkflowGraph {
    private final Map<String, Set<String>> edges;

    public InMemoryWorkflowGraph() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("Draft", Set.of("Review", "Rejected"));
        m.put("Review", Set.of("Approved", "ChangesRequested"));
        m.put("ChangesRequested", Set.of("Review", "ChangesRequested"));
        m.put("Approved", Set.of());   // terminal
        m.put("Rejected", Set.of());   // terminal
        this.edges = Collections.unmodifiableMap(m);
    }

    @Override
    public Set<String> allowedNext(String fromKey) {
        return edges.getOrDefault(fromKey, Set.of());
    }
}

```

### Registry (ordered matchers)

```java

// DefaultRegistry.java
import java.util.*;

public final class DefaultRegistry implements StateHandlerRegistry {
    private final List<TaskStepHandler> handlers;

    public DefaultRegistry() {
        // Order matters: more specific handlers first to avoid ambiguity
        handlers = List.of(
            new ApprovedHandler(),          // approved=true
            new RejectedHandler(),          // invalid=true
            new ChangesRequestedHandler(),  // approved=false && !changesApplied
            new ReviewHandler(),            // readyForReview && approved==null
            new DraftHandler()              // fallback: !readyForReview && !invalid
        );
    }

    @Override
    public List<TaskStepHandler> handlers() {
        return handlers;
    }

    @Override
    public TaskStepHandler findMatching(TaskEntity task) {
        List<TaskStepHandler> matches = new ArrayList<>();
        for (TaskStepHandler h : handlers) {
            if (h.matches(task)) matches.add(h);
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("No handler matches task data: " + task.getData());
        }
        if (matches.size() > 1) {
            // If you want “first match wins”, return matches.get(0); here we fail fast to catch ambiguous rules.
            throw new IllegalStateException("Ambiguous matches: " + matches.stream().map(TaskStepHandler::key).toList());
        }
        return matches.get(0);
    }
}
```

### Demo main

```java

// DemoMain.java
import java.util.UUID;

public class DemoMain {
    public static void main(String[] args) {
        InMemoryRepo repo = new InMemoryRepo();
        WorkflowGraph graph = new InMemoryWorkflowGraph();
        StateHandlerRegistry registry = new DefaultRegistry();
        WorkflowEngine engine = new WorkflowEngine(repo, graph, registry);

        // Create a task with initial (external) data
        UUID taskId = repo.seed(new TaskEntity(UUID.randomUUID(), 0));
        TaskEntity task = repo.get(taskId);
        task.getData().put("title", "Hello");
        task.getData().put("content", "World");

        // Draft -> (execute) -> Review or Rejected depending on data validity
        engine.run(taskId);

        // Simulate reviewer decision (set externally)
        task = repo.get(taskId);
        task.getData().put("approved", false);

        // Review -> ChangesRequested
        engine.run(taskId);

        // Author applies changes (set externally)
        task = repo.get(taskId);
        task.getData().put("changesApplied", true);

        // ChangesRequested -> Review (re-opened)
        engine.run(taskId);

        // Reviewer approves
        task = repo.get(taskId);
        task.getData().put("approved", true);

        // Review -> Approved (terminal)
        engine.run(taskId);

        System.out.println("Final step (by matching): " + registry.findMatching(repo.get(taskId)).key()); // Approved
        System.out.println("Version: " + repo.get(taskId).getVersion()); // increments only on real transitions
    }
}

```

### Optional: Graph as JSON (for dynamic workflows)

```json
{
  "Draft":            ["Review", "Rejected"],
  "Review":           ["Approved", "ChangesRequested"],
  "ChangesRequested": ["Review", "ChangesRequested"],
  "Approved":         [],
  "Rejected":         []
}
```

### When to use State vs. a pure FSM library

When to use State vs. a pure FSM library

Use State pattern when per-step behavior is non-trivial (side-effects, orchestration, integrations) and you want to keep logic localized to step classes.
Use a table-driven FSM (or a library) when transitions are simple, primarily guards and actions, and you want a compact, data-only model.
Hybrid is best for workflows in business apps: data-driven graph + handler-per-step.

## todo

###


Extensions You Might Like

Enum-based State with overridden behavior

Domain Events: Emit TaskBlocked, TaskResolved, TaskCompleted for audit/logging.
Persistence: Store state.name() and any state-specific fields (e.g., blockedReason). Rehydrate the state object on load.
Role-based rules: Inject a Policy or use decorators to enforce permissions (e.g., only Assignee can start()).
Time-based transitions: Scheduled transition (e.g., auto cancel() if New for >30 days).
Finite State Machine tooling: If transitions become very complex, consider a DSL or config-driven FSM, keeping State classes for behavior.


### 

```java

interface State {
       void handle();
}

class ConcreteStateA implements State {
    public void handle() {
        System.out.println("Handling in State A");
    }
}

class ConcreteStateB implements State {
    public void handle() {
        System.out.println("Handling in State B");
    }
}

class Context {
    private State state;

    public Context(State state) {
        this.state = state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void request() {
        state.handle();
    }
}

// Usage
Context context = new Context(new ConcreteStateA());
context.request(); // Handling in State A
context.setState(new ConcreteStateB());

```

### Problem Domain: Document Lifecycle
Common actions:

edit, submitForReview, approve, requestChanges, publish, archive, comment.

Valid transitions:

Draft → InReview (submit for review)
InReview → Approved (approve)
InReview → Draft (request changes)
Approved → Published (publish)
Draft/Approved/Published → Archived (archive)
Published → Archived (archive)
Archived: read-only (no edits or transitions back)


### java

```java

```

### Enum-based FSM

### State vs. Strategy (quick contrast)

State: encapsulates behavior + transitions; the context changes behavior over time depending on internal state.
Strategy: encapsulates interchangeable algorithms; the context selects an algorithm — no transitions or lifecycle.

### 

State is defined from external data → each state is just a recognizer (matches(data)), no transitions inside.
State class does not know the next step → transitions live in a graph, not in the states.
Each state has next candidates → adjacency is defined in the graph.
Candidates can be filtered out → edge guards (predicates) filter candidates based on external data.
Not a workflow but a graph; moving “next” can jump over multiple states → the engine does a guarded BFS to find the first matching state and returns the full multi‑step path.


### java

```java

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Graph-based State recognition and navigation.
 *
 * - States are recognizers only (match external data) -> no knowledge of next steps.
 * - Graph edges define next candidates.
 * - Each edge can have a guard (filter) based on the same external data.
 * - Engine finds a path over multiple steps until it reaches a state whose recognizer matches the data.
 *
 * Domain used: Document lifecycle (as data only), but this design is domain-agnostic.
 */
public class GraphStateDemo {

    // ==== DEMO ======================================================================

    public static void main(String[] args) {
        // Build the graph (nodes = recognizers, edges = candidates with guards)
        StateGraph<DocumentData, DocStateId> graph = buildDocumentGraph();

        // Engine that plans a multi-step path using guards; stops on first matching node
        TransitionEngine<DocumentData, DocStateId> engine = new TransitionEngine<>(graph);

        // CASE 1: Multi-hop plan (Draft -> InReview -> Approved -> Published) in one call
        DocumentData data1 = new DocumentData()
                .title("Policy v1")
                .submitted(true)
                .reviewerAssigned(true)
                .approvalCount(2)                  // enough approvals
                .publishAllowed(true)
                .publishedAt(Instant.now())
                .archived(false);

        System.out.println("\n--- CASE 1: multi-hop to PUBLISHED ---");
        PlanResult<DocStateId> plan1 = engine.plan(DocStateId.DRAFT, data1);
        printPlan(plan1);

        // CASE 2: Candidate filtered out (no publishAllowed -> can reach APPROVED but not PUBLISHED)
        DocumentData data2 = new DocumentData()
                .title("Policy v2")
                .submitted(true)
                .reviewerAssigned(true)
                .approvalCount(2)
                .publishAllowed(false)            // blocks APPROVED -> PUBLISHED edge
                .archived(false);

        System.out.println("\n--- CASE 2: filtered edge, stops at APPROVED ---");
        PlanResult<DocStateId> plan2 = engine.plan(DocStateId.DRAFT, data2);
        printPlan(plan2);

        // CASE 3: Jump directly to ARCHIVED (graph supports skipping)
        DocumentData data3 = new DocumentData()
                .title("Policy v3")
                .archived(true);                  // ARCHIVED recognizer true

        System.out.println("\n--- CASE 3: direct jump to ARCHIVED ---");
        PlanResult<DocStateId> plan3 = engine.plan(DocStateId.DRAFT, data3);
        printPlan(plan3);

        // CASE 4: Nothing matches along any guard-satisfied path
        DocumentData data4 = new DocumentData()
                .title("Policy v4")
                .submitted(true)      // submitted but no reviewer assigned -> edge guard fails
                .reviewerAssigned(false)
                .archived(false);

        System.out.println("\n--- CASE 4: no reachable matching state (guards block) ---");
        PlanResult<DocStateId> plan4 = engine.plan(DocStateId.DRAFT, data4);
        printPlan(plan4);
    }

    private static void printPlan(PlanResult<DocStateId> plan) {
        System.out.println("Start: " + plan.start());
        System.out.println("Matching target: " + plan.target().map(Object::toString).orElse("<none>"));
        System.out.println("Path: " + plan.path());
        System.out.println("Reason: " + plan.reason());
        System.out.println("-----");
    }

    // ==== DOMAIN DATA (EXTERNAL) =====================================================

    static class DocumentData {
        String title;
        boolean submitted;
        boolean reviewerAssigned;
        int approvalCount;
        boolean publishAllowed;
        Instant publishedAt;
        boolean archived;
        boolean changesRequested;

        // Fluent setters for demo
        DocumentData title(String t) { this.title = t; return this; }
        DocumentData submitted(boolean v) { this.submitted = v; return this; }
        DocumentData reviewerAssigned(boolean v) { this.reviewerAssigned = v; return this; }
        DocumentData approvalCount(int v) { this.approvalCount = v; return this; }
        DocumentData publishAllowed(boolean v) { this.publishAllowed = v; return this; }
        DocumentData publishedAt(Instant t) { this.publishedAt = t; return this; }
        DocumentData archived(boolean v) { this.archived = v; return this; }
        DocumentData changesRequested(boolean v) { this.changesRequested = v; return this; }
    }

    // ==== STATE IDENTIFIERS =========================================================

    enum DocStateId {
        DRAFT, IN_REVIEW, CHANGES_REQUESTED, APPROVED, PUBLISHED, ARCHIVED
    }

    // ==== RECOGNIZER (STATE DEFINITION) ============================================

    interface Recognizer<D> {
        /** Does this external data correspond to this state's semantics? */
        boolean matches(D data);
        /** Optional: human-friendly name */
        default String name() { return getClass().getSimpleName(); }
    }

    // ---- Document state recognizers (no transitions here; predicates only)

    static class DraftR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return !d.archived && !isPublished(d) && !d.submitted && !d.changesRequested && d.approvalCount == 0;
        }
        private boolean isPublished(DocumentData d) { return d.publishedAt != null; }
    }

    static class InReviewR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return !d.archived && d.submitted && d.reviewerAssigned && d.approvalCount < 2 && !d.changesRequested && d.publishedAt == null;
        }
    }

    static class ChangesRequestedR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return !d.archived && d.changesRequested && d.publishedAt == null;
        }
    }

    static class ApprovedR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return !d.archived && d.approvalCount >= 2 && d.publishedAt == null;
        }
    }

    static class PublishedR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return !d.archived && d.publishedAt != null;
        }
    }

    static class ArchivedR implements Recognizer<DocumentData> {
        public boolean matches(DocumentData d) {
            if (d == null) return false;
            return d.archived;
        }
    }

    // ==== GRAPH (candidates + edge guards) =========================================

    static final class StateNode<D, ID> {
        final ID id;
        final Recognizer<D> recognizer;
        StateNode(ID id, Recognizer<D> recognizer) { this.id = id; this.recognizer = recognizer; }
    }

    static final class Edge<D, ID> {
        final ID from;
        final ID to;
        final Predicate<D> guard; // filters candidate
        final int priority;       // lower = earlier in neighbor ordering

        Edge(ID from, ID to, Predicate<D> guard, int priority) {
            this.from = from; this.to = to; this.guard = guard == null ? d -> true : guard; this.priority = priority;
        }
    }

    static final class StateGraph<D, ID> {
        private final Map<ID, StateNode<D, ID>> nodes = new HashMap<>();
        private final Map<ID, List<Edge<D, ID>>> adj = new HashMap<>();

        public StateGraph<D, ID> addNode(ID id, Recognizer<D> r) {
            nodes.put(id, new StateNode<>(id, r));
            adj.computeIfAbsent(id, k -> new ArrayList<>());
            return this;
        }
        public StateGraph<D, ID> addEdge(ID from, ID to, Predicate<D> guard, int priority) {
            adj.computeIfAbsent(from, k -> new ArrayList<>())
               .add(new Edge<>(from, to, guard, priority));
            return this;
        }
        public Optional<StateNode<D, ID>> node(ID id) { return Optional.ofNullable(nodes.get(id)); }
        public List<Edge<D, ID>> edgesFrom(ID id) {
            return adj.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparingInt(e -> e.priority))
                    .toList();
        }
        public Collection<StateNode<D, ID>> allNodes() { return nodes.values(); }
    }

    // Build a graph for Document states:
    // - Next candidates are edges.
    // - Guards filter them (based on external data).
    static StateGraph<DocumentData, DocStateId> buildDocumentGraph() {
        StateGraph<DocumentData, DocStateId> g = new StateGraph<>();

        // Nodes: recognizers only
        g.addNode(DocStateId.DRAFT,              new DraftR());
        g.addNode(DocStateId.IN_REVIEW,          new InReviewR());
        g.addNode(DocStateId.CHANGES_REQUESTED,  new ChangesRequestedR());
        g.addNode(DocStateId.APPROVED,           new ApprovedR());
        g.addNode(DocStateId.PUBLISHED,          new PublishedR());
        g.addNode(DocStateId.ARCHIVED,           new ArchivedR());

        // Edges (candidates) with guards. Lower priority first.
        g.addEdge(DocStateId.DRAFT,     DocStateId.IN_REVIEW,
                d -> d.submitted && d.reviewerAssigned, 10);
        g.addEdge(DocStateId.DRAFT,     DocStateId.ARCHIVED,
                d -> d.archived, 90);

        g.addEdge(DocStateId.IN_REVIEW, DocStateId.APPROVED,
                d -> d.approvalCount >= 2, 10);
        g.addEdge(DocStateId.IN_REVIEW, DocStateId.CHANGES_REQUESTED,
                d -> d.changesRequested, 20);
        g.addEdge(DocStateId.IN_REVIEW, DocStateId.DRAFT,
                d -> !d.submitted, 30);
        g.addEdge(DocStateId.IN_REVIEW, DocStateId.ARCHIVED,
                d -> d.archived, 90);

        g.addEdge(DocStateId.CHANGES_REQUESTED, DocStateId.DRAFT,
                d -> !d.changesRequested, 10);
        g.addEdge(DocStateId.CHANGES_REQUESTED, DocStateId.ARCHIVED,
                d -> d.archived, 90);

        g.addEdge(DocStateId.APPROVED, DocStateId.PUBLISHED,
                d -> d.publishAllowed && d.publishedAt != null, 10);
        g.addEdge(DocStateId.APPROVED, DocStateId.ARCHIVED,
                d -> d.archived, 90);

        g.addEdge(DocStateId.PUBLISHED, DocStateId.ARCHIVED,
                d -> d.archived, 10);

        // Note: we don't add inbound edges to PUBLISHED except APPROVED->PUBLISHED,
        // but the engine can still "jump" multiple steps if intermediate edges are passable.

        return g;
    }

    // ==== ENGINE (guarded BFS with early stop on recognizer match) ===================

    static final class PlanResult<ID> {
        private final ID start;
        private final List<ID> path;      // excluding start; includes target if found
        private final Optional<ID> target;
        private final String reason;
        PlanResult(ID start, List<ID> path, Optional<ID> target, String reason) {
            this.start = start; this.path = path; this.target = target; this.reason = reason;
        }
        public ID start() { return start; }
        public List<ID> path() { return path; }
        public Optional<ID> target() { return target; }
        public String reason() { return reason; }
    }

    static final class TransitionEngine<D, ID> {
        private final StateGraph<D, ID> graph;
        private final int maxDepth;

        public TransitionEngine(StateGraph<D, ID> graph) {
            this(graph, 64);
        }
        public TransitionEngine(StateGraph<D, ID> graph, int maxDepth) {
            this.graph = graph;
            this.maxDepth = maxDepth;
        }

        /**
         * Plan a path from current to the first reachable node whose recognizer matches(data),
         * honoring edge guards and priorities. Returns the full path (0..N hops).
         * If current already matches, empty path and target=current.
         * If none reachable, empty path and no target.
         */
        public PlanResult<ID> plan(ID current, D data) {
            StateNode<D, ID> start = graph.node(current)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown start node: " + current));
            if (start.recognizer.matches(data)) {
                return new PlanResult<>(current, List.of(), Optional.of(current),
                        "Already in a matching state.");
            }

            // BFS with path reconstruction; neighbors ordered by edge priority
            Map<ID, ID> parent = new HashMap<>();
            Deque<ID> q = new ArrayDeque<>();
            Set<ID> visited = new HashSet<>();

            q.add(current);
            visited.add(current);

            int depth = 0;
            ID found = null;

            while (!q.isEmpty() && depth < maxDepth) {
                int layer = q.size();
                for (int i = 0; i < layer; i++) {
                    ID u = q.remove();
                    for (Edge<D, ID> e : graph.edgesFrom(u)) {
                        if (!e.guard.test(data)) continue;          // candidate filtered out
                        ID v = e.to;
                        if (visited.contains(v)) continue;
                        visited.add(v);
                        parent.put(v, u);

                        // Early stop: first node whose recognizer matches external data
                        StateNode<D, ID> nodeV = graph.node(v)
                                .orElseThrow(() -> new IllegalStateException("Graph integrity error for node " + v));
                        if (nodeV.recognizer.matches(data)) {
                            found = v;
                            // reconstruct and return
                            List<ID> path = reconstruct(parent, current, found);
                            return new PlanResult<>(current, path, Optional.of(found), "Found matching node via guarded BFS.");
                        }
                        q.add(v);
                    }
                }
                depth++;
            }
            return new PlanResult<>(current, List.of(), Optional.empty(),
                    "No reachable node matches data (guards or topology block it).");
        }

        private List<ID> reconstruct(Map<ID, ID> parent, ID start, ID found) {
            LinkedList<ID> path = new LinkedList<>();
            ID cur = found;
            while (!Objects.equals(cur, start)) {
                               path.addFirst(cur);
                cur = parent.get(cur);
                if (cur == null) break; // safety
            }
            return path;
        }
    }

```