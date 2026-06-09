# Prethink Demos

Three short demos showing how Moderne Prethink gives AI coding agents resolved, structured context to work from — so they reason from facts instead of exploring blindly. Step-by-step walkthroughs are below.

| Demo | Repo | What it shows |
|------|------|---------------|
| **1 — Prethink on the SaaS Platform** | `shopizer` | Generate context on the platform, then watch a local agent use it |
| **2 — Customizing Prethink** | `prethink-ecommerce-example` | Extend Prethink to discover your own platform conventions; the agent follows them |
| **3 — Code quality as agent feedback** | `prethink-ecommerce-example` | The agent reasons from a God Class signal instead of piling on |

## Prerequisites

Run `./init.sh` to set up the demo environment (pins the CLI to a stable `4.2.12`, clones the repos, generates Prethink context). It writes **both** `CLAUDE.md` and `.github/copilot-instructions.md`, so you can run either agent without re-initializing.

```bash
./init.sh                  # both Claude + Copilot configs (default)
./init.sh --agent copilot  # limit to a single agent if you prefer
```

Install helpers if needed: `brew install tree duckdb`.

### Two trees: `with-prethink/` and `no-prethink/`

`init.sh` clones the repos twice:

- **`with-prethink/`** — Prethink context generated; this is where the live demos run.
- **`no-prethink/`** — the same repos at the same commits, left untouched (no `.moderne/context`, no `CLAUDE.md`). This is the baseline.

We're **not** doing a live side-by-side. Instead, run each demo's agent session in both trees **ahead of time** so you know the results are what you want — then scroll back through the recorded sessions during the talk, and use them to compare token usage (`session-tokens.sh`, below). The no-prethink runs stay off-screen unless you want to call out the contrast.

---

## Demo 1 — Prethink on the SaaS Platform

_Two parts. First, on the Moderne Platform, see how the context is generated and what's in it. Then, in a local agent, watch it get used. This is "Prethink with agents" at the base level — the customization comes in Demo 2._

### Part 1 — On the platform: the recipe and the context

Done live on the **Moderne Platform** — there's no local step for this part, just the walkthrough:

- Run the Prethink recipe against `shopizer` and watch it produce the context.
- Walk the data tables it generates — architecture, API contracts, quality metrics, test gaps — the resolved knowledge, built deterministically from the LST (no AI, no embeddings).
- This is exactly what lands in the repo as `.moderne/context/`, alongside a `CLAUDE.md` that tells the agent to read it first. `shopizer` is a real OSS Spring platform, ~6,500 methods — far too big for an agent to read its way through.

### Part 2 — In the local agent: using the context

The context is already populated in the repo (`init.sh` committed it). Switch to the agent and prompt it.

```bash
cd with-prethink/shopizer-ecommerce/shopizer
claude     # or: copilot
```

**Prompt:** `Which parts of the platform handle order shipping, and what external shipping carriers does it integrate with?`

**Goal:** the agent answers entirely from `.moderne/context/` — `architecture.md` for the shipping components, `service-endpoints.csv` for the `/shipping/*` APIs, and `external-service-calls.csv` for the USPS/UPS carrier integrations — not by exploring across 6,500 methods. It cites Prethink as the source.

> This is an **orientation** question in the platform's own vocabulary (order, shipping, carriers), so the resolved context answers it completely. Avoid "how would I add/change X" phrasing here — that pulls the agent into reading source to learn the extension pattern (which is the point of Demos 2 and 3, not this one).

### What to look for

- A few targeted context lookups instead of a sprawl of source-file exploration — resolved context, not retrieved text.
- The answer is accurate because the facts were precomputed, not inferred.

---

## Demo 2 — Customizing Prethink

_The second part of "Prethink with agents." Demo 1 used the context Prethink generates out of the box; here you **extend** Prethink to discover your org's own rules, and the agent follows them. The customization is the point; the agent following the rules is the payoff._

### The customization (the hero beat)

`prethink-ecommerce-example` ships a custom Prethink recipe in `rewrite.yml`. It extends the standard starter with discovery of three platform rules that exist nowhere in the file an agent would be editing:

```bash
cd with-prethink/bryanfriedman/prethink-ecommerce-example
cat rewrite.yml
```

Walk what it does:
- Starts from the standard `io.moderne.prethink.UpdatePrethinkContextNoAiStarter` (architecture, quality, tests…)
- Adds `FindAnnotations` for `@RateLimited` and `@Auditable`, and `FindTypes` for the platform `ServiceClient`
- Pipes each into `ExportContext` with a `longDescription` that tells the agent **when and why** to apply the rule

