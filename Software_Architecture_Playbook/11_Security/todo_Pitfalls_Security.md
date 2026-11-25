# 11_Security — pitfalls from practice: service-to-service OAuth2, exposure, personal data, CVEs

**Question it answers:** A service calls a protected API and is itself protected. Which roles and checks matter,
which defaults silently change behaviour, what leaks (endpoints, personal data, secrets), and how do I treat
vulnerable dependencies?

**Source:** `developer-pitfalls.md` (draft v4) §15 (without the resilience part), §2.4, §2.16, §6.23, §9.2,
§11, §14.7, §1.4; login-tracking assessment (both `SecurityConfig`s, Keycloak realm file and README,
`TrackLoginControllerTest`, `TrackLoginServiceEndToEndIT`).

**Format:** pitfall → why → what to do. **Did:** = what I did in the project.
Auth server as a runtime dependency (timeouts, hidden retries, stale tokens) →
`06_Resilience/todo_Pitfalls_Auth_Server_Dependency.md`.

---

## 1. Basics

- [ ] **Three roles** — authorization server (issues signed tokens), client (gets a token, sends
      `Authorization: Bearer`), resource server (validates, checks scope and audience). One service can be both.
      Only the authorization server knows client secrets.
- [ ] **Grant types** — `client_credentials` (service, no user), `authorization_code` + PKCE (people),
      `password` (deprecated, removed in OAuth 2.1), token exchange RFC 8693 (service acting for a user).
- [ ] **One client per service, only its flow, only its scopes** — Keycloak enables the standard flow by default.
      Did: service accounts only, `standardFlowEnabled: false`, one scope.
- [ ] **JWT anatomy** — `header.payload.signature`, base64url; **signed, not encrypted** — anyone can read it.
      Claims: `iss`, `sub`, `aud`, `exp`/`iat`, `scope`, `azp`. No personal or secret data in custom claims.

## 2. Resource server

- [ ] **Local validation vs introspection** — local: JWKS cached, no call per request, works while Keycloak is
      down, revocation seen only at `exp`; introspection (RFC 7662): immediate revocation, a call per request,
      Keycloak is a runtime dependency, works with opaque tokens. Default: local + short lifetime; introspection
      when immediate revocation is a hard requirement (payments, admin actions).
- [ ] **A revoked client gets no 401 with local validation** — accepted (200) until `exp`.
- [ ] **What must be checked** — signature, `iss`, `exp`/`nbf` (Spring by default with `issuer-uri`) **plus `aud`**
      and the scope.
- [ ] **No audience check** — a token issued for another API of the same Keycloak is accepted (a compromised
      low-privilege service can call you). Spring checks `aud` only if `audiences` is configured; Keycloak adds a
      custom `aud` only with an audience mapper. Did: both.
- [ ] **`scope` → authority** — `logins:write` → `SCOPE_logins:write` → `hasAuthority(...)`.
- [ ] **401 vs 403** — 401: no / invalid / expired token, wrong `aud` ("who are you?"); 403: valid token, missing
      scope ("I know you, you may not"). A test for each.
- [ ] **The issuer is a URL and must match exactly** — `localhost:8180` vs `keycloak:8080`, http vs https, trailing
      slash → 401. In Keycloak a fixed `KC_HOSTNAME` makes `iss` independent of the request URL.
- [ ] **Stateless API: no session, no CSRF** — CSRF exploits cookies a browser sends automatically; a bearer token
      is sent only by code that has it. Disabling CSRF in an app that **also** uses session cookies is a mistake.
- [ ] **Deny by default** — explicit rules, then `anyRequest().denyAll()`; a new endpoint is closed until opened.
- [ ] **Alternatives for the key source** — `issuer-uri` (discovery), `jwk-set-uri`, `public-key-location`
      (static key, no rotation).

## 3. Client

- [ ] **Adding the OAuth2 client starter locks the whole app** — it brings `spring-security-web`; without your own
      `SecurityFilterChain` Boot protects every incoming request with basic auth → healthcheck and Prometheus get
      401. Did: own chain in the client service too (health + prometheus open, the rest denied).
- [ ] **Client registration in configuration** — secret only from the environment (no default); production: a
      secret store and rotation.
- [ ] **Attaching the token — options:** `@ClientRegistrationId` on the interface (least code; the contract learns
      who calls it) vs an interceptor on the client's HTTP service group (contract stays clean) vs a hand-written
      interceptor (re-implementing caching, expiry, refresh — avoid) vs gateway / proxy adds it.
      Did: group interceptor, because the interface is a shared contract.
- [ ] **Authorized client manager outside an HTTP request** — the default manager stores tokens per HTTP session;
      Kafka listeners, schedulers, startup code have no request → failure. Use
      `AuthorizedClientServiceOAuth2AuthorizedClientManager`. Verify from the listener thread.
- [ ] **Token lifetime trade-off** — short = small revocation window, more requests (cheap with caching); long =
      stolen token usable for hours.

## 4. Testing security without an authorization server

- [ ] **Web slice** — MockMvc + `jwt()`: access rules 401 / 403 / 200.
- [ ] **E2E** — `@MockitoBean JwtDecoder` returning a `Jwt`: real filter chain, real header.
- [ ] **Client** — WireMock as token endpoint + API (same server); assert the `Authorization` header and one token
      request for N calls.
