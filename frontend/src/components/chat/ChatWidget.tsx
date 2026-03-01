import { useState, useRef, useEffect, useCallback } from 'react'
import { sendChatMessage, clearChatSession, getChatStatus, ChatMessage, ChatSseEvent } from '../../api/chat'
import './ChatWidget.css'

function generateSessionId(): string {
  return 'chat-' + Math.random().toString(36).substring(2, 10)
}

export function ChatWidget() {
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
  }, [input, isLoading, sessionId])

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
            {msg.content}
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