The three rules it teaches Prethink to surface:
- `ServiceClient` — required base class for all service-to-service communication
- `@RateLimited` — required on public-facing write / expensive-query endpoints
- `@Auditable` — required on state-changing operations (orders, payments, inventory)

### The context that customization produces

Only because of that recipe, the agent gets two context files the standard starter would never generate:

```bash
cat .moderne/context/platform-service-client-usage.md
cat .moderne/context/rate-limited-and-auditable-methods.md
```

Note the embedded `longDescription` — your instructions, delivered to any agent as resolved context.

_(Optional — run the custom recipe live to regenerate the context:)_

```bash
mod build .
mod run . --recipe com.example.prethink.CustomPrethink -PtargetConfigFile=CLAUDE.md
mod git apply . --last-recipe-run
```

### Goal (state this up front)

A correct implementation will:
1. Use **`ServiceClient`** as the base class for the external call (not raw `RestTemplate` / `WebClient`)
2. Add **`@RateLimited`** to the new public endpoint
3. Add **`@Auditable`** to the state-changing operation

### Run

```bash
claude     # or: copilot
```

**Prompt:** `Add a new endpoint to redeem loyalty rewards points for a customer using our external loyalty platform service.`

### What to look for

- The payoff of the customization: the agent reads **your** custom-discovered context and checks all three boxes — applying rules that are invisible in the surrounding code.
- The takeaway isn't just "the agent followed conventions" — it's that **you taught Prethink to surface them**, deterministically, for any agent on any task. Prethink is a platform you extend, not a fixed feature.

---

## Demo 3 — Code quality as agent feedback

_Two parts, like Demo 1. First, on the Moderne Platform, see the code-quality intelligence Prethink computes. Then, in a local agent, watch it reason from that same data on a real God Class._

### Part 1 — On the platform: the quality visualizations

Done live on the **Moderne Platform** — the same recipes that produce agent-readable CSVs also power human-facing views:

- Debt treemap — methods sized by volume, colored by debt
- Coupling–cohesion quadrant — healthy / spaghetti / hub / island
- Test-gap heatmap — untested methods by risk score
- Maintainability dashboard across repos

This is the quality signal Prethink resolves from the LST — and it's the same data the agent reads in Part 2.

### Part 2 — In the local agent: reasoning from the signal

`prethink-ecommerce-example` has a deliberately overloaded service class. The headline offender:

- **`OrderService`** → `GOD_CLASS`, severity **HIGH**, evidence `WMC=70, TCC=0.29, ATFD=50` (393 lines, 19 methods)

Inside it, the worst feature-envy methods make the case concrete — logic that clearly belongs elsewhere: `calculateFinalPrice` (CRITICAL, 101 foreign accesses) and `formatCustomerInvoice` (CRITICAL, 50).

```bash
cd with-prethink/bryanfriedman/prethink-ecommerce-example
duckdb -c "SELECT \"Class name\", Severity, Evidence FROM '.moderne/context/code-smells.csv' WHERE \"Smell type\"='GOD_CLASS' ORDER BY 1"
```

Prethink computed this deterministically from the LST — the same metric evidence the agent reads.

### Goal (state this up front)

A quality-aware change will:
1. **Recognize `OrderService` is already a God Class** and cite the metric evidence from Prethink
2. **Not add another method to it** (which would make cohesion worse)
3. **Recommend extraction** — a focused collaborator (e.g. a dedicated calculator/service) instead

### Run

```bash
claude     # or: copilot
```

**Prompt:** `Add logic to OrderService to calculate loyalty points earned for a completed order. Follow the code quality standards as defined in the Prethink context.`

### What to look for

- The agent queries `code-smells.csv` / `class-quality-metrics.csv`, sees `OrderService` is a HIGH-severity God Class, and **pushes back on adding to it** — proposing a separate, cohesive component and citing the metric.
- **Test-health tease:** `cat .moderne/context/test-gaps.md` — Prethink also ranks untested, high-risk methods, so the agent knows where new code needs coverage. Quality data goes to the *agent*, not just a dashboard.

---

## Reporting token usage (optional)

Report a completed session's token usage with `session-tokens.sh`. Run the same prompt in both trees ahead of time (`with-prethink/` and `no-prethink/`), then pull the numbers for each session to show the contrast:

```bash
./session-tokens.sh <session-id>            # Claude: session ID via /status or on exit
./session-tokens.sh <session-id> copilot    # Copilot: /session info
```
