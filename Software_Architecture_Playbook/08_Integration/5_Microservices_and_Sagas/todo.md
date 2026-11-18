
## Search engine next to the database — how do you keep it in sync?
Via Change Data Capture (CDC) or the Transactional Outbox pattern. You stream database changes (e.g., using Debezium
to read the WAL) into a message broker like Kafka, which then updates the search engine (Elasticsearch) asynchronously.
Never try to write to both synchronously from the app.

## Moved from encoding/evolution: 

“Service vs shared database” is not about encoding. 
It’s about integration style: shared tables become the contract. It belongs with service boundaries (05/4), not here.

### Service vs shared database — difference?
<details><summary>Show answer</summary>



Both let clients store and query data. The difference is encapsulation — what clients can reach:
- **Shared database** — any query on any table (SQL). The tables become the contract, so changing a column can break
  a client you don't know about.
- **Service** — only the requests its API defines, with answers set by its business logic. The service decides what
  each client may do; its tables stay hidden.

</details>

## Moved from encoding/evolution 2

### What is service discovery?
<details><summary>Show answer</summary>

How a client finds where a service is running: the host and port of an instance it can connect to right now.

</details>

### Service discovery — why not just hardcode the IP address?
<details><summary>Show answer</summary>

A hardcoded IP works until the server moves to a new machine, goes down, or gets overloaded — then every client has
to be reconfigured by hand.

In production it gets worse: a service runs as many instances on many machines, for availability and scale, and they
start and stop all the time. So the client needs the current list of instances and a way to spread requests across
them (load balancing).

</details>

### Service discovery — which option when?
<details><summary>Show answer</summary>

Root: where the choice of instance is made — in a box in the middle, in DNS, in the client, or in a proxy beside it.

- **Load balancer** (hardware, or NGINX/HAProxy) — clients connect to one fixed address; the balancer picks
  a server and skips dead ones. Fits simple setups.
- **DNS** — one name, several IPs; the client picks one. Cheap, but answers are cached and change slowly, so clients
  can [keep using dead IPs](#clients-use-dns-and-hit-dead-servers--why).
- **Discovery registry** (etcd, ZooKeeper) — instances register and send heartbeats; the client asks for the live
  list, then connects directly. Fits instances that change often, and returns metadata (shard, datacenter) so the
  client can choose smarter.
- **[Service mesh](#service-mesh--what-does-it-add)** (Istio, Linkerd) — a proxy next to every client and server
  does discovery and balancing. Fits large, fast-changing setups, usually on Kubernetes.

</details>

### Clients use DNS and hit dead servers — why?
<details><summary>Show answer</summary>

- **Verdict** — DNS caching: clients and resolvers keep old answers for a while, so they still hold IPs of instances
  that are gone.
- **Reasoning** — DNS was built for addresses that rarely change, so changes spread slowly on purpose. Instances that
  restart and move often outrun it.
- **Trade-off** — a discovery registry drops a dead instance once its heartbeats stop, but it's one more system to
  run and keep available. When instances are stable, DNS is enough.

</details>

### Service mesh — what does it add?
<details><summary>Show answer</summary>

A proxy (a sidecar) runs next to every client and every server. The app's outgoing call goes to its own proxy, which
finds a server and sends the call to that server's proxy, which hands it to the server process. This adds:
- **Encryption** — the proxies encrypt traffic between them (mTLS), so app code never deals with certificates.
- **Observability** — a live view of who calls whom, failures and traffic load.
- **Network rules outside the code** — retries and timeouts are set in the mesh, not in each service.

Cost: many extra processes, a complex setup to run and debug, and two extra hops on every call.

</details>


### What is durable execution?
<details><summary>Show answer</summary>

A workflow runs several service calls as steps: charge the card, then deposit to the bank. A crash in between can
leave a charge with no deposit, and calls to other services can't share one database transaction.

Durable execution (Temporal, Restate) logs every call and its result to durable storage. After a failure it re-runs
the workflow but skips calls already logged as done, returning the logged results instead. The aim: each step takes
effect exactly once.

</details>

### Durable execution — why are code changes risky?
<details><summary>Show answer</summary>

Root: a re-run replays the log, so the code must make the same calls in the same order as the first run.

- **Changed code** — after an edit, even just reordering calls, runs already in progress no longer match their log.
  Fix: deploy the change as a new version; old runs finish on the old code, only new runs use the new one.
- **Code that varies per run** — random numbers or the system clock give a different result on replay. Fix: use the
  framework's deterministic versions of these.

</details>

### What is REST?
<details><summary>Show answer</summary>

A style of designing APIs over HTTP: the API is a set of resources, each with its own URL, and clients act on them
with the standard HTTP methods (`GET /orders/42` reads order 42).

</details>

### What is RPC?
<details><summary>Show answer</summary>

Remote procedure call: calling a function on another machine as if it were a local function in your own code
(`orders.getOrder(42)`). Hiding the network this way is called location transparency. Main example today: gRPC.

</details>

### Why is RPC's core idea flawed?
<details><summary>Show answer</summary>

RPC makes a network call look like a local one ([location transparency](#what-is-rpc)), but the two differ in three
ways:
- **Failure** — a local call succeeds or fails on things you control. A network call can be lost, or the other side
  can be slow or down. On a timeout you don't know whether the work was done, and a retry may
  [do it twice](#a-payment-call-times-out--safe-to-retry).
- **Time** — a local call takes about the same time every run. A network call is much slower, and the same call can
  take under a millisecond or several seconds.
- **Data** — a local call passes pointers. A network call must encode every argument to bytes, which hurts with
  large or changing objects, and translate types when the other side uses another language.

So don't make a remote service look like a local object. REST avoids the trap: a request is clearly a network
request, not a function call.

</details>


### REST vs RPC — when which?
<details><summary>Show answer</summary>

**How they model the API**
- **[REST](#what-is-rest)** — resource-oriented: nouns with URLs (`/orders/42`), acted on with HTTP methods; HTTP
  itself handles caching, auth and content type.
- **[RPC](#what-is-rpc)** — action-oriented: verbs (`getOrder(42)`), called as if the method were local.

**How the [contract](3.6.3_Dataflow_Databases_&_Service_APIs.md#how-is-a-service-api-defined) is written**
- **REST** — usually JSON, described with OpenAPI.
- **RPC** — binary Protobuf, described in a `.proto` file (gRPC).

**When which** — follows from the two above:
- **REST**
    - Prefer for: public APIs, browsers, other companies — any language and any tool (curl, a browser) can call it.
    - Cost: [larger and slower](3.6.2_Encoding_Formats.md#json-vs-binary-formats--when-which); the OpenAPI file is
      optional, so schema checks are too.
- **RPC**
    - Prefer for: high-volume calls between your own services that share the `.proto` file.
    - Cost: binary, so no curl and no reading by eye. And
      [a call that looks local still crosses the network](#why-is-rpcs-core-idea-flawed): it can time out, and you
      can't tell whether the work was done.

</details>
