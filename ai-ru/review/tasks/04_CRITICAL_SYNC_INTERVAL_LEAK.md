# TASK: Fix triggerSync interval leak in useBoardData

**Priority:** Critical
**Review ID:** C5
**File:** `frontend/src/hooks/useBoardData.ts:41-62`

## Проблема

`setInterval` для polling статуса синхронизации не отменяется при unmount компонента. Если пользователь уходит со страницы во время синка:
- Interval продолжает работать
- Попытки обновить state на unmounted компоненте
- Утечка памяти

## Рекомендация

```ts
// Использовать useRef для хранения interval ID
const pollRef = useRef<number | null>(null)

// Cleanup в useEffect
useEffect(() => {
  return () => {
    if (pollRef.current) clearInterval(pollRef.current)
  }
}, [])

// В triggerSync: pollRef.current = setInterval(...)
```

Или использовать AbortController для отмены fetch-запросов.
