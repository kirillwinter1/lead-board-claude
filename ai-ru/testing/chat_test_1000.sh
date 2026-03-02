#!/bin/bash
# Chat Bot Test — 1000 Questions
# Sends all questions from chat-questions-1000.md to the AI chat, collects and saves results.
# Supports resume: skips already-tested questions.

SESSION_COOKIE="LEAD_SESSION=c154f905-4351-47bb-a7f5-f9b01f1be434"
TENANT_HEADER="X-Tenant-Slug: test2"
BASE_URL="http://localhost:8080/api/chat/message"
QUESTIONS_FILE="/Users/kirillreshetov/IdeaProjects/lead-board-claude/ai-ru/chat-questions-1000.md"
OUTPUT_DIR="/Users/kirillreshetov/IdeaProjects/lead-board-claude/ai-ru/testing/chat_results"
RESULTS_CSV="${OUTPUT_DIR}/results.csv"
PROGRESS_FILE="${OUTPUT_DIR}/progress.txt"
LOG_FILE="${OUTPUT_DIR}/test_log.txt"

mkdir -p "$OUTPUT_DIR"

# Initialize CSV header if not exists
if [ ! -f "$RESULTS_CSV" ]; then
    echo "num|question|currentPage|tools|response_length|first_200_chars|has_error" > "$RESULTS_CSV"
fi

