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
2. Run `refresh-prethink.sh` on `with-prethink/` to generate Prethink context (build LSTs, run recipe, apply changes)

Use `--skip-prethink` to clone only, without generating context. Use `--clean` to remove cloned directories, or `--reset` to clean and re-initialize.

## Directory Structure

```
.
├── init.sh                         # Setup script — run this first
├── repos.csv                       # Repositories to clone for the demo
├── refresh-prethink.sh             # Regenerates Prethink context for a directory
├── session-tokens.sh               # Token usage reporter for agent sessions
├── DEMOS.md                        # Step-by-step demo walkthrough
├── with-prethink/                  # Cloned repos + Prethink context (generated)
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

See [DEMOS.md](DEMOS.md) for detailed step-by-step instructions for each scenario.
