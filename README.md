# Moderne Prethink Demo

Demonstrates how Moderne Prethink improves AI coding agent effectiveness by providing rich, resolved codebase context upfront — so agents reason from facts instead of spelunking.

## Prerequisites

- [Moderne CLI](https://docs.moderne.io/moderne-cli/getting-started/cli-intro) (`mod`) installed and authenticated
- Git
- Python 3 (for token counting)

## Setup

```bash
./init.sh
```

Clones the demo repos into two trees — `with-prethink/` (Prethink context generated) and `no-prethink/` (clean clones, the baseline for token comparison) — and generates context for the former. Use `--skip-prethink` to clone only, or `--reset` to start fresh.

> The CLI is pinned to a stable release (`4.2.12`); pass `--cli-version` to override.

## Demos

Three single-run demos, each showing an agent working from resolved Prethink context. See [DEMOS.md](DEMOS.md) for step-by-step instructions.

1. **Prethink on a real platform** — Open the context Prethink resolves from Shopizer, a large open-source Spring e-commerce platform: architecture map, API contracts, quality metrics, test gaps. All deterministic — none of it requiring the agent to read source.
2. **Customizing Prethink** — A custom recipe teaches Prethink to discover your platform's own conventions (a required base client, rate-limiting and audit annotations). The agent then applies them on a new endpoint, first try.
3. **Code quality as agent feedback** — Prethink flags a God Class (`OrderService`) in the ecommerce example with metric evidence. Asked to add to it, a quality-aware agent cites the signal and refactors instead of piling on.

## Token usage

Report token usage for a completed agent session. Run the same prompt in both `with-prethink/` and `no-prethink/` ahead of time, then compare:

```bash
./session-tokens.sh <session-id>           # Claude Code
./session-tokens.sh <session-id> copilot   # GitHub Copilot
```