- [ ] **`jwt()` bypasses decoding** — doesn't test `issuer-uri` / `aud`; needs a signed test token or a manual
      check against the running stack.
- [ ] **The token store is a singleton in the cached context** — remove the token in `@BeforeEach` (stored under
      the anonymous principal).
- [ ] **Full stack** — Keycloak in Testcontainers: real tokens, slow, local only.

## 5. Keycloak-specific traps

- [ ] **Realm placeholders: `${NAME}`, not `${env.NAME}`** — the wrong form is stored literally; every token request
      fails with `unauthorized_client`. Without a token the call isn't made — the downstream log stays empty,
      look at the client's log.
- [ ] **`include.in.token.scope`** decides whether a scope appears in the `scope` claim; a scope is in the token only
      if assigned to the client.
- [ ] **`--import-realm` skips an existing realm** — with a persistent DB changes to the file are ignored;
      `start-dev` uses an in-memory DB (re-import every start, console changes lost, keys change on restart).
- [ ] **Local demo only** — `start-dev`, `admin/admin`, no TLS.

## 6. Exposure: what the service shows to the world

- [ ] **Actuator exposure** — only `health, metrics, prometheus`; `env`, `heapdump` leak secrets.
- [ ] **`show-details: always` on an open health endpoint** reveals which systems stand behind the service and
      which are down; production: `when-authorized` or a separate port.
- [ ] **Actuator on the application port** — reachable wherever the API is. Do: `management.server.port` inside
      the cluster only, never published. A service whose only web part is actuator: just don't publish the port.
- [ ] **"Authorized" without people** — readers are machines (probes, Prometheus); network isolation, or a bearer
      token / basic auth in the scrape config (own client with `actuator:read`).

## 7. Personal data (GDPR)

- [ ] **`toString()` of a record contains every field** — logging a whole DTO writes username and IP.
      Did: convention only (logs carry `messageId`) — nothing in the logger filters it.
- [ ] **Idea: `@Sensitive` on record components + one shared `SafeToString`** — the rules live where the DTO is
      defined. Defense in depth: masking in the log encoder too (regex, CPU per line).
- [ ] **Exception messages carry payload** — a deserialization error may quote the raw JSON.
- [ ] **Don't echo rejected values in error responses** — they end up in gateway / client logs.
- [ ] **The DLT is personal data storage** — full payloads: restricted access, explicit retention.
- [ ] **Data minimization in storage** — Did: `username` not stored; IP stored, needs a retention period.
- [ ] **Topic retention defaults apply to personal data too** (7 days) → `08/6 Kafka/todo_Pitfalls_Kafka.md` §4.

## 8. Secrets

- [ ] **Demo secrets only in `.env`**, passed as env vars; never in code, YAML defaults or images.
- [ ] **The realm file holds no secret** — placeholders resolved at import from the container env.
- [ ] **Production** — secret store, rotation; the same value must reach both sides (client and Keycloak).

## 9. Vulnerable dependencies (CVE)

- [ ] **CVE** = public id of a known vulnerability in a specific library version.
- [ ] **Triage, don't panic** — is the vulnerable code path used? (A Kafka **broker** SASL CVE doesn't affect a
      client app); test-only scope → lower priority.
- [ ] **Fix without breaking BOM alignment** — override the Boot property (`jackson-bom.version`,
      `logback.version`) with a comment: CVE id + "remove once Boot manages X"; verify with
      `dependency:tree` → `02_Application_Architecture/todo_Pitfalls_Application.md` §1.
- [ ] **SCA in CI** (Dependabot, OWASP Dependency-Check), not ad hoc in the IDE.
- [ ] **Static security analysis** — FindSecBugs in the build.

## 10. Alternatives to OAuth2 for service-to-service

- [ ] OAuth2 client credentials — standard, scopes, short-lived tokens, central management; an auth server to run.
- [ ] Self-signed JWT — no extra server; key distribution and rotation by hand, no central revocation.
- [ ] mTLS — strong, transport level, no tokens; PKI, certificate rotation, no fine-grained scopes.
- [ ] API key / static secret — simplest; long-lived, no scopes, leaks hard to notice.

## 11. Interview questions to answer out loud

- [ ] Three roles in a service-to-service call; can one service have two?
- [ ] Why `client_credentials` and not `authorization_code`; what's wrong with the password grant?
- [ ] Is a JWT encrypted? What can anyone read?
- [ ] Local validation vs introspection — when introspection?
- [ ] Which claims must be checked; what goes wrong without `aud`?
- [ ] 401 vs 403 — an example of each.
- [ ] Why is CSRF disabled, and when would that be a mistake?
- [ ] You add the OAuth2 client starter and the healthcheck turns red — why?
- [ ] How does the token get onto outgoing requests; which options, why this one?
- [ ] The call comes from a Kafka listener thread — what's special?
- [ ] A token works in one environment and gets 401 in another — first check?
- [ ] How do you test security without Keycloak?
- [ ] The client was revoked in Keycloak — what happens to its token?
- [ ] Alternatives to OAuth2 for service-to-service?

---

## Not here

- Token client timeouts, hidden retries, cache skew, 401 after key rotation, JWKS without timeouts →
  `06_Resilience/todo_Pitfalls_Auth_Server_Dependency.md`.
- Separate management port as isolation (own thread pool) → `09_Operability/4_Infrastructure/todo_Pitfalls_Infrastructure.md`.
