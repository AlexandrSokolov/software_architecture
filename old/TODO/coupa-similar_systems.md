Good question. A few real reasons, none of them about storing intermediate results:
Credentials. Coupa API tokens live in Workato, not in the browser. If the Angular app called Coupa directly, the token would be exposed to every user's browser.
CORS / auth complexity. Coupa's API isn't designed to be called from a browser. Workato exposes simple HTTP endpoints the SPA can call cleanly.
Logic centralization. "Get commodities" isn't one Coupa call — it's likely multiple calls plus shaping. Workato hides that complexity behind one endpoint.
Reusability. The same Workato recipes can be called by other integrations, not just this form. If logic lived in Angular, it'd be locked to this app.
Observability + error handling. Workato logs every call, retries, alerts on failures. The browser can't.
So Workato is acting as a backend-for-frontend / API gateway, not as storage. Nothing persists there between the call and the response — it's pass-through with credentials, shaping, and logging.


Question:
You're building a web application that lets users in System A create records in System B. System B is a third-party SaaS platform with a REST API.
The naive design is: the browser-based UI talks to System B's API directly. Your tech lead pushes back and says you need an integration platform (like Workato, MuleSoft, or a custom backend service) sitting between the UI and System B.
Why? What problems does that middle layer actually solve, and under what conditions would the direct approach be acceptable instead?

That's deliberately under-specified. To answer it well you'd need to ask me things like:

Who are the users? Are they authenticated, and how?
Does System B's API support browser-based calls (CORS, public client auth)?
How are credentials to System B issued — per-user, or one shared service account?
How complex is a single "create record" operation in System B — one call or many?
Are there other systems that will need to do the same integration later?
What are the observability, audit, and compliance requirements?


Architectural Interview: Why an Integration Layer?
Interviewer: You're building a web application that lets users in System A create records in System B. System B is a third-party SaaS platform with a REST API. The naive design is: the browser-based UI talks to System B's API directly. Your tech lead pushes back and says you need an integration platform sitting between the UI and System B. Why? Under what conditions would the direct approach be acceptable?
Candidate: Before I answer, I need to understand the constraints. Let me ask a few clarifying questions.
First — who are the users, and how do they authenticate to System B? Do they have individual accounts there, or does the application use a single service account?
Interviewer: Users do not have individual accounts in System B. The application uses a single API token issued to the integration. All users acting through the UI share that token's permissions.
Candidate: That's already significant. If the browser were to call System B directly, that shared token would have to be present in the browser — either embedded in the JavaScript bundle or fetched at runtime. Either way, anyone opening DevTools could extract it and call System B's API with the application's full privileges, outside the UI.
So the token has to live server-side. That alone forces some kind of intermediary.
Next question — does System B's API support being called from a browser? Specifically, does it return appropriate CORS headers, and is its authentication scheme designed for public clients?
Interviewer: No. System B's API expects server-to-server calls. It doesn't return CORS headers, and its auth uses long-lived bearer tokens with no concept of per-user scoping or short-lived credentials. It was never designed for browser consumption.
Candidate: Then direct browser calls are technically blocked, not just inadvisable. The browser would reject the requests on CORS grounds, and even if you proxied around that, the bearer-token model is incompatible with safe public-client use.
So we now have two independent reasons the browser can't talk to System B: credentials must be protected server-side, and the API itself isn't browser-compatible.
Let me ask about the operations themselves. How complex is a single user-facing action? When a user does "create a record," is that one call to System B, or several?
Interviewer: Several. For example, to populate the form's dropdowns, you need to fetch reference data from multiple endpoints — users, suppliers, accounts, a hierarchical commodity tree. The commodity tree in particular comes back flat and has to be reshaped into a nested structure for the UI. Creating the record itself involves validation against several lookups before the final POST.
Candidate: So even setting aside the credential and CORS problems, putting that orchestration in the browser would mean the UI is making five or ten calls per page load, doing data shaping, and holding business logic about which fields are required and how lookups relate. That couples the UI tightly to System B's API surface. Every change in System B — endpoint renames, response shape changes, new validation rules — would force a frontend release.
A middle layer lets you expose one coarse-grained endpoint per user intent — get-form-data, create-record — and absorb System B's call patterns internally. The UI becomes thin.
One more question on scope. Is this UI the only thing that will ever need to integrate with System B, or are there other workflows planned?
Interviewer: There are other workflows — scheduled syncs, batch imports from other systems, possibly a mobile app later. All of them need to talk to System B.
Candidate: Then there's a reusability argument as well. If the orchestration logic — "how do we create a record in System B correctly, with all the right lookups and validation" — lives in the Angular app, it's locked to that app. Any other workflow has to re-implement it. If it lives in the integration layer, every consumer calls the same well-tested endpoint.
Last question — what are the operational requirements? Logging, retries on transient failures, alerting when System B is down or rate-limiting us?
Interviewer: All of those matter. The team needs to be able to see every call, replay failures, and get paged when integration health degrades.
Candidate: That's the final piece. Browsers are a terrible place for that kind of observability — you'd be reliant on client-side error reporting, with no server-side record of what was attempted, no retry queue, no central place to alert from. An integration layer gives you that for free, or close to it.

