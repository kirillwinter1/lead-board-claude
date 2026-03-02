#!/bin/bash
# Chat Bot Test Script
# Sends 15 test questions to the AI chat and collects responses

SESSION_COOKIE="LEAD_SESSION=c154f905-4351-47bb-a7f5-f9b01f1be434"
TENANT_HEADER="X-Tenant-Slug: test2"
BASE_URL="http://localhost:8080/api/chat/message"
OUTPUT_DIR="/Users/kirillreshetov/IdeaProjects/lead-board-claude/ai-ru/testing/chat_results"

mkdir -p "$OUTPUT_DIR"

send_question() {
    local num="$1"
    local page="$2"
    local question="$3"
    local session_id="chat-test-$(date +%s)-${num}"

    echo "=== Q${num}: ${question} (page: ${page:-none}) ==="

    local page_json="null"
    if [ -n "$page" ]; then
        page_json="\"${page}\""
    fi

    local response
    response=$(curl -s -N -X POST "$BASE_URL" \
        -H "Content-Type: application/json" \
        -H "Cookie: ${SESSION_COOKIE}" \
        -H "${TENANT_HEADER}" \
        -d "{\"message\":\"${question}\",\"sessionId\":\"${session_id}\",\"currentPage\":${page_json}}" \
        --max-time 60 2>&1)

    # Save raw response
    echo "$response" > "${OUTPUT_DIR}/q${num}_raw.txt"

    # Extract tool calls
    local tools=$(echo "$response" | grep 'event:tool_call' -A1 | grep 'data:' | sed 's/data://' | jq -r '.content // empty' 2>/dev/null)

    # Extract text content
    local text=$(echo "$response" | grep 'event:text' -A1 | grep 'data:' | sed 's/data://' | jq -r '.content // empty' 2>/dev/null)

    # Extract errors
    local error=$(echo "$response" | grep 'event:error' -A1 | grep 'data:' | sed 's/data://' | jq -r '.content // empty' 2>/dev/null)

    # Output
    echo "Tools: ${tools:-none}"
    echo "Text: ${text:0:200}"
    if [ -n "$error" ]; then
        echo "ERROR: ${error}"
    fi
    echo ""

    # Save structured result
    cat > "${OUTPUT_DIR}/q${num}_result.json" <<EOFJ
{
    "num": ${num},
    "question": "${question}",
    "currentPage": ${page_json},
    "tools": "${tools:-none}",
    "text": $(echo "$text" | jq -Rs .),
    "error": "${error}"
}
EOFJ

    # Rate limit: wait between requests
    sleep 2
}

echo "Starting Chat Bot Tests at $(date)"
echo "=================================="
echo ""

# Clear all test sessions
for i in $(seq 1 15); do
    curl -s -X DELETE "http://localhost:8080/api/chat/session/chat-test-${i}" \
        -H "Cookie: ${SESSION_COOKIE}" -H "${TENANT_HEADER}" > /dev/null 2>&1
done

# Test questions
send_question 1  "Board (Доска задач)"          "Что означает цвет карточки?"
send_question 2  "Board (Доска задач)"          "Как отсортировать задачи?"
send_question 3  "Timeline (Таймлайн Gantt)"    "Что означает зелёный цвет?"
send_question 4  "Timeline (Таймлайн Gantt)"    "Что означают полосатые бары?"
send_question 5  "Metrics (Метрики команды)"    "Что означает красный цвет?"
send_question 6  "Metrics (Метрики команды)"    "Что такое DSR?"
send_question 7  "Data Quality (Качество данных)" "Что означает жёлтый треугольник?"
send_question 8  "Bug Metrics (Метрики багов)"  "Что означает SLA нарушен?"
send_question 9  "Projects (Проекты)"           "Что означает RICE score?"
send_question 10 "Workflow Config"               "Как добавить новый тип задачи?"
send_question 11 ""                              "Привет, что ты умеешь?"
send_question 12 ""                              "Сколько задач в работе?"
send_question 13 "Board (Доска задач)"          "Почему задача серая?"
send_question 14 "Timeline (Таймлайн Gantt)"    "Как читать этот график?"
send_question 15 "Team Members"                  "Кто сейчас в отпуске?"

echo "=================================="
echo "Tests completed at $(date)"
echo "Results saved to ${OUTPUT_DIR}"
