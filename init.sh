#!/usr/bin/env bash
#
# Initializes the demo environment by cloning the repositories in repos.csv
# into with-prethink/ and generating Prethink context for them.
#
# Repos:
#   shopizer-ecommerce/shopizer          -> standard Prethink (Demos 1 & 3: platform context + code quality)
#   bryanfriedman/prethink-ecommerce-example -> custom Prethink (Demo 2: platform conventions)
#
# Usage: ./init.sh [--skip-prethink] [--skip-custom-recipe] [--agent <name>]
#                  [--clean] [--reset] [--cli-version <ver>]
#                  [--prethink-version <ver>] [--moderne-prethink-version <ver>]
#   --skip-prethink       Skip running the refresh-prethink step
#   --skip-custom-recipe  Skip the custom recipe against the ecommerce example
#   --agent <name>        Target agent: claude (default), copilot, cursor, windsurf
#   --clean               Remove cloned repos and .moderne artifacts, then exit
#   --reset               Clean and re-initialize (equivalent to --clean + init)
#   --cli-version <ver>   Moderne CLI version (default: 4.2.12 — see note below)
#   --prethink-version <ver>          org.openrewrite.recipe:rewrite-prethink (default: RELEASE)
#   --moderne-prethink-version <ver>  io.moderne.recipe:rewrite-prethink (default: RELEASE)
#
# Version note: CLI is pinned to a stable release (4.2.12). Do NOT use RELEASE/LATEST
# blindly — the 4.3.0-SNAPSHOT line has an LST-deserialization bug that fails on large
# source sets (see memory: prethink-demo-cli-bug). Recipes are pinned to RELEASE (stable).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPOS_CSV="$SCRIPT_DIR/repos.csv"
WITH_DIR="$SCRIPT_DIR/with-prethink"
SKIP_PRETHINK=false
SKIP_CUSTOM_RECIPE=false
AGENT="claude"
CLEAN=false
RESET=false
CLI_VERSION="4.2.12"
PRETHINK_VERSION="RELEASE"
MODERNE_PRETHINK_VERSION="RELEASE"

# Ensure enough heap for the embedded JVM in the polyglot RPC subprocess
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx8G}"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-prethink)      SKIP_PRETHINK=true; shift ;;
    --skip-custom-recipe) SKIP_CUSTOM_RECIPE=true; shift ;;
    --agent)              AGENT="$2"; shift 2 ;;
    --clean)              CLEAN=true; shift ;;
    --reset)              CLEAN=true; RESET=true; shift ;;
    --cli-version)              CLI_VERSION="$2"; shift 2 ;;
    --prethink-version)         PRETHINK_VERSION="$2"; shift 2 ;;
    --moderne-prethink-version) MODERNE_PRETHINK_VERSION="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

# Clean mode (also used by --reset)
if [ "$CLEAN" = true ]; then
  echo "==> Cleaning demo directories..."
  rm -rf "$WITH_DIR" "$SCRIPT_DIR/no-prethink" "$SCRIPT_DIR/.moderne"
  echo "==> Clean complete."
  if [ "$RESET" = false ]; then
    exit 0
  fi
fi

if [ ! -f "$REPOS_CSV" ]; then
  echo "Error: repos.csv not found at $REPOS_CSV"
  exit 1
fi

# Pin CLI and recipe versions for compatibility BEFORE any mod invocations,
# so every subsequent mod call (including git sync) uses the pinned version.
echo "==> Pinning CLI to $CLI_VERSION; installing rewrite-prethink $PRETHINK_VERSION and io.moderne prethink $MODERNE_PRETHINK_VERSION..."
mkdir -p "$HOME/.moderne/cli/dist"
echo "version=$CLI_VERSION" > "$HOME/.moderne/cli/dist/moderne-wrapper.properties"
# Force LST v2 — this is the serialization format the demo context was validated against.
mod config features lst --version=2
mod config recipes jar install "org.openrewrite.recipe:rewrite-prethink:$PRETHINK_VERSION" "io.moderne.recipe:rewrite-prethink:$MODERNE_PRETHINK_VERSION"

# Sync repos into with-prethink/
echo "==> Syncing repos into with-prethink/..."
mkdir -p "$WITH_DIR"
mod git sync csv "$WITH_DIR" "$REPOS_CSV" --with-sources --yes

# Install custom recipe YAML
echo "==> Installing custom recipe YAML..."
mod config recipes yaml install "$WITH_DIR/bryanfriedman/prethink-ecommerce-example/rewrite.yml"

# Create symlinks to session-tokens.sh in each repo directory
echo "==> Creating session-tokens.sh symlinks..."
for dir in \
  "$WITH_DIR/shopizer-ecommerce/shopizer" \
  "$WITH_DIR/bryanfriedman/prethink-ecommerce-example"; do
  ln -sf "$SCRIPT_DIR/session-tokens.sh" "$dir/session-tokens.sh"
done

# Map agent to config file
case "$AGENT" in
  claude)  TARGET_CONFIG="CLAUDE.md" ;;
  copilot) TARGET_CONFIG=".github/copilot-instructions.md" ;;
  cursor)  TARGET_CONFIG=".cursor/rules/prethink.mdc" ;;
  windsurf) TARGET_CONFIG=".windsurf/rules/prethink.md" ;;
esac

# Run prethink refresh
if [ "$SKIP_PRETHINK" = false ]; then
  SHOPIZER="$WITH_DIR/shopizer-ecommerce/shopizer"
  CUSTOM_APP="$WITH_DIR/bryanfriedman/prethink-ecommerce-example"

  # Standard Prethink against shopizer (Demos 1 & 3)
  echo "==> Running Prethink refresh against shopizer..."
  "$SCRIPT_DIR/refresh-prethink.sh" "$SHOPIZER" "$AGENT"

  # Custom Prethink recipe against the ecommerce example (Demo 2), or standard if skipped
  if [ "$SKIP_CUSTOM_RECIPE" = false ]; then
    echo "==> Running custom Prethink recipe against ecommerce example..."
    mod build "$CUSTOM_APP"
    mod run "$CUSTOM_APP" --recipe com.example.prethink.CustomPrethink -PtargetConfigFile="$TARGET_CONFIG"
    mod git apply "$CUSTOM_APP" --last-recipe-run
    mod git add "$CUSTOM_APP" --last-recipe-run
    mod git commit "$CUSTOM_APP" -m "Apply custom prethink recipe." --last-recipe-run
    mod build "$CUSTOM_APP"
  else
    echo "==> Running standard Prethink against ecommerce example..."
    "$SCRIPT_DIR/refresh-prethink.sh" "$CUSTOM_APP" "$AGENT"
  fi
else
  echo "==> Skipping Prethink refresh (--skip-prethink)"
fi

echo ""
echo "==> Init complete."
