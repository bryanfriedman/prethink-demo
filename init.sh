#!/usr/bin/env bash
#
# Initializes the demo environment by cloning repositories from repos.csv
# into both with-prethink/ and no-prethink/ directories, then generates
# Prethink context in with-prethink/.
#
# Usage: ./init.sh [--skip-prethink] [--agent <name>] [--clean] [--reset]
#                  [--cli-version <ver>] [--prethink-version <ver>]
#                  [--moderne-prethink-version <ver>]
#   --skip-prethink       Skip running the refresh-prethink step
#   --skip-custom-recipe  Skip running the custom recipe against ecommerce example
#   --agent <name>        Target agent: claude (default), copilot, cursor, windsurf
#   --clean               Remove cloned repos and .moderne artifacts, then exit
#   --reset               Clean and re-initialize (equivalent to --clean + init)
#   --cli-version <ver>   Moderne CLI version (default: 4.0.6)
#   --prethink-version <ver>  org.openrewrite.recipe:rewrite-prethink version (default: 0.3.5)
#   --moderne-prethink-version <ver>  io.moderne.recipe:rewrite-prethink version (default: 0.4.0)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPOS_CSV="$SCRIPT_DIR/repos.csv"
WITH_DIR="$SCRIPT_DIR/with-prethink"
WITHOUT_DIR="$SCRIPT_DIR/no-prethink"
SKIP_PRETHINK=false
SKIP_CUSTOM_RECIPE=false
AGENT="claude"
CLEAN=false
RESET=false
CLI_VERSION="4.0.6"
PRETHINK_VERSION="0.3.5"
MODERNE_PRETHINK_VERSION="0.4.0"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-prethink)
      SKIP_PRETHINK=true
      shift
      ;;
    --skip-custom-recipe)
      SKIP_CUSTOM_RECIPE=true
      shift
      ;;
    --agent)
      AGENT="$2"
      shift 2
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --reset)
      CLEAN=true
      RESET=true
      shift
      ;;
    --cli-version)
      CLI_VERSION="$2"
      shift 2
      ;;
    --prethink-version)
      PRETHINK_VERSION="$2"
      shift 2
      ;;
    --moderne-prethink-version)
      MODERNE_PRETHINK_VERSION="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--skip-prethink] [--agent <name>] [--clean] [--reset]"
      echo "               [--cli-version <ver>] [--prethink-version <ver>]"
      echo "               [--moderne-prethink-version <ver>]"
      echo "  --skip-prethink       Skip running the refresh-prethink step"
      echo "  --skip-custom-recipe  Skip running the custom recipe against ecommerce example"
      echo "  --agent <name>        Target agent: claude (default), copilot, cursor, windsurf"
      echo "  --clean               Remove cloned repos and .moderne artifacts, then exit"
      echo "  --reset               Clean and re-initialize"
      echo "  --cli-version <ver>   Moderne CLI version (default: 4.0.6)"
      echo "  --prethink-version <ver>  org.openrewrite.recipe:rewrite-prethink version (default: 0.3.5)"
      echo "  --moderne-prethink-version <ver>  io.moderne.recipe:rewrite-prethink version (default: 0.4.0)"
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
  rm -rf "$WITH_DIR" "$WITHOUT_DIR" "$SCRIPT_DIR/.moderne"
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
# CLI 4.0.7+ has a bug where ExportContext can't read data tables (rewrite-core PR #7256 fixes this).
# Once a CLI ships with that fix, use --cli-version LATEST --prethink-version LATEST.
echo "==> Setting CLI to $CLI_VERSION, installing rewrite-prethink $PRETHINK_VERSION and io.moderne prethink $MODERNE_PRETHINK_VERSION..."
mkdir -p "$HOME/.moderne/cli/dist"
echo "version=$CLI_VERSION" > "$HOME/.moderne/cli/dist/moderne-wrapper.properties"
# Force LST v2 — CLI 4.0.6 has a bug that persists v3 in ~/.moderne/cli/moderne.yml
mod config features lst --version=2
mod config recipes jar install "org.openrewrite.recipe:rewrite-prethink:$PRETHINK_VERSION" "io.moderne.recipe:rewrite-prethink:$MODERNE_PRETHINK_VERSION"

# Sync repos into both directories
echo "==> Syncing repos into no-prethink/..."
mkdir -p "$WITHOUT_DIR"
mod git sync csv "$WITHOUT_DIR" "$REPOS_CSV" --with-sources --yes

echo "==> Syncing repos into with-prethink/..."
mkdir -p "$WITH_DIR"
mod git sync csv "$WITH_DIR" "$REPOS_CSV" --with-sources --yes

# Install custom recipe YAML
echo "==> Installing custom recipe YAML..."
mod config recipes yaml install "$WITH_DIR/bryanfriedman/prethink-ecommerce-example/rewrite.yml"

# Create symlinks to session-tokens.sh in each repo directory
echo "==> Creating session-tokens.sh symlinks..."
for dir in \
  "$WITH_DIR/nashtech-garage/yas" \
  "$WITHOUT_DIR/nashtech-garage/yas" \
  "$WITH_DIR/bryanfriedman/prethink-ecommerce-example" \
  "$WITHOUT_DIR/bryanfriedman/prethink-ecommerce-example"; do
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
  CUSTOM_APP="$WITH_DIR/bryanfriedman/prethink-ecommerce-example"

  # Run standard Prethink against yas
  echo "==> Running Prethink refresh against yas..."
  "$SCRIPT_DIR/refresh-prethink.sh" "$WITH_DIR/nashtech-garage/yas" "$AGENT"

  # Run standard Prethink against ecommerce example, or custom recipe if not skipped
  if [ "$SKIP_CUSTOM_RECIPE" = false ]; then
    echo "==> Running custom Prethink recipe against ecommerce example..."
    mod build "$CUSTOM_APP"
    mod run "$CUSTOM_APP" --recipe com.example.prethink.CustomPrethink -PtargetConfigFile="$TARGET_CONFIG"
    mod git apply "$CUSTOM_APP" --last-recipe-run
  else
    echo "==> Running standard Prethink against ecommerce example..."
    "$SCRIPT_DIR/refresh-prethink.sh" "$CUSTOM_APP" "$AGENT"
  fi
else
  echo "==> Skipping Prethink refresh (--skip-prethink)"
fi

echo ""
echo "==> Init complete."
