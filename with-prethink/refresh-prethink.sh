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

# Ensure enough heap for the embedded JVM in the polyglot RPC subprocess
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx8G}"

echo "==> Refreshing Prethink context in: ${SCRIPT_DIR}"
echo "    Agent: ${AGENT}"
echo "    Recipe: ${RECIPE}"
echo "    Target config: ${TARGET_CONFIG}"
echo ""

# Find all git repos under this directory (excluding this directory itself).
# This is needed because this directory lives inside a parent git repo,
# so mod can't discover nested repos when given the parent path.
REPO_PATHS=()
while IFS= read -r gitdir; do
  REPO_PATHS+=("$(dirname "$gitdir")")
done < <(find "${SCRIPT_DIR}" -mindepth 2 -name .git -type d)

if [ ${#REPO_PATHS[@]} -eq 0 ]; then
  echo "Error: No repositories found under ${SCRIPT_DIR}"
  exit 1
fi

for repo in "${REPO_PATHS[@]}"; do
  echo "==> Building LSTs for $(basename "$repo")..."
  mod build "$repo"

  echo ""
  echo "==> Running Prethink recipe..."
  mod run "$repo" --recipe "${RECIPE}" -PtargetConfigFile="${TARGET_CONFIG}"

  echo ""
  echo "==> Applying changes..."
  mod git apply "$repo" --last-recipe-run
done

echo ""
echo "==> Prethink context refresh complete."