log() {
    echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

# Get the page context based on question number
get_page_context() {
    local num=$1
    if   [ "$num" -ge 1   ] && [ "$num" -le 30  ]; then echo ""
    elif [ "$num" -ge 31  ] && [ "$num" -le 70  ]; then echo "Board (Доска задач)"
    elif [ "$num" -ge 71  ] && [ "$num" -le 100 ]; then echo "Teams (Команды)"
    elif [ "$num" -ge 101 ] && [ "$num" -le 130 ]; then echo "Metrics (Метрики команды)"
    elif [ "$num" -ge 131 ] && [ "$num" -le 155 ]; then echo "Timeline (Таймлайн Gantt)"
    elif [ "$num" -ge 156 ] && [ "$num" -le 180 ]; then echo "Data Quality (Качество данных)"
    elif [ "$num" -ge 181 ] && [ "$num" -le 205 ]; then echo "Bug Metrics (Метрики багов)"
    elif [ "$num" -ge 206 ] && [ "$num" -le 225 ]; then echo "Projects (Проекты)"
    elif [ "$num" -ge 226 ] && [ "$num" -le 250 ]; then echo ""
    elif [ "$num" -ge 251 ] && [ "$num" -le 300 ]; then echo "Board (Доска задач)"
    elif [ "$num" -ge 301 ] && [ "$num" -le 360 ]; then echo "Metrics (Метрики команды)"
    elif [ "$num" -ge 361 ] && [ "$num" -le 410 ]; then echo "Timeline (Таймлайн Gantt)"
    elif [ "$num" -ge 411 ] && [ "$num" -le 440 ]; then echo "Data Quality (Качество данных)"
    elif [ "$num" -ge 441 ] && [ "$num" -le 465 ]; then echo "Projects (Проекты)"
    elif [ "$num" -ge 466 ] && [ "$num" -le 500 ]; then echo "Workflow Config"
    elif [ "$num" -ge 501 ] && [ "$num" -le 540 ]; then echo "Team Members"
    elif [ "$num" -ge 541 ] && [ "$num" -le 575 ]; then echo "Team Members"
    elif [ "$num" -ge 576 ] && [ "$num" -le 610 ]; then echo "Team Members"
    elif [ "$num" -ge 611 ] && [ "$num" -le 645 ]; then echo "Planning Poker"
    elif [ "$num" -ge 646 ] && [ "$num" -le 680 ]; then echo "Board (Доска задач)"
    elif [ "$num" -ge 681 ] && [ "$num" -le 720 ]; then echo "Metrics (Метрики команды)"
    elif [ "$num" -ge 721 ] && [ "$num" -le 750 ]; then echo "Simulation"
    elif [ "$num" -ge 751 ] && [ "$num" -le 800 ]; then echo "Projects (Проекты)"
    elif [ "$num" -ge 801 ] && [ "$num" -le 850 ]; then echo "Metrics (Метрики команды)"
    elif [ "$num" -ge 851 ] && [ "$num" -le 900 ]; then echo "Timeline (Таймлайн Gantt)"
    elif [ "$num" -ge 901 ] && [ "$num" -le 935 ]; then echo "Bug Metrics (Метрики багов)"
    elif [ "$num" -ge 936 ] && [ "$num" -le 970 ]; then echo "Settings (Настройки)"
    elif [ "$num" -ge 971 ] && [ "$num" -le 1000 ]; then echo ""
    else echo ""
    fi
}

# Send a single question to chat API
send_question() {
    local num="$1"
    local question="$2"
    local page="$3"
    local session_id="q${num}-$(date +%s)"

    local page_json="null"
    if [ -n "$page" ]; then
        page_json="\"${page}\""
    fi

    # Escape double quotes in question for JSON
    local escaped_question
    escaped_question=$(printf '%s' "$question" | sed 's/"/\\"/g')

    local raw_file="${OUTPUT_DIR}/q${num}_raw.txt"

    # Make the API call
    local response
    response=$(curl -s -N -X POST "$BASE_URL" \
        -H "Content-Type: application/json" \
        -H "Cookie: ${SESSION_COOKIE}" \
        -H "${TENANT_HEADER}" \
        -d "{\"message\":\"${escaped_question}\",\"sessionId\":\"${session_id}\",\"currentPage\":${page_json}}" \
        --max-time 60 2>&1) || true

    # Save raw response
    printf '%s' "$response" > "$raw_file"

    # Extract tool calls
    local tools
    tools=$(printf '%s' "$response" | grep 'event:tool_call' -A1 | grep 'data:' | sed 's/data://' | jq -r '.content // empty' 2>/dev/null | tr '\n' ',' | sed 's/,$//')
    [ -z "$tools" ] && tools="none"

    # Extract text content
    local text
    text=$(printf '%s' "$response" | grep 'event:text' -A1 | grep 'data:' | sed 's/data://' | jq -r '.content // empty' 2>/dev/null)

    # Check for errors
    local has_error="no"
    if printf '%s' "$response" | grep -q 'event:error'; then
        has_error="yes"
    fi
    if printf '%s' "$response" | grep -q '"error":'; then
        has_error="yes"
    fi
    # Also check for empty response
    if [ -z "$text" ] && [ "$tools" = "none" ]; then
        has_error="yes"
    fi

    local resp_len=${#text}
    local first200="${text:0:200}"
    # Escape pipe and newline chars for CSV
    first200=$(printf '%s' "$first200" | tr '|' '/' | tr '\n' ' ')

    # Append to CSV
    echo "${num}|${escaped_question}|${page:-none}|${tools}|${resp_len}|${first200}|${has_error}" >> "$RESULTS_CSV"

    # Update progress
    echo "$num" > "$PROGRESS_FILE"

    # Log
    local status_icon="✓"
    [ "$has_error" = "yes" ] && status_icon="✗"
    log "Q${num} ${status_icon} [${tools}] (${resp_len} chars) ${question:0:60}"
}

# Get last completed question number for resume support
get_last_completed() {
    if [ -f "$PROGRESS_FILE" ]; then
        cat "$PROGRESS_FILE"
    else
        echo "0"
    fi
}

# ---- MAIN ----

log "========================================"
log "Chat Bot Test — 1000 Questions"
log "========================================"

# Check backend is running
if ! curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    log "ERROR: Backend is not running on :8080"
    exit 1
fi

# Check chat is enabled
chat_status=$(curl -s http://localhost:8080/api/chat/status | jq -r '.enabled' 2>/dev/null)
if [ "$chat_status" != "true" ]; then
    log "ERROR: Chat is not enabled"
    exit 1
fi

log "Backend: OK | Chat: enabled"

# Count total questions
TOTAL=$(grep -cE '^\d+\.' "$QUESTIONS_FILE")
log "Total questions: $TOTAL"

LAST_COMPLETED=$(get_last_completed)
log "Resuming from question: $((LAST_COMPLETED + 1))"

STARTED=$(date +%s)
ERRORS=0
TOOL_CALLS=0
CURRENT=0

# Read and process questions line by line
grep -E '^\d+\.' "$QUESTIONS_FILE" | while IFS= read -r line; do
    # Extract number and question text
    num=$(echo "$line" | sed 's/^\([0-9]*\)\..*/\1/')
    question=$(echo "$line" | sed 's/^[0-9]*\. //')
    CURRENT=$num

    # Skip already completed questions
    if [ "$num" -le "$LAST_COMPLETED" ]; then
        continue
    fi

    page=$(get_page_context "$num")
    send_question "$num" "$question" "$page"

    # Progress every 10 questions
    if [ $((num % 10)) -eq 0 ]; then
        elapsed=$(( $(date +%s) - STARTED ))
        if [ "$elapsed" -gt 0 ]; then
            completed=$((num - LAST_COMPLETED))
            rate=$(echo "scale=1; $completed / $elapsed * 60" | bc 2>/dev/null || echo "?")
            errors_so_far=$(grep -c '|yes$' "$RESULTS_CSV" 2>/dev/null || echo 0)
            tools_so_far=$(grep -v '|none|' "$RESULTS_CSV" | grep -v '^num' | wc -l | tr -d ' ')
            log "--- Progress: ${num}/${TOTAL} | Errors: ${errors_so_far} | Tools: ${tools_so_far} | Rate: ${rate} q/min ---"
        fi
    fi

    # Rate limiting: 2 seconds between requests
    sleep 2
done

ELAPSED=$(( $(date +%s) - STARTED ))
MINUTES=$(( ELAPSED / 60 ))

# Final stats
TOTAL_DONE=$(grep -v '^num' "$RESULTS_CSV" | wc -l | tr -d ' ')
ERRORS=$(grep -c '|yes$' "$RESULTS_CSV" 2>/dev/null || echo 0)
TOOL_CALLS=$(grep -v '|none|' "$RESULTS_CSV" | grep -v '^num' | wc -l | tr -d ' ')

log "========================================"
log "DONE! ${TOTAL_DONE} questions in ${MINUTES} minutes"
log "Errors: ${ERRORS}"
log "Tool calls: ${TOOL_CALLS}/${TOTAL_DONE}"
log "Results: ${RESULTS_CSV}"
log "========================================"

# Generate summary
TOOL_USAGE_RATE=$(echo "scale=1; $TOOL_CALLS * 100 / ($TOTAL_DONE + 1)" | bc 2>/dev/null || echo "?")
ERROR_RATE=$(echo "scale=1; $ERRORS * 100 / ($TOTAL_DONE + 1)" | bc 2>/dev/null || echo "?")

cat > "${OUTPUT_DIR}/SUMMARY.md" <<EOFSUM
# Chat Bot Test Summary — $(date '+%Y-%m-%d %H:%M')

- **Questions:** ${TOTAL_DONE}
- **Duration:** ${MINUTES} min
- **Errors:** ${ERRORS} (${ERROR_RATE}%)
- **Tool usage:** ${TOOL_CALLS}/${TOTAL_DONE} (${TOOL_USAGE_RATE}%)

## Tool distribution

\`\`\`
$(grep -v "^num" "$RESULTS_CSV" | cut -d'|' -f4 | sort | uniq -c | sort -rn)
\`\`\`

## Errors (first 30)

\`\`\`
$(grep '|yes$' "$RESULTS_CSV" | cut -d'|' -f1,2 | head -30)
\`\`\`

## Short responses (< 50 chars, first 30)

\`\`\`
$(awk -F'|' '$5 < 50 && $5 > 0 {print $1, $2, "(" $5 " chars)"}' "$RESULTS_CSV" | head -30)
\`\`\`

## Empty responses

\`\`\`
$(awk -F'|' '$5 == 0 {print $1, $2}' "$RESULTS_CSV" | head -30)
\`\`\`
EOFSUM

log "Summary saved to ${OUTPUT_DIR}/SUMMARY.md"
