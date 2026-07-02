import { useState, useRef, useCallback, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface HoverInfoCardProps<T> {
  title: string
  width?: number
  loadData: (signal: AbortSignal) => Promise<T>
  render: (data: T) => ReactNode
  children: ReactNode
}

// F85 — обобщённая hover-обёртка: ленивая загрузка данных при наведении,
// портальный рендер и позиционирование (по образцу StatusHistoryTooltip).
export function HoverInfoCard<T>({ title, width = 300, loadData, render, children }: HoverInfoCardProps<T>) {
  const [show, setShow] = useState(false)
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null)

  const triggerRef = useRef<HTMLSpanElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const fetchData = useCallback(async () => {
    if (data) return
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    setError(false)
    try {
      const result = await loadData(controller.signal)
      if (!controller.signal.aborted) setData(result)
    } catch {
      if (!controller.signal.aborted) setError(true)
    } finally {
      setLoading(false)
    }
  }, [data, loadData])

  const handleMouseEnter = () => {
    setShow(true)
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      let left = rect.left + rect.width / 2 - width / 2
      if (left + width > window.innerWidth - 8) left = window.innerWidth - width - 8
      if (left < 8) left = 8
      const spaceBelow = window.innerHeight - rect.bottom
      const top = spaceBelow >= 180 ? rect.bottom + 6 : Math.max(8, rect.top - 6 - 200)
      setPos({ top, left })
    }
    if (!data && !loading) {
      debounceRef.current = setTimeout(fetchData, 300)
    }
  }

  const handleMouseLeave = () => {
    setShow(false)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    abortRef.current?.abort()
    setLoading(false)
  }

  return (
    <span
      ref={triggerRef}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {children}
      {show && pos && createPortal(
        <div
          style={{
            position: 'fixed',
            top: pos.top,
            left: pos.left,
            width,
            zIndex: 10000,
            background: '#fff',
            border: '1px solid #dfe1e6',
            borderRadius: 8,
            boxShadow: '0 6px 20px rgba(9,30,66,0.25)',
            padding: 12,
            fontSize: 12,
            pointerEvents: 'none',
          }}
        >
          <div style={{ fontWeight: 600, color: '#172b4d', marginBottom: 8 }}>{title}</div>
          {loading && <div style={{ color: '#6b778c' }}>Загрузка…</div>}
          {error && <div style={{ color: '#de350b' }}>Не удалось загрузить</div>}
          {data && !loading && render(data)}
        </div>,
        document.body
      )}
    </span>
  )
}
