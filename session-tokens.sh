#!/usr/bin/env bash
#
# Reports token usage for a coding agent session.
#
# Usage: ./session-tokens.sh <session-id> [claude|copilot]
#   session-id  The session ID to look up
#   agent       Which agent's logs to parse (default: claude)

set -euo pipefail

SESSION_ID="${1:-}"
AGENT="${2:-claude}"

if [ -z "$SESSION_ID" ]; then
  echo "Usage: $0 <session-id> [claude|copilot]"
  exit 1
fi

# ─── Claude ───────────────────────────────────────────────────────────────────

report_claude() {
  local MAIN_LOG
  MAIN_LOG=$(find ~/.claude/projects -maxdepth 2 -name "$SESSION_ID.jsonl" 2>/dev/null | head -1)

  if [ -z "$MAIN_LOG" ]; then
    echo "Session not found: $SESSION_ID"
    exit 1
  fi

  local PROJECT_DIR
  PROJECT_DIR=$(dirname "$MAIN_LOG")
  local SUBAGENT_DIR="$PROJECT_DIR/$SESSION_ID/subagents"

  sum_tokens() {
    python3 -c "
import json, sys

totals = {'input': 0, 'output': 0, 'cache_create': 0, 'cache_read': 0}
entries = 0

for filepath in sys.argv[1:]:
    with open(filepath) as f:
        for line in f:
            try:
                obj = json.loads(line)
                usage = obj.get('usage') or obj.get('message', {}).get('usage')
                if usage:
                    totals['input'] += usage.get('input_tokens', 0)
                    totals['output'] += usage.get('output_tokens', 0)
                    totals['cache_create'] += usage.get('cache_creation_input_tokens', 0)
                    totals['cache_read'] += usage.get('cache_read_input_tokens', 0)
                    entries += 1
            except json.JSONDecodeError:
                pass

total = totals['input'] + totals['output'] + totals['cache_create'] + totals['cache_read']
print(f\"  Uses:          {entries}\")
print(f\"  Input:         {totals['input']:,}\")
print(f\"  Output:        {totals['output']:,}\")
print(f\"  Cache create:  {totals['cache_create']:,}\")
print(f\"  Cache read:    {totals['cache_read']:,}\")
print(f\"  Total tokens:  {total:,}\")
" "$@"
  }

  echo "=== Main session ==="
  if [ -f "$MAIN_LOG" ]; then
    sum_tokens "$MAIN_LOG"
  else
    echo "  Not found: $MAIN_LOG"
  fi

  echo ""
  echo "=== Subagents ==="
  if [ -d "$SUBAGENT_DIR" ]; then
    for f in "$SUBAGENT_DIR"/*.jsonl; do
      echo "  --- $(basename "$f") ---"
      sum_tokens "$f"
      echo ""
    done
  else
    echo "  No subagents found"
  fi

  echo ""
  echo "=== Combined total ==="
  local ALL_FILES="$MAIN_LOG"
  if [ -d "$SUBAGENT_DIR" ]; then
    ALL_FILES="$ALL_FILES $SUBAGENT_DIR/*.jsonl"
  fi
  sum_tokens $ALL_FILES
}

# ─── Copilot ──────────────────────────────────────────────────────────────────

report_copilot() {
  local BASE_DIR="$HOME/.copilot/session-state"
  local EVENTS="$BASE_DIR/$SESSION_ID/events.jsonl"

  if [ ! -f "$EVENTS" ]; then
    echo "Error: $EVENTS not found"
    exit 1
  fi

  local selected_model
  selected_model=$(grep '"session.start"' "$EVENTS" | head -1 | sed -n 's/.*"selectedModel":"\([^"]*\)".*/\1/p')
  echo "Session:  $SESSION_ID"

  python3 -c "
import json, sys

events_file = sys.argv[1]
primary_model = sys.argv[2] if len(sys.argv) > 2 else ''

shutdown = None
with open(events_file) as f:
    for line in f:
        try:
            obj = json.loads(line)
            if obj.get('type') == 'session.shutdown':
                shutdown = obj
                break
        except json.JSONDecodeError:
            pass

if not shutdown:
    print('  (no shutdown event found - session may still be running)')
    sys.exit(0)

data = shutdown.get('data', shutdown)
metrics = data.get('modelMetrics', {})

if not metrics:
    print('  (no modelMetrics in shutdown event)')
    sys.exit(0)

agent_model = primary_model
if not agent_model or agent_model == 'unknown':
    agent_model = next(iter(metrics), '')

groups = {}  # 'Agent' or 'Subagents' -> {reqs, input, output, cread, cwrite}
for model, info in metrics.items():
    usage = info.get('usage', {})
    reqs = info.get('requests', {}).get('count', 0)
    input_t = usage.get('inputTokens', 0)
    output_t = usage.get('outputTokens', 0)
    cread = usage.get('cacheReadTokens', 0)
    cwrite = usage.get('cacheWriteTokens', 0)

    bucket = 'Agent' if model == agent_model else 'Subagents'
    g = groups.setdefault(bucket, {'reqs': 0, 'input': 0, 'output': 0, 'cread': 0, 'cwrite': 0})
    g['reqs'] += reqs
    g['input'] += input_t
    g['output'] += output_t
    g['cread'] += cread
    g['cwrite'] += cwrite

for label in ['Agent', 'Subagents']:
    if label not in groups:
        continue
    g = groups[label]
    total = g['input'] + g['output'] + g['cread'] + g['cwrite']
    print(f\"  {label}\")
    print(f\"    Requests:           {g['reqs']:,}\")
    print(f\"    Input tokens:       {g['input']:,}\")
    print(f\"    Output tokens:      {g['output']:,}\")
    print(f\"    Cache read tokens:  {g['cread']:,}\")
    print(f\"    Cache write tokens: {g['cwrite']:,}\")
    print(f\"    TOTAL tokens:       {total:,}\")
    print()

if len(groups) > 1:
    totals = {k: sum(g[k] for g in groups.values()) for k in ['reqs','input','output','cread','cwrite']}
    grand = totals['input'] + totals['output'] + totals['cread'] + totals['cwrite']
    print('  Total (agent + subagent)')
    print(f\"    Requests:           {totals['reqs']:,}\")
    print(f\"    Input tokens:       {totals['input']:,}\")
    print(f\"    Output tokens:      {totals['output']:,}\")
    print(f\"    Cache read tokens:  {totals['cread']:,}\")
    print(f\"    Cache write tokens: {totals['cwrite']:,}\")
    print(f\"    TOTAL tokens:       {grand:,}\")
" "$EVENTS" "$selected_model"
}

# ─── Dispatch ─────────────────────────────────────────────────────────────────

case "$AGENT" in
  claude)
    report_claude
    ;;
  copilot)
    report_copilot
    ;;
  *)
    echo "Unknown agent: $AGENT (expected 'claude' or 'copilot')"
    exit 1
    ;;
esac
