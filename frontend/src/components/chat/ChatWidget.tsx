import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { useLocation } from 'react-router-dom'
import { sendChatMessage, clearChatSession, getChatStatus, ChatMessage, ChatSseEvent } from '../../api/chat'
import './ChatWidget.css'

function generateSessionId(): string {
  return 'chat-' + Math.random().toString(36).substring(2, 10)
}

const PAGE_NAMES: Record<string, string> = {
  '/': 'Board',
  '/timeline': 'Timeline (Gantt)',
  '/metrics': 'Team Metrics',
  '/data-quality': 'Data Quality',
  '/bug-metrics': 'Bug Metrics',
  '/projects': 'Projects',
  '/teams': 'Teams',
  '/poker': 'Planning Poker',
  '/settings': 'Settings',
  '/workflow': 'Workflow Config',
}

function getPageName(pathname: string): string {
  // Exact match first
  if (PAGE_NAMES[pathname]) return PAGE_NAMES[pathname]

  // Pattern matching for dynamic routes
  if (/^\/poker\/room\//.test(pathname)) return 'Poker Room'
  if (/^\/teams\/\d+\/member\//.test(pathname)) return 'Member Profile'
  if (/^\/teams\/\d+\/competency/.test(pathname)) return 'Team Competency'
  if (/^\/teams\/\d+/.test(pathname)) return 'Team Members'

  return pathname
}

function renderMarkdown(text: string) {
  const lines = text.split('\n')
  const elements: React.ReactNode[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    // Code block: ```
    if (line.trim().startsWith('```')) {
      const codeLines: string[] = []
      i++ // skip opening ```
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(lines[i])
        i++
      }
      i++ // skip closing ```
      elements.push(
        <pre key={`code-${i}`} style={{
          background: '#1e2a3a',
          color: '#e6e8eb',
          padding: '8px 10px',
          borderRadius: 6,
          fontSize: 11,
          lineHeight: 1.5,
          overflowX: 'auto',
          margin: '4px 0',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word'
        }}>
          {codeLines.join('\n')}
        </pre>
      )
      continue
    }

    // Heading: # ## ###
    const headingMatch = line.match(/^(#{1,3})\s+(.*)/)
    if (headingMatch) {
      const level = headingMatch[1].length
      const size = level === 1 ? 15 : level === 2 ? 14 : 13
      elements.push(
        <div key={i} style={{ fontWeight: 700, fontSize: size, marginTop: 6, marginBottom: 2 }}>
          {formatInline(headingMatch[2])}
        </div>
      )
      i++
      continue
    }

    // List items: "- " or "* " or "1. " or "  - " (nested)
    const listMatch = line.match(/^(\s*)(?:[*-]|\d+\.)\s+(.*)/)
    if (listMatch) {
      const indent = listMatch[1].length
      elements.push(
        <div key={i} style={{ paddingLeft: 8 + indent * 8, display: 'flex', gap: 4 }}>
          <span>•</span>
          <span>{formatInline(listMatch[2])}</span>
        </div>
      )
      i++
      continue
    }

    // Empty line
    if (line.trim() === '') {
      elements.push(<div key={i} style={{ height: 4 }} />)
      i++
      continue
    }

    // Regular text
    elements.push(<div key={i}>{formatInline(line)}</div>)
    i++
  }

  return <>{elements}</>
}

function formatInline(text: string): React.ReactNode {
  const parts: React.ReactNode[] = []
  let remaining = text
  let key = 0

  while (remaining.length > 0) {
    // Inline code: `code`
    const codeMatch = remaining.match(/^(.*?)`([^`]+)`(.*)/)
    if (codeMatch) {
      if (codeMatch[1]) parts.push(formatBoldItalic(codeMatch[1], key++))
      parts.push(
        <code key={`c${key++}`} style={{
          background: '#f0f1f3',
          padding: '1px 4px',
          borderRadius: 3,
          fontSize: '0.9em',
          color: '#d63384'
        }}>
          {codeMatch[2]}
        </code>
      )
      remaining = codeMatch[3]
      continue
    }
    // No inline code left — handle bold/italic
    parts.push(formatBoldItalic(remaining, key++))
    break
  }

  return parts.length === 1 ? parts[0] : <>{parts}</>
}

function formatBoldItalic(text: string, baseKey: number): React.ReactNode {
  const parts: React.ReactNode[] = []
  let remaining = text
  let key = baseKey * 100

  while (remaining.length > 0) {
    // **bold**
    const boldMatch = remaining.match(/^(.*?)\*\*(.+?)\*\*(.*)/)
    if (boldMatch) {
      if (boldMatch[1]) parts.push(boldMatch[1])
      parts.push(<strong key={key++}>{boldMatch[2]}</strong>)
      remaining = boldMatch[3]
      continue
    }
    parts.push(remaining)
    break
  }

  return parts.length === 1 ? parts[0] : <>{parts}</>
}

export function ChatWidget() {
  const location = useLocation()
  const [isOpen, setIsOpen] = useState(false)
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [toolCallInProgress, setToolCallInProgress] = useState<string | null>(null)
  const [sessionId] = useState(generateSessionId)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  const currentPageInfo = useMemo(() => {
    const pageName = getPageName(location.pathname)
    const params = new URLSearchParams(location.search)
    const queryParts: string[] = []
    params.forEach((value, key) => queryParts.push(`${key}=${value}`))
    return queryParts.length > 0 ? `${pageName} (${queryParts.join(', ')})` : pageName
  }, [location.pathname, location.search])

  useEffect(() => {
    getChatStatus()
      .then(s => setEnabled(s.enabled))
      .catch(() => setEnabled(false))
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, toolCallInProgress])

  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus()
    }
  }, [isOpen])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false)
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen])

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || isLoading) return

    const userMsg: ChatMessage = { role: 'user', content: text, timestamp: Date.now() }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setIsLoading(true)
    setToolCallInProgress(null)

    let assistantText = ''
    const toolCalls: string[] = []

    abortRef.current = sendChatMessage(
      text,
      sessionId,
      currentPageInfo,
      (event: ChatSseEvent) => {
        switch (event.type) {
          case 'text':
            assistantText += event.content || ''
            setMessages(prev => {
              const last = prev[prev.length - 1]
              if (last?.role === 'assistant' && last.timestamp === -1) {
                return [...prev.slice(0, -1), { ...last, content: assistantText }]
              }
              return [...prev, { role: 'assistant', content: assistantText, timestamp: -1, toolCalls: toolCalls.length > 0 ? [...toolCalls] : undefined }]
            })
            setToolCallInProgress(null)
            break
          case 'tool_call':
            toolCalls.push(event.content || '')
            setToolCallInProgress(event.content)
            break
          case 'done':
            setMessages(prev => {
              const last = prev[prev.length - 1]
              if (last?.role === 'assistant' && last.timestamp === -1) {
                return [...prev.slice(0, -1), { ...last, timestamp: Date.now(), toolCalls: toolCalls.length > 0 ? [...toolCalls] : undefined }]
              }
              return prev
            })
            setIsLoading(false)
            setToolCallInProgress(null)
            break
          case 'error':
            setMessages(prev => [...prev, { role: 'assistant', content: event.content || 'An error occurred', timestamp: Date.now() }])
            setIsLoading(false)
            setToolCallInProgress(null)
            break
        }
      },
      (error: string) => {
        setMessages(prev => [...prev, { role: 'assistant', content: `Connection error: ${error}`, timestamp: Date.now() }])
        setIsLoading(false)
        setToolCallInProgress(null)
      }
    )
  }, [input, isLoading, sessionId, currentPageInfo])

  const handleClear = useCallback(() => {
    setMessages([])
    clearChatSession(sessionId)
  }, [sessionId])

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  if (enabled === null || enabled === false) return null

  if (!isOpen) {
    return (
      <button className="chat-bubble" onClick={() => setIsOpen(true)} title="AI Assistant">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      </button>
    )
  }

  return (
    <div className="chat-panel">
      <div className="chat-header">
        <span className="chat-header-title">AI Assistant</span>
        <div className="chat-header-actions">
          <button className="chat-header-btn" onClick={handleClear} title="Clear history">
            Clear
          </button>
          <button className="chat-header-btn" onClick={() => setIsOpen(false)} title="Close">
            ✕
          </button>
        </div>
      </div>

      <div className="chat-messages">
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#6b778c', fontSize: 13, padding: '40px 20px' }}>
            Ask me about tasks, metrics, navigation, or team data.
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`chat-message chat-message-${msg.role}`}>
            {msg.role === 'assistant' ? renderMarkdown(msg.content) : msg.content}
          </div>
        ))}

        {toolCallInProgress && (
          <div className="chat-tool-indicator">
            <div className="chat-tool-spinner" />
            Fetching: {toolCallInProgress}
          </div>
        )}

        {isLoading && !toolCallInProgress && messages[messages.length - 1]?.role === 'user' && (
          <div className="chat-typing">
            <div className="chat-typing-dot" />
            <div className="chat-typing-dot" />
            <div className="chat-typing-dot" />
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="chat-input-area">
        <textarea
          ref={inputRef}
          className="chat-input"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyPress}
          placeholder="Ask a question..."
          rows={1}
          disabled={isLoading}
        />
        <button className="chat-send-btn" onClick={handleSend} disabled={isLoading || !input.trim()}>
          Send
        </button>
      </div>
    </div>
  )
}
