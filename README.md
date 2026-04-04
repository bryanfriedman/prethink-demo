# Moderne Prethink Demo

Demonstrates how Moderne Prethink improves AI coding agent effectiveness by providing rich codebase context upfront.

## Prerequisites

- [Moderne CLI](https://docs.moderne.io/moderne-cli/getting-started/cli-intro) (`mod`) installed and authenticated
- Git
- Python 3 (for token counting)

## Setup

```bash
./init.sh
```

This will:
1. Clone all repositories listed in `repos.csv` into both `with-prethink/` and `no-prethink/`
2. Run `refresh-prethink.sh` in `with-prethink/` to generate Prethink context (build LSTs, run recipe, apply changes)

Use `--skip-prethink` to clone only, without generating context. Use `--clean` to get back to the original root repo state, or `--reset` to clean first and then re-initialize.

## Directory Structure

```
.
├── init.sh                         # Setup script — run this first
├── repos.csv                       # Repositories to clone for the demo
├── session-tokens.sh               # Token usage reporter for agent sessions
├── custom-app/                     # Sample app for custom starter & code quality demos
├── with-prethink/                  # Cloned repos + Prethink context (generated)
│   └── refresh-prethink.sh         # Regenerates Prethink context
└── no-prethink/                    # Cloned repos without context (generated)
```

## Token Counting

Report token usage for a completed agent session:

```bash
# Claude Code (default)
./session-tokens.sh <session-id>

# GitHub Copilot
./session-tokens.sh <session-id> copilot
```

## Demos

### 1. Side-by-Side Token Comparison

_Compare token usage when an agent works with vs. without Prethink context._

**Example Prompts**
* `If I modify the order entity, what other services will be affected?`

### 2. Customized Recipe

_Show how Prethink context from custom recipes gives agents domain-specific understanding._

**Example Prompts**
* `Add a new endpoint to look up a customer's loyalty rewards balance from our external loyalty platform service. Include the service client, controller endpoint, and any required annotations.`

### 3. Code Quality

_Demonstrate how Prethink-informed agents produce higher quality code changes._

**Example Prompts**
* `Add a payment validation method to OrderService`