Candidate (summary): Putting it together, the middle layer earns its keep on five concerns:
Credentials. System B's API token must live server-side. If the browser called System B directly, the token would be exposed in every user's browser and could be extracted and abused. The integration layer holds the token and never sends it to the client.
CORS and auth model. System B's API isn't designed for browser consumption — no CORS headers, bearer-token auth meant for server-to-server. The integration layer exposes browser-friendly HTTPS endpoints that the SPA can call cleanly, and translates them into server-to-server calls behind the scenes.
Logic centralization. A single user action ("show me the form," "submit the record") maps to multiple calls plus data shaping in System B. The integration layer hides that complexity behind one endpoint per intent, keeping the UI thin and decoupled from System B's call patterns.
Reusability. The same integration endpoints can be consumed by other workflows — scheduled syncs, batch jobs, future apps — not just this UI. If the orchestration lived in the frontend, every new consumer would have to re-implement it.
Observability and error handling. The integration layer logs every call, retries transient failures, and alerts on degraded health. The browser can't do any of that reliably.
So the middle layer is acting as a backend-for-frontend and API gateway — not a data store. Nothing persists between request and response. It's a pass-through that handles credentials, shaping, reuse, and operations.
When would direct browser-to-System-B be acceptable? Only if all five conditions reverse: per-user OAuth with short-lived tokens (no shared secret to protect), CORS-enabled and browser-friendly API on System B's side, single-call operations with no orchestration, no other consumers planned, and no operational requirements beyond what client-side telemetry provides. In practice that combination is rare for enterprise SaaS integrations.

Candidate (summary): Putting it together, the middle layer earns its keep on five concerns:
Credentials. System B's API token must live server-side. If the browser called System B directly, the token would be exposed in every user's browser and could be extracted and abused. The integration layer holds the token and never sends it to the client.
CORS and auth model. System B's API isn't designed for browser consumption — no CORS headers, bearer-token auth meant for server-to-server. The integration layer exposes browser-friendly HTTPS endpoints that the SPA can call cleanly, and translates them into server-to-server calls behind the scenes.
Logic centralization. A single user action ("show me the form," "submit the record") maps to multiple calls plus data shaping in System B. The integration layer hides that complexity behind one endpoint per intent, keeping the UI thin and decoupled from System B's call patterns.
Reusability. The same integration endpoints can be consumed by other workflows — scheduled syncs, batch jobs, future apps — not just this UI. If the orchestration lived in the frontend, every new consumer would have to re-implement it.
Observability and error handling. The integration layer logs every call, retries transient failures, and alerts on degraded health. The browser can't do any of that reliably.
So the middle layer is acting as a backend-for-frontend and API gateway — not a data store. Nothing persists between request and response. It's a pass-through that handles credentials, shaping, reuse, and operations.
When would direct browser-to-System-B be acceptable? Only if all five conditions reverse: per-user OAuth with short-lived tokens (no shared secret to protect), CORS-enabled and browser-friendly API on System B's side, single-call operations with no orchestration, no other consumers planned, and no operational requirements beyond what client-side telemetry provides. In practice that combination is rare for enterprise SaaS integrations.


When Direct Calls Are Acceptable
The graph also tells you the negative answer cleanly: direct browser-to-System-B is acceptable only when every fork resolves to the left-hand branch. Specifically:

A1a: per-user OAuth with short-lived tokens (no shared secret to protect)
A2a: CORS-enabled, browser-friendly auth on System B
A3a: single-call operations with no orchestration
A4a: no other consumers planned, ever
A5a: no operational requirements beyond client telemetry

If even one fork goes right, the middle layer is justified. In practice all five going left is rare for enterprise SaaS integrations — which is why the pattern is so common.