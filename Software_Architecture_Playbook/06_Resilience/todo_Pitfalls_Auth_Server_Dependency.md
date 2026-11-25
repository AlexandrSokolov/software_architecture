# 06_Resilience — pitfalls from practice: the authorization server as a runtime dependency

**Question it answers:** Every protected call needs a token, so Keycloak (or any auth server) is a dependency of
every call — at least now and then. How does it fail the caller and the resource server, and how do I keep it from
blocking threads or causing silent failures?

**Source:** `developer-pitfalls.md` (draft v4) §3.2, §4.3, §5.10, §15.6, §15.11, §15.14–15.15a, §15.23–15.25;
login-tracking assessment (`TrackLoginClientConfig#tokenResponseClient`, `TrackLoginClientTokenTimeoutTest`,
`TrackLoginClientTest#retriesWhenAccessTokenCannotBeObtained`).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.
OAuth2 itself (roles, JWT, `aud`, introspection) → `11_Security/todo_Pitfalls_Security.md`.

---

## 1. Token requests use their own HTTP client

- [ ] **Pitfall:** retries disabled and timeouts set for the API client, yet with Keycloak down the test counts
      **twice** as many token requests as attempts (4 → 8), and a hanging Keycloak blocks the listener thread
      with **no timeout**.
- [ ] **Why:** Spring Security fetches tokens with its own internal `RestClient`; your HTTP service group settings
      don't apply. With Apache HttpClient on the classpath: one automatic retry on 503/429, no read timeout.
- [ ] **Do:** own token client built from the **same** request factory builder (one place for "no library
      retries") with explicit timeouts; a custom `RestClient` must carry the converters and error handler Spring
      Security needs. Did: `app.oauth2.token-client` 1 s / 2 s.
- [ ] **Verify:** token endpoint 503 → exactly N token requests for N attempts, 0 API requests; a slow token
      endpoint → `SocketTimeoutException` as root cause (lowered timeout in its own test class).
- [ ] **The attempt grows** — token request + API call; recheck `max.poll.interval.ms` and the shutdown chain
      when adding authentication. Did: chain raised 23.5 → 37.25 s.
- [ ] **Invisible in metrics** — a hand-built `RestClient` has no `ObservationRegistry` →
      `09_Operability/3_Instrumentation/todo_Pitfalls_Instrumentation.md`.

## 2. The resource server's key download

- [ ] **`issuer-uri` = discovery + JWKS fetched lazily on the first request**, then cached; a new `kid` triggers a
      refetch.
- [ ] **That download has no timeouts** — rare (first request, key rotation), but a hanging Keycloak blocks
      request threads. Do: own `JwtDecoder` with a timed client — same lesson as §1. Did: Known limitation.

## 3. Startup dependency

- [ ] **Client `issuer-uri` fetches discovery at startup** → the service doesn't start while Keycloak is down;
      `token-uri` alone has no startup dependency. Did: `token-uri`.

## 4. Token cache and outages

- [ ] **The manager reuses the token until `exp` minus clock skew (60 s)** — one token request per ~4 minutes,
      not per event. Verify: N calls → 1 token request.
- [ ] **A token request per call** (hand-written client, a new manager per call) — load on Keycloak, latency,
      rate limits.
- [ ] **The skew decides when an outage starts to hurt** — token 5 min, skew 60 s → from minute 4 the manager
      requests a new one; with Keycloak down that fails although the old token is valid one more minute.
      The outage hits after `lifetime − skew`.
- [ ] **Keycloak down, token still cached** — calls continue until the token expires.

## 5. Token failures in retry and circuit breaker

- [ ] **Token failure happens before the API request** — `OAuth2AuthorizationException`, not
      `RestClientException`; retry and breaker see only the types they're configured for.
      Did: added to `retry-exceptions` and `record-exceptions`.
- [ ] **One breaker for "the call", token included** — a Keycloak outage opens it like a downstream outage; one
      mechanism (and one future pause) covers both. Short hiccups: absorbed by retries.
- [ ] **A wrong client secret is not transient** — still retried, but the breaker opens fast: the
      misconfiguration becomes visible.
- [ ] **Idea:** a separate retry policy for the token request belongs in the token client (inside the
      interceptor), not in `@Retry` on the calling method.

## 6. Stale cached token after a key change

- [ ] **Pitfall:** Keycloak restarted in `start-dev` (keys in memory) or rotated keys → the client still considers
      the token valid, the resource server rejects it → 401 for minutes; 401 not retried, not recorded:
      silent `unsuccessful`.
- [ ] **The client can't know in advance** — the manager checks only time; keys, revocation, audience are known
      to the resource server only. The only reliable signal is the 401.
- [ ] **401 in `retry-exceptions` alone is useless** — every attempt takes the same stale token.
- [ ] **Do:** failure handler on the interceptor drops the cached token on 401 + retry → attempt 2 fetches a new
      token. Cost: a real 401 (wrong `aud`) uses all attempts and counts in the breaker; "only once for 401" is
      hard with one shared `@Retry`.
- [ ] **The classic exception to "never retry 4xx"** — refresh, then repeat once.

## 7. Claims a reviewer may make — and why they don't hold here

- [ ] **"Thundering herd on token refresh"** — bounded by listener threads per instance (3) once per token
      lifetime; single-flight would add complexity for nothing.
- [ ] **"Token expires in flight"** — client refreshes 60 s before `exp`, resource server allows 60 s skew.
- [ ] **"A short Keycloak hiccup opens the breaker"** — retries absorb it; opening needs minimum calls + failure
      rate.

## 8. Alternatives to consider (resilience angle)

- [ ] **Local validation vs introspection** — introspection makes Keycloak a dependency of **every** request
      (down → all fail) → `11_Security` for the trade-off.
- [ ] **mTLS / self-signed JWT** — no auth server at runtime, but key distribution and rotation by hand →
      `11_Security`.

---

## Not here

- Roles, grant types, JWT claims, `aud`, 401 vs 403, testing security → `11_Security/todo_Pitfalls_Security.md`.
- Retry and breaker in general → `todo_Pitfalls_HTTP_Calls.md`.
