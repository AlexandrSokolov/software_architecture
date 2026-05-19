# Document 2: Enterprise Integration Patterns (EIP)

## Cross-Workspace Telemetry & Hub-and-Spoke Remediation

This document defines the messaging, routing, and network communication patterns required to pass
telemetry and manage automated errors across partitioned enterprise environments.

### 1. Cross-Workspace Connectivity & Platform Boundaries
Implementing centralized error management within enterprise integration platforms (like Workato)
requires navigating strict multi-tenant and workspace security boundaries.

#### The Workspace Isolation Challenge
In modern iPaaS architectures, native storage assets (Data Tables, Pub/Sub topics) are rigidly
encapsulated inside individual Workspaces (e.g., HR Workspace vs. Finance Workspace). A recipe in
Workspace B cannot natively query a table in Workspace A. To construct a global monitoring platform,
data must cross these boundaries via standardized, secure application-layer protocols.

#### Why Email is a High-Risk Data Pipeline (Anti-Pattern)
Decoupling cross-workspace errors by routing alerts via email to a central mailbox parser introduces
severe operational vulnerabilities:

* **Structural Corruption:** Mail servers inject HTML tags and wrap lines, corrupting raw JSON.
* **Data Truncation:** Large Java stack traces exceed email character thresholds and are truncated.
* **Rate-Limiting Collapses:** A downstream ERP crash can generate thousands of errors per minute,
  flooding email infrastructure, triggering spam filters, and blocking service accounts.
* **Compliance Violations:** Transmitting PII or secure tokens embedded in error logs over
  unencrypted SMTP introduces major compliance risks.

#### The Secure Bridge: Central HTTP Webhooks
All cross-workspace communications must utilize standard HTTP infrastructure. The central repository
workspace exposes a single API Endpoint / Webhook secured via token authentication. External
workspaces transmit clean JSON payloads directly to this gateway using encrypted HTTPS POST requests.

### 2. The Hub-and-Spoke Telemetry & Remediation Pattern
To achieve a balance between local workflow autonomy (immediate error reaction) and global visibility
(centralized business intelligence), systems must follow a tiered Hub-and-Spoke messaging layout.

#### The Spoke Level (The Local Workspace)
Every autonomous workspace maintains its independent business recipes, a localized Pub/Sub Error
Topic, and a single Workspace Subscriber Recipe.

* **The Business Recipes:** Core execution paths are wrapped in standard Try/Catch blocks. If a
  step fails, the Catch block executes a fire-and-forget publish to the local Pub/Sub topic.
* **The Workspace Subscriber (The Buffer):** Listens to the local error topic and executes two tracks:
    1. **Immediate Telemetry Transfer:** Forwards the unedited payload to the Central HTTP Webhook.
    2. **Localized Remediation:** Evaluates the error context and triggers a local retry script if
       the issue is a known, resolvable data problem.

#### The Hub Level (The Central Workspace)
The central workspace acts as the master processing and analytics core for the organization.

* **The Central HTTP Webhook:** The single gateway for all spoke subscriber webhooks.
* **The Central Classification Engine:** Directs incoming messages through the data rules matrix
  and commits the structured rows to the master log database.
* **The Global Monitor Dashboard:** Aggregates data from the central table to render real-time
  analytics concerning the health and failure trends of all client environments.

#### The Maintenance Advantage of the Subscriber Buffer
Utilizing a local Workspace Subscriber recipe as an abstraction layer prevents architectural blast
radius. If every business workflow is wired directly to an external Central Webhook URL, the system
becomes tightly coupled. Changing that URL requires redeploying every recipe.

By introducing a local Pub/Sub topic and a single Subscriber recipe acting as an adapter, the
core infrastructure achieves loose coupling. If the Central Hub changes its API, you modify exactly
ONE Subscriber recipe per workspace, leaving core business logic completely untouched.