# Prethink Demos

## Prerequisites

Run `./init.sh` to set up the demo environment. Use `--agent copilot` if demoing Copilot.

```bash
# For Claude (default)
./init.sh

# For Copilot
./init.sh --agent copilot
```

Install `tree` if you don't have it: `brew install tree`

---

## 1. Side-by-Side Token Comparison

_Compare token usage when an agent works with vs. without Prethink context._

### Setup

Open two terminal windows side by side.

#### Claude

```bash
# Terminal 1 — No Prethink
cd no-prethink/nashtech-garage/yas/
ls CLAUDE.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/nashtech-garage/yas/
ls CLAUDE.md && tree .moderne/context
```

#### Copilot

```bash
# Terminal 1 — No Prethink
cd no-prethink/nashtech-garage/yas/
ls .github/copilot-instructions.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/nashtech-garage/yas/
ls .github/copilot-instructions.md && tree .moderne/context
```

In Terminal 2, take a look at the agent instructions file to see what Prethink generated (`cat CLAUDE.md` or `cat .github/copilot-instructions.md`).

### Run the Agent

Use the same prompt in both terminals, then compare token counts.

#### Claude

```bash
# Both terminals
claude
```

#### Copilot

```bash
# Both terminals
copilot
```

### Prompt

```
If I modify the order entity, what other services will be affected?
```

### Check Token Usage

After both sessions finish, grab the session IDs and compare.

#### Claude

Session ID is shown when you exit Claude Code or via the `/status` command.

```bash
./session-tokens.sh <no-prethink-session-id>
./session-tokens.sh <with-prethink-session-id>
```

#### Copilot

Session ID is available via `/session info` inside Copilot.

```bash
./session-tokens.sh <no-prethink-session-id> copilot
./session-tokens.sh <with-prethink-session-id> copilot
```

### What to Look For

- The no-prethink agent will spawn subagents and read many files to explore the codebase
- The with-prethink agent has architecture context already and should answer more directly
- Compare total token counts — with-prethink should use significantly fewer tokens

---

## 2. Custom Prethink Recipe

_Show how a custom Prethink recipe gives agents domain-specific understanding of platform conventions._

### Background

The prethink-ecommerce-example app has a `rewrite.yml` that extends the standard Prethink recipe with discovery of:
- `@RateLimited` — required on public-facing write/query endpoints
- `@Auditable` — required on state-changing operations (orders, payments, inventory)
- `ServiceClient` — required base class for all service-to-service communication

The custom recipe is installed and run automatically by `init.sh`. If you want to run the custom recipe live as part of the demo, use `--skip-custom-recipe` during init and run it manually:

```bash
cd with-prethink/bryanfriedman/prethink-ecommerce-example/
mod build .
mod run . --recipe com.example.prethink.CustomPrethink -PtargetConfigFile=CLAUDE.md
mod git apply . --last-recipe-run
```

Before running the agent, look at the generated context files in `.moderne/context/` — especially the markdown files for the custom discoveries. The `longDescription` from `rewrite.yml` gets embedded into the context, giving the agent explicit instructions about when and how to apply each convention.

### Setup

Open two terminal windows side by side.

#### Claude

```bash
# Terminal 1 — No Prethink
cd no-prethink/bryanfriedman/prethink-ecommerce-example/
ls CLAUDE.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/bryanfriedman/prethink-ecommerce-example/
ls CLAUDE.md && tree .moderne/context
```

#### Copilot

```bash
# Terminal 1 — No Prethink
cd no-prethink/bryanfriedman/prethink-ecommerce-example/
ls .github/copilot-instructions.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/bryanfriedman/prethink-ecommerce-example/
ls .github/copilot-instructions.md && tree .moderne/context
```

### Run the Agent

#### Claude

```bash
# Both terminals
claude
```

#### Copilot

```bash
# Both terminals
copilot
```

### Prompt

```
Add a new endpoint to redeem loyalty rewards points for a customer using our external loyalty platform service. Follow the project's existing patterns for service clients and controller conventions.
```

### What to Look For

- **With Prethink:** The agent should use `ServiceClient` as the base class (not raw `RestTemplate` or `WebClient`), and add `@RateLimited` and `@Auditable` annotations following the platform conventions
- **Without Prethink:** The agent will typically miss the platform conventions — using `RestTemplate`/`WebClient` directly and omitting the required annotations

---

## 3. Code Quality

_Demonstrate how Prethink-informed agents produce higher quality code — specifically, recognizing LCOM (Lack of Cohesion of Methods) and avoiding making a bloated class worse._

### Background

`OrderService` in the prethink-ecommerce-example app was intentionally designed with three distinct responsibility groups:
1. **Order lifecycle** — uses `orderRepository`, `orderValidator`
2. **Reporting/analytics** — uses `reportingDao`, `metricsCollector`
3. **Notifications** — uses `emailService`, `notificationConfig`

Plus a feature-envy method (`formatCustomerInvoice`) and a high-complexity method (`calculateFinalPrice`). Prethink's code quality analysis should flag the high LCOM score.

### Setup

Open two terminal windows side by side. Prethink context should already be generated from `init.sh`.

#### Claude

```bash
# Terminal 1 — No Prethink
cd no-prethink/bryanfriedman/prethink-ecommerce-example/
ls CLAUDE.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/bryanfriedman/prethink-ecommerce-example/
ls CLAUDE.md && tree .moderne/context
```

#### Copilot

```bash
# Terminal 1 — No Prethink
cd no-prethink/bryanfriedman/prethink-ecommerce-example/
ls .github/copilot-instructions.md && tree .moderne/context

# Terminal 2 — With Prethink
cd with-prethink/bryanfriedman/prethink-ecommerce-example/
ls .github/copilot-instructions.md && tree .moderne/context
```

### Run the Agent

#### Claude

```bash
# Both terminals
claude
```

#### Copilot

```bash
# Both terminals
copilot
```

### Prompt

```
Add a payment validation method to OrderService
```

### What to Look For

- **With Prethink:** The agent should notice the LCOM stat and recognize that `OrderService` already has too many responsibilities. Instead of blindly adding another method, it should suggest refactoring — e.g., extracting payment validation into a separate service or adding it to the existing `OrderValidator`
- **Without Prethink:** The agent will likely just add a new `validatePayment()` method directly to `OrderService`, making the cohesion problem worse
- The key insight is that Prethink context helps agents make architectural decisions, not just syntactically correct ones
