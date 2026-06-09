#!/usr/bin/env bash
#
# Initializes the demo environment by cloning the repositories in repos.csv into
# two trees: with-prethink/ (Prethink context generated) and no-prethink/ (clean
# clones, left untouched as the baseline for token comparison). Run agent sessions
# in both ahead of time so you can scroll back through them and compare token usage.
#
# Repos:
#   shopizer-ecommerce/shopizer              -> standard Prethink (Demo 1: platform context)
#   bryanfriedman/prethink-ecommerce-example -> custom Prethink  (Demo 2: conventions; Demo 3: code quality)
#
# Usage: ./init.sh [--skip-prethink] [--skip-custom-recipe] [--agent <name>]
#                  [--clean] [--reset] [--cli-version <ver>]
#                  [--prethink-version <ver>] [--moderne-prethink-version <ver>]
#   --skip-prethink       Skip running the refresh-prethink step
#   --skip-custom-recipe  Skip the custom recipe against the ecommerce example
#   --agent <name>        Limit config output to one agent (claude, copilot, cursor,
#                         windsurf). Default: emit both claude and copilot.
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
NO_PRETHINK_DIR="$SCRIPT_DIR/no-prethink"
SKIP_PRETHINK=false
SKIP_CUSTOM_RECIPE=false
# Agent config files to emit. Default: both claude and copilot, so either agent
# works out of the box. Pass --agent <name> to limit to a single one.
AGENTS="claude copilot"
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
    --agent)              AGENTS="$2"; shift 2 ;;
    --clean)              CLEAN=true; shift ;;
    --reset)              CLEAN=true; RESET=true; shift ;;
    --cli-version)              CLI_VERSION="$2"; shift 2 ;;
    --prethink-version)         PRETHINK_VERSION="$2"; shift 2 ;;
    --moderne-prethink-version) MODERNE_PRETHINK_VERSION="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

# Map an agent name to its config file path (relative to the repo root)
agent_config_path() {
  case "$1" in
    claude)   echo "CLAUDE.md" ;;
    copilot)  echo ".github/copilot-instructions.md" ;;
    cursor)   echo ".cursor/rules/prethink.mdc" ;;
    windsurf) echo ".windsurf/rules/prethink.md" ;;
    *)        echo "Unknown agent: $1" >&2; return 1 ;;
  esac
}

# The recipe runs once and writes the primary agent's config; the Prethink context
# instructions are agent-agnostic (they just point at .moderne/context/, with paths
# that resolve from the repo root for any agent), so the remaining agents' configs
# are exact copies at their own paths.
PRIMARY_AGENT="${AGENTS%% *}"
for a in $AGENTS; do agent_config_path "$a" >/dev/null || exit 1; done

# Copy the primary config to every other requested agent's path and commit them.
emit_extra_agent_configs() {
  local repo="$1"
  local primary_path; primary_path="$(agent_config_path "$PRIMARY_AGENT")"
  local committed=false
  for a in $AGENTS; do
    [ "$a" = "$PRIMARY_AGENT" ] && continue
    local path; path="$(agent_config_path "$a")"
    mkdir -p "$repo/$(dirname "$path")"
    cp "$repo/$primary_path" "$repo/$path"
    git -C "$repo" add "$path"
    committed=true
  done
  if [ "$committed" = true ]; then
    git -C "$repo" commit -q -m "Add agent config(s): ${AGENTS#"$PRIMARY_AGENT" }"
  fi
}

# Clean mode (also used by --reset)
if [ "$CLEAN" = true ]; then
  echo "==> Cleaning demo directories..."
  rm -rf "$WITH_DIR" "$NO_PRETHINK_DIR" "$SCRIPT_DIR/.moderne"
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

# Sync the same repos into no-prethink/ as clean clones — the baseline for token
# comparison. Same branches/commits as with-prethink, but no Prethink context is
# generated here; these are left exactly as an agent would see them unaided.
echo "==> Syncing repos into no-prethink/ (clean baseline, no Prethink context)..."
mkdir -p "$NO_PRETHINK_DIR"
mod git sync csv "$NO_PRETHINK_DIR" "$REPOS_CSV" --yes

# Install custom recipe YAML
echo "==> Installing custom recipe YAML..."
mod config recipes yaml install "$WITH_DIR/bryanfriedman/prethink-ecommerce-example/rewrite.yml"

# Create symlinks to session-tokens.sh in each repo directory (both trees, so you
# can report tokens from with-prethink and no-prethink sessions alike)
echo "==> Creating session-tokens.sh symlinks..."
for dir in \
  "$WITH_DIR/shopizer-ecommerce/shopizer" \
  "$WITH_DIR/bryanfriedman/prethink-ecommerce-example" \
  "$NO_PRETHINK_DIR/shopizer-ecommerce/shopizer" \
  "$NO_PRETHINK_DIR/bryanfriedman/prethink-ecommerce-example"; do
  ln -sf "$SCRIPT_DIR/session-tokens.sh" "$dir/session-tokens.sh"
done

# Primary agent's config file — the recipe writes this; the rest are copied from it
TARGET_CONFIG="$(agent_config_path "$PRIMARY_AGENT")"

# Run prethink refresh
if [ "$SKIP_PRETHINK" = false ]; then
  SHOPIZER="$WITH_DIR/shopizer-ecommerce/shopizer"
  CUSTOM_APP="$WITH_DIR/bryanfriedman/prethink-ecommerce-example"

  echo "==> Emitting agent configs for: $AGENTS"

  # Standard Prethink against shopizer (Demo 1)
  echo "==> Running Prethink refresh against shopizer..."
  "$SCRIPT_DIR/refresh-prethink.sh" "$SHOPIZER" "$PRIMARY_AGENT"
  emit_extra_agent_configs "$SHOPIZER"

  # Custom Prethink recipe against the ecommerce example (Demos 2 & 3), or standard if skipped
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
    "$SCRIPT_DIR/refresh-prethink.sh" "$CUSTOM_APP" "$PRIMARY_AGENT"
  fi
  emit_extra_agent_configs "$CUSTOM_APP"
else
  echo "==> Skipping Prethink refresh (--skip-prethink)"
fi

echo ""
echo "==> Init complete."
