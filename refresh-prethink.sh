#!/usr/bin/env bash
#
# Refreshes Moderne Prethink context for all repositories under a directory.
# Runs: mod build -> mod run (prethink recipe) -> mod git apply
#
# Usage: ./refresh-prethink.sh <target-dir> [agent]
#   target-dir  Directory containing repositories to refresh
#   agent       Target agent: claude (default), copilot, cursor, windsurf
#               Determines which AI config file to update.
#

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <target-dir> [agent]"
  exit 1
fi

TARGET_DIR="$(cd "$1" && pwd)"
RECIPE="io.moderne.prethink.UpdatePrethinkContextNoAiStarter"
AGENT="${2:-claude}"

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

# Ensure enough heap for the embedded JVM in the polyglot RPC subprocess
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx8G}"

echo "==> Refreshing Prethink context in: ${TARGET_DIR}"
echo "    Agent: ${AGENT}"
echo "    Recipe: ${RECIPE}"
echo "    Target config: ${TARGET_CONFIG}"
echo ""

echo "==> Building LSTs..."
mod build "$TARGET_DIR"

echo ""
echo "==> Running Prethink recipe..."
mod run "$TARGET_DIR" --recipe "${RECIPE}" -PtargetConfigFile="${TARGET_CONFIG}"

echo ""
echo "==> Applying changes..."
mod git apply "$TARGET_DIR" --last-recipe-run

echo ""
echo "==> Prethink context refresh complete."
