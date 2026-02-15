#!/bin/bash
# Pre-commit hook: runs backend tests before git commit
# If tests fail, the commit is blocked (exit 2)

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# Only intercept git commit commands
if [[ "$COMMAND" =~ git\ commit ]]; then
  echo "Running backend tests before commit..." >&2

  cd "$CLAUDE_PROJECT_DIR/backend" || exit 0

  if ./gradlew test --no-daemon -q 2>&1 | tail -5 >&2; then
    echo "Tests passed." >&2
    exit 0
  else
    echo "Tests FAILED. Commit blocked." >&2
    exit 2
  fi
fi

exit 0
