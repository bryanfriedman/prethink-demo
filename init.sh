#!/usr/bin/env bash
#
# Initializes the demo environment by cloning repositories from repos.csv
# into both with-prethink/ and no-prethink/ directories, then generates
# Prethink context in with-prethink/.
#
# Usage: ./init.sh [--skip-prethink] [--agent <name>] [--reset]
#                  [--cli-version <ver>] [--prethink-version <ver>]
#   --skip-prethink       Skip running the refresh-prethink step
#   --agent <name>        Target agent: claude (default), copilot, cursor, windsurf
#   --reset               Remove cloned repos and .moderne artifacts, then exit
#   --cli-version <ver>   Moderne CLI version (default: 4.0.6)
#   --prethink-version <ver>  rewrite-prethink version (default: 0.3.5)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPOS_CSV="$SCRIPT_DIR/repos.csv"
WITH_DIR="$SCRIPT_DIR/with-prethink"
WITHOUT_DIR="$SCRIPT_DIR/no-prethink"
SKIP_PRETHINK=false
AGENT="claude"
RESET=false
CLI_VERSION="4.0.6"
PRETHINK_VERSION="0.3.5"

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
    --reset)
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
    -h|--help)
      echo "Usage: $0 [--skip-prethink] [--agent <name>] [--reset]"
      echo "               [--cli-version <ver>] [--prethink-version <ver>]"
      echo "  --skip-prethink       Skip running the refresh-prethink step"
      echo "  --agent <name>        Target agent: claude (default), copilot, cursor, windsurf"
      echo "  --reset               Remove cloned repos and .moderne artifacts, then exit"
      echo "  --cli-version <ver>   Moderne CLI version (default: 4.0.6)"
      echo "  --prethink-version <ver>  rewrite-prethink version (default: 0.3.5)"
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

# Reset mode
if [ "$RESET" = true ]; then
  echo "==> Resetting demo directories..."
  clean_dir "$WITH_DIR"
  clean_dir "$WITHOUT_DIR"
  # Remove custom-app git repo, .moderne artifacts, and fake remote
  rm -rf "$SCRIPT_DIR/custom-app/.git" "$SCRIPT_DIR/custom-app/.moderne" "$SCRIPT_DIR/custom-app/target"
  rm -rf /tmp/fake-remotes/custom-app.git /tmp/fake-remotes/custom-app-gitdir
  echo "    Cleaned $SCRIPT_DIR/custom-app"
  rm -rf "$SCRIPT_DIR/.moderne"
  echo "==> Reset complete."
  exit 0
fi

if [ ! -f "$REPOS_CSV" ]; then
  echo "Error: repos.csv not found at $REPOS_CSV"
  exit 1
fi

# Read repos.csv (skip header line)
tail -n +2 "$REPOS_CSV" | while IFS=, read -r origin path cloneUrl branch _rest; do
  # Skip empty lines
  [ -z "$path" ] && continue

  echo "==> Processing $path (branch: $branch)"

  # Clone into no-prethink/
  dest_without="$WITHOUT_DIR/$path"
  if [ -d "$dest_without" ]; then
    echo "    Removing existing $dest_without"
    rm -rf "$dest_without"
  fi
  echo "    Cloning into no-prethink/$path..."
  mkdir -p "$(dirname "$dest_without")"
  git clone --branch "$branch" "$cloneUrl" "$dest_without"

  # Clone into with-prethink/
  dest_with="$WITH_DIR/$path"
  if [ -d "$dest_with" ]; then
    echo "    Removing existing $dest_with"
    rm -rf "$dest_with"
  fi
  echo "    Cloning into with-prethink/$path..."
  mkdir -p "$(dirname "$dest_with")"
  git clone --branch "$branch" "$cloneUrl" "$dest_with"

  echo ""
done

# Pin CLI and recipe versions for compatibility
# CLI 4.0.7+ has a bug where ExportContext can't read data tables (rewrite-core PR #7256 fixes this).
# Once a CLI ships with that fix, use --cli-version LATEST --prethink-version LATEST.
echo "==> Setting CLI to $CLI_VERSION and installing rewrite-prethink $PRETHINK_VERSION..."
echo "version=$CLI_VERSION" > "$HOME/.moderne/cli/dist/moderne-wrapper.properties"
mod config recipes jar install "org.openrewrite.recipe:rewrite-prethink:$PRETHINK_VERSION"

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
