#!/usr/bin/env bash
set -euo pipefail

# Creates a local bare git repo and connects a project directory to it
# as a "fake" remote, so the directory appears to have a remote origin.
#
# Usage: ./setup-fake-remote.sh <project-dir> [bare-repo-dir]
#
# If bare-repo-dir is not specified, it defaults to /tmp/fake-remotes/<dirname>.git

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <project-dir> [bare-repo-dir]"
  echo ""
  echo "  project-dir   Directory to set up with a fake remote"
  echo "  bare-repo-dir Optional path for the bare repo (default: /tmp/fake-remotes/<dirname>.git)"
  exit 1
fi

PROJECT_DIR="$(cd "$1" && pwd)"
DIR_NAME="$(basename "$PROJECT_DIR")"
BARE_REPO="${2:-/tmp/fake-remotes/${DIR_NAME}.git}"

# Create the bare repository
echo "Creating bare repo at: $BARE_REPO"
mkdir -p "$BARE_REPO"
git init --bare "$BARE_REPO"

# Initialize the project dir if it's not already a git repo
if [[ ! -d "$PROJECT_DIR/.git" ]]; then
  echo "Initializing git repo in: $PROJECT_DIR"
  git -C "$PROJECT_DIR" init
fi

# Add the bare repo as origin (remove existing origin first if present)
if git -C "$PROJECT_DIR" remote get-url origin &>/dev/null; then
  echo "Removing existing 'origin' remote"
  git -C "$PROJECT_DIR" remote remove origin
fi

echo "Adding bare repo as 'origin' remote"
git -C "$PROJECT_DIR" remote add origin "$BARE_REPO"

# Create an initial commit if the repo has no commits yet
if ! git -C "$PROJECT_DIR" rev-parse HEAD &>/dev/null; then
  echo "Creating initial commit"
  git -C "$PROJECT_DIR" add -A
  git -C "$PROJECT_DIR" commit -m "Initial commit" --allow-empty
fi

# Push to the fake remote
echo "Pushing to fake remote"
git -C "$PROJECT_DIR" push -u origin "$(git -C "$PROJECT_DIR" branch --show-current)"

echo ""
echo "Done! '$PROJECT_DIR' now has a local remote at '$BARE_REPO'"
echo ""
echo "Verify with:"
echo "  git -C \"$PROJECT_DIR\" remote -v"
