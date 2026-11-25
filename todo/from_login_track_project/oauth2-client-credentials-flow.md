# OAuth2 Client Credentials: How a Service Gets and Uses Tokens

Generic workflow for a backend service that calls a protected API **on its own behalf**
(no user), typically from a background thread: message consumer, scheduler, batch job.
Spring Security implementation; the pattern itself is framework-neutral.

Scope: the **client** side only. How the API validates the token (resource server) is a separate topic.

---

## 1. The pattern in one sentence

Before each outgoing request: take a valid token from a cache; if there is none (or it is about to expire),
get a new one from the authorization server, cache it, then put it into the `Authorization` header.

Hand-written, this is an interceptor + a cache + a call to the auth service.
Spring Security ships each piece as a standard component.

| Hand-written                                   | Spring Security                            |
|------------------------------------------------|--------------------------------------------|
| Own HTTP interceptor                           | `OAuth2ClientHttpRequestInterceptor`       |
| "From cache, else fetch and store" logic       | `OAuth2AuthorizedClientManager`            |
| Cache (e.g. Guava / Caffeine)                  | `OAuth2AuthorizedClientService`            |
| Call to the auth service                       | `OAuth2AuthorizedClientProvider` (client credentials) |
| Auth service URL, client id, secret in config  | `ClientRegistration` (from YAML)           |

---

## 2. Components

| Component | Task | Where it comes from |
|---|---|---|
| `ClientRegistration` | Who the client is (`client-id`, `client-secret`), which grant, token endpoint, scopes | `spring.security.oauth2.client.registration.<id>` + `provider.<name>`; Boot builds it |
| `ClientRegistrationRepository` | Holds all registrations, looked up by registration id | Boot (in-memory) |
| `OAuth2AuthorizedClientService` | Stores obtained tokens ("authorized clients") | Boot (in-memory) |
| `OAuth2AuthorizedClientProvider` | Knows **how** to obtain a token for one grant type (here: client credentials) | Configured in the manager |
| `OAuth2AuthorizedClientManager` | Orchestrates: look up stored token → valid? → else ask the provider → store | **You** create the right one (section 5) |
| `OAuth2ClientHttpRequestInterceptor` | Asks the manager for a token and sets the `Authorization` header | **You** attach it to the HTTP client |

---

## 3. The chain

```
Background thread (no incoming HTTP request)
│
└─ business code calls the API client
   └─ HTTP interface proxy / RestClient                    [RestClient]
      └─ request interceptor                               [OAuth2ClientHttpRequestInterceptor]
         │  "token for registration <id>?"
         └─ authorized client manager                      [AuthorizedClientServiceOAuth2AuthorizedClientManager]
            ├─ client configuration                        [ClientRegistrationRepository → ClientRegistration]
            ├─ stored token?                               [OAuth2AuthorizedClientService]
            │   ├─ present and not expiring → use it
            │   └─ missing or expiring →
            │        POST <token-uri>                      [OAuth2AuthorizedClientProvider: client credentials]
            │          grant_type=client_credentials
            │          client_id, client_secret          → authorization server
            │        ← access_token, expires_in
            │        store it                              [OAuth2AuthorizedClientService]
            └─ return token
         └─ Authorization: Bearer <token>
      └─ HTTP request                                    → protected API
```

### Three situations

| Situation | What happens | Calls to the authorization server |
|---|---|---|
| First request after start | No token stored → fetch → store → send | 1 |
| Following requests | Token found and valid → send | 0 |
| Token close to expiry | Treated as missing → fetch a new one → replace | 1 |

---

## 4. Storage and sharing

- **One store per application instance**, shared by all threads. Not per thread, not per consumer partition.
- **Key:** registration id + principal name. In client credentials there is no user, so effectively
  **one token per registration** per instance.
- **Several instances** of the service: each has its own in-memory store → each fetches its own token.
  N instances = N tokens in parallel; normal.
- **Restart:** the store is empty → the first call fetches a new token.
- **Race at startup:** several threads may find no token at the same moment and each fetch one.
  A few extra token requests once; the last stored wins. Harmless.

### Expiry and clock skew

- The token carries its lifetime (`expires_in`, `exp`), typically minutes.
- The provider treats a token as expired **60 seconds before** its real expiry (clock skew, configurable),
  so a token never runs out in the middle of a request because of slightly different clocks.
- Example: lifetime 5 min → a new token roughly every 4 min, regardless of traffic.

---

## 5. Which manager: the main pitfall

| Manager | Stores tokens in | Needs an incoming HTTP request | Use for |
|---|---|---|---|
| `DefaultOAuth2AuthorizedClientManager` | The user's HTTP session (via `OAuth2AuthorizedClientRepository`) | **Yes** | Web apps acting for a logged-in user |
| `AuthorizedClientServiceOAuth2AuthorizedClientManager` | `OAuth2AuthorizedClientService` (application memory) | No | Background threads: consumers, schedulers, jobs |

