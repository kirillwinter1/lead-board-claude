import { getTenantSlug } from '../utils/tenant'

export interface ChatSseEvent {
  type: 'text' | 'tool_call' | 'done' | 'error'
  content: string | null
  sessionId: string
}

function buildHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const slug = getTenantSlug()
  if (slug) {
    headers['X-Tenant-Slug'] = slug
  }
  return headers
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  toolCalls?: string[]
}

export function sendChatMessage(
  message: string,
  sessionId: string,
  currentPage: string | null,
  onEvent: (event: ChatSseEvent) => void,
  onError: (error: string) => void
): AbortController {
  const controller = new AbortController()

  fetch('/api/chat/message', {
    method: 'POST',
    headers: buildHeaders(),
    credentials: 'include',
    body: JSON.stringify({ message, sessionId, currentPage }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        onError(`HTTP ${response.status}`)
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        onError('No response body')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (!data) continue
            try {
              const event: ChatSseEvent = JSON.parse(data)
              onEvent(event)
            } catch {
              // Skip malformed data
            }
          }
        }
      }

      // Process remaining buffer
      if (buffer.startsWith('data:')) {
        const data = buffer.slice(5).trim()
        if (data) {
          try {
            const event: ChatSseEvent = JSON.parse(data)
            onEvent(event)
          } catch {
            // Skip
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message || 'Connection failed')
      }
    })

  return controller
}

export async function clearChatSession(sessionId: string): Promise<void> {
  await fetch(`/api/chat/session/${sessionId}`, { method: 'DELETE', headers: buildHeaders(), credentials: 'include' })
}

export async function getChatStatus(): Promise<{ enabled: boolean }> {
  const res = await fetch('/api/chat/status')
  return res.json()
}
