#!/usr/bin/env bash
#
# Refreshes Moderne Prethink context for all repositories under this directory.
# Runs: mod build -> mod run (prethink recipe) -> mod git apply
#
# Usage: ./refresh-prethink.sh [agent]
#   agent  Target agent: claude (default), copilot, cursor, windsurf
#          Determines which AI config file to update.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RECIPE="io.moderne.prethink.UpdatePrethinkContextNoAiStarter"
AGENT="${1:-claude}"

# Map agent name to its config file
case "$AGENT" in
  claude)
    TARGET_CONFIG="CLAUDE.md"
    ;;
  copilot)
    TARGET_CONFIG=".github/copilot-instructions.md"
    ;;
  cursor)
    TARGET_CONFIG=".cursor/rules/prethink.mdc"
    ;;
  windsurf)
    TARGET_CONFIG=".windsurf/rules/prethink.md"
    ;;
  *)
    echo "Unknown agent: $AGENT"
    echo "Supported: claude, copilot, cursor, windsurf"
    exit 1
    ;;
esac

echo "==> Refreshing Prethink context in: ${SCRIPT_DIR}"
echo "    Agent: ${AGENT}"
echo "    Recipe: ${RECIPE}"
echo "    Target config: ${TARGET_CONFIG}"
echo ""

# Step 1: Build LSTs
echo "==> Building LSTs..."
mod build "${SCRIPT_DIR}"

# Step 2: Run prethink recipe
echo ""
echo "==> Running Prethink recipe..."
mod run "${SCRIPT_DIR}" --recipe "${RECIPE}" -PtargetConfigFile="${TARGET_CONFIG}"

# Step 3: Apply changes
echo ""
echo "==> Applying changes..."
mod git apply "${SCRIPT_DIR}" --last-recipe-run

echo ""
echo "==> Prethink context refresh complete."
