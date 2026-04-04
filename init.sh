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
      echo "  --skip-prethink       Skip running the refresh-prethink step"
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

# Clean a directory: remove everything except .gitkeep and refresh-prethink.sh
clean_dir() {
  local dir="$1"
  if [ -d "$dir" ]; then
    find "$dir" -mindepth 1 -maxdepth 1 \
      ! -name '.gitkeep' \
      ! -name 'refresh-prethink.sh' \
      -exec rm -rf {} +
    echo "    Cleaned $dir"
  fi
}

# Clean mode (also used by --reset)
if [ "$CLEAN" = true ]; then
  echo "==> Cleaning demo directories..."
  clean_dir "$WITH_DIR"
  clean_dir "$WITHOUT_DIR"
  # Remove custom-app git repo, .moderne artifacts, and fake remote
  rm -rf "$SCRIPT_DIR/custom-app/.git" "$SCRIPT_DIR/custom-app/.moderne" "$SCRIPT_DIR/custom-app/target"
  rm -rf /tmp/fake-remotes/custom-app.git /tmp/fake-remotes/custom-app-gitdir
  echo "    Cleaned $SCRIPT_DIR/custom-app"
  rm -rf "$SCRIPT_DIR/.moderne"
  echo "==> Clean complete."
  # If --reset, continue with init; if --clean only, exit
  if [ "$RESET" = false ]; then
    exit 0
  fi
fi

if [ ! -f "$REPOS_CSV" ]; then
  echo "Error: repos.csv not found at $REPOS_CSV"
  exit 1
fi

# Sync repos using mod git sync so the CLI registers them in its organization
echo "==> Syncing repos into no-prethink/..."
mod git sync csv "$WITHOUT_DIR" "$REPOS_CSV" --with-sources --yes

echo "==> Syncing repos into with-prethink/..."
mod git sync csv "$WITH_DIR" "$REPOS_CSV" --with-sources --yes

# Pin CLI and recipe versions for compatibility
# CLI 4.0.7+ has a bug where ExportContext can't read data tables (rewrite-core PR #7256 fixes this).
# Once a CLI ships with that fix, use --cli-version LATEST --prethink-version LATEST.
echo "==> Setting CLI to $CLI_VERSION, installing rewrite-prethink $PRETHINK_VERSION and io.moderne prethink $MODERNE_PRETHINK_VERSION..."
echo "version=$CLI_VERSION" > "$HOME/.moderne/cli/dist/moderne-wrapper.properties"
# Force LST v2 — CLI 4.0.6 has a bug that persists v3 in ~/.moderne/cli/moderne.yml
mod config features lst --version=2
mod config recipes jar install "org.openrewrite.recipe:rewrite-prethink:$PRETHINK_VERSION"
mod config recipes jar install "io.moderne.recipe:rewrite-prethink:$MODERNE_PRETHINK_VERSION"

# Set up custom-app as a git repo with a fake remote so mod can build it
echo "==> Setting up custom-app with fake remote..."
"$SCRIPT_DIR/setup-fake-remote.sh" "$SCRIPT_DIR/custom-app"

# Install custom recipe YAML
echo "==> Installing custom recipe YAML..."
mod config recipes yaml install "$SCRIPT_DIR/custom-app/rewrite.yml"

# Create symlinks to session-tokens.sh
echo "==> Creating session-tokens.sh symlinks..."
ln -sf ../session-tokens.sh "$WITH_DIR/session-tokens.sh"
ln -sf ../session-tokens.sh "$WITHOUT_DIR/session-tokens.sh"

# Run prethink refresh
if [ "$SKIP_PRETHINK" = false ]; then
  echo "==> Running Prethink refresh in with-prethink/ (agent: $AGENT)..."
  "$WITH_DIR/refresh-prethink.sh" "$AGENT"
else
  echo "==> Skipping Prethink refresh (--skip-prethink)"
fi

echo ""
echo "==> Init complete."