- **Pitfall:** the default manager in a call from a background thread → no current request →
  failure (`servletRequest cannot be null`) or no token.
- **Do:** define the service-based manager yourself:

```java
@Bean
OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                      OAuth2AuthorizedClientService authorizedClients) {
    var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build());
    return manager;
}
```

---

## 6. Configuration

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          my-api:                                   # registration id, used to find this client in code
            provider: my-auth-server
            client-id: ${OAUTH2_CLIENT_ID}
            client-secret: ${OAUTH2_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: orders:write
        provider:
          my-auth-server:
            token-uri: ${OAUTH2_TOKEN_URI}
```

| Property | Meaning |
|---|---|
| `registration.<id>` | Name of this client configuration; code refers to it by this id |
| `provider` | Which provider block holds the authorization server URLs |
| `client-id` / `client-secret` | Credentials registered at the authorization server; secret only from the environment |
| `authorization-grant-type` | `client_credentials`: the service authenticates as itself |
| `scope` | Permissions to request; must be allowed for this client at the authorization server |
| `provider.<name>.token-uri` | Token endpoint URL |

- **Know:** `provider.<name>.issuer-uri` instead of `token-uri` → the endpoints are discovered at **startup**
  → the service does not start while the authorization server is down. `token-uri` has no startup dependency.

---

## 7. Failure cases

| Case | Result |
|---|---|
| Authorization server down / slow | Exception **before** the API request (OAuth2 exception, not `RestClientException`) |
| Wrong secret / unknown client | Token request rejected (`unauthorized_client`) → exception, every call fails |
| Token valid, API rejects with 401 | Wrong `aud` / `iss` or clock problems — check the token claims |
| Token valid, API answers 403 | Scope missing for this client |
| Client disabled at the authorization server | Current token keeps working until it expires; next fetch fails |

- **Decide:** whether retry / circuit breaker cover token failures; they react only to the exception types they are configured for.
  Base type to configure: `org.springframework.security.oauth2.core.OAuth2AuthorizationException`.

### The token request has its own HTTP client

The `POST <token-uri>` in the chain (section 3) is **not** sent through the HTTP client of the protected API.
Spring Security uses its own internal `RestClient` for it:

```
API call:       interceptor → [your RestClient / HTTP service group: your retries off, your timeouts] → API
Token request:  provider    → [Spring Security's internal RestClient: library defaults]             → authorization server
```

Library defaults with Apache HttpClient 5: one automatic retry on 503/429, no read timeout. Consequences:
- token requests are multiplied (N Resilience4j attempts → 2N token requests when the server answers 503);
- a hanging authorization server blocks the calling thread (e.g. a Kafka listener) without limit.

Fix: configure the token client explicitly with the same "no library retries" request factory and its own timeouts:

```java
@Bean
OAuth2AuthorizedClientManager authorizedClientManager(
    ClientRegistrationRepository registrations,
    OAuth2AuthorizedClientService authorizedClients,
    ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,          // the same builder as for the API client
    @Value("${app.oauth2.token-client.connect-timeout}") Duration connectTimeout,
    @Value("${app.oauth2.token-client.read-timeout}") Duration readTimeout) {

    var restClient = RestClient.builder()
        .requestFactory(requestFactoryBuilder.build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
        // A custom RestClient must carry what Spring Security needs to send the form and parse the token response.
        .configureMessageConverters(converters -> converters
            .disableDefaults()
            .addCustomConverter(new FormHttpMessageConverter())
            .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
        .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
        .build();

    var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
    tokenResponseClient.setRestClient(restClient);

    var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials(cc -> cc.accessTokenResponseClient(tokenResponseClient))
        .build());
    return manager;
}
```

- **Know:** the worst case per attempt is now token request + API call — recheck outer time limits.
- **Found by:** a test that counts token requests with the token endpoint returning 503 (expected 4, got 8).

---

## 8. Attaching the interceptor: option A vs option B

Both options use the **same** interceptor and the **same** manager; the chain from section 3 does not change.
They differ in one point: **who tells the interceptor which registration to use**, and how the interceptor
gets into the HTTP client.

The interceptor cannot guess: a service may have several registrations (one per protected API).

Example setup for both options: a shared contract module `orders-api` with an HTTP interface that the
**server implements** and the **client calls**; the client's registration id is `orders-api`.

### Option A: annotation on the HTTP interface

Chain difference:
```
OrdersApi.create(order)                          [HTTP interface proxy]
│  reads @ClientRegistrationId("orders-api")     [ClientRegistrationIdProcessor]
│  puts the id into the request attributes
└─ interceptor reads the id from the attributes  [OAuth2ClientHttpRequestInterceptor]
   └─ manager → token → Authorization: Bearer
```

Contract module (shared by client and server):
```java
@HttpExchange("/api/v1/orders")
@ClientRegistrationId("orders-api")              // client-side security detail inside the contract
public interface OrdersApi {

    @PostExchange
    void create(@RequestBody Order order);
}
```
The contract module now needs `spring-security-oauth2-client` (at least `provided`) for the annotation.

Client service:
```java
@Bean
OAuth2RestClientHttpServiceGroupConfigurer securityConfigurer(OAuth2AuthorizedClientManager manager) {
    // Adds the annotation processor to the proxy factory and the interceptor to every RestClient group.
    return OAuth2RestClientHttpServiceGroupConfigurer.from(manager);
}
```

Server service:
```java
@RestController
public class OrdersController implements OrdersApi {   // inherits @ClientRegistrationId: meaningless here
    ...
}
```

### Option B: interceptor configured on the client's HTTP service group

Chain difference:
```
OrdersApi.create(order)                          [HTTP interface proxy, contract unchanged]
└─ interceptor attached only to group "orders"   [OAuth2ClientHttpRequestInterceptor]
   with a fixed registration id from the client's configuration
   └─ manager → token → Authorization: Bearer
```

Contract module: unchanged, no security dependency.
```java
@HttpExchange("/api/v1/orders")
public interface OrdersApi {

    @PostExchange
    void create(@RequestBody Order order);
}
```

Client service:
```java
@Bean
RestClientHttpServiceGroupConfigurer ordersOAuth2(OAuth2AuthorizedClientManager manager) {
    var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
    // One protected downstream per group: every request of this group uses this registration.
    interceptor.setClientRegistrationIdResolver(request -> "orders-api");
    return groups -> groups.filterByName("orders")
            .forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
}
```

Server service: implements the contract, knows nothing about how clients authenticate.

_(Code is a sketch against Spring Security 7 / Spring Framework 7 HTTP service groups; check exact signatures when implementing.)_

### Comparison

| | A: annotation on the interface | B: interceptor on the client's group |
|---|---|---|
| Where "which token" is decided | In the contract | In the client's configuration |
| Contract module | Gets a Spring Security dependency and a client's registration id | Unchanged |
| Server implementing the contract | Inherits a client-side annotation it does not use | Unaffected |
| Another client of the same contract | Must name its registration exactly like the annotation | Chooses its own name |
| Changing the auth mechanism (other registration, mTLS, API key) | New contract version, all consumers affected | Change in one client only |
| Code in the client | One bean, one line | One bean, a few lines |
| Granularity | Per method (different registrations, public methods without a token) | Per group (one registration for all calls of the group) |

### Scenario: a third client

A new service calls the same API through the contract and names its registration `ordering`:
- **A:** the annotation demands `orders-api` → the token lookup fails ("no client registration with id orders-api")
  unless the new service renames its registration to match someone else's choice.
- **B:** the new service configures its own group with `ordering`; nothing else changes.

### When to use which

- **A** fits when the interface belongs to the client only (e.g. a client for a third-party API such as GitHub):
  nobody else implements or shares it, and per-method control can be useful.
- **B** fits when the interface is a **shared contract**: authentication is a property of the **call**, not of the API.

### Decision example (login-tracking assessment): option B

- **Reason:** the HTTP interface is the published contract between services. Authentication is not part of the
  API — it is how one particular client calls it.
- **Analogy:** checked exceptions. They force callers to handle them, but they become part of the published
  signature, and every change to them breaks all consumers and makes evolution harder. A security annotation on
  the contract has the same effect: a client-side decision becomes a contract change.
- **Consistent with:** resilience annotations (`@Retry`, `@CircuitBreaker`) are also kept out of the contract and
  live in the client.
- **Cost accepted:** a few more lines of configuration in the client.

---

## 9. How to verify

| What | How |
|---|---|
| Token is attached | Stub server (WireMock) for the API: request has `Authorization: Bearer ...` |
| Token is cached | Stub server as token endpoint: N API calls → 1 token request |
| Works from a background thread | Integration test that triggers the call from the consumer, not from a web request |
| Token endpoint failure | Token endpoint stub returns 503 → expected outcome of the business flow |
| No hidden retries on token requests | Token endpoint stub returns 503 → token requests = configured attempts (not double) |
| Tests independent of order | Token store cleared in `@BeforeEach` (`removeAuthorizedClient(registrationId, "anonymousUser")`) |
| Real token content | Request a token with curl, decode the payload, check `scope`, `aud`, `iss`, `exp` |
