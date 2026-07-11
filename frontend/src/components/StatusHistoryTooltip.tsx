import { useState, useRef, useCallback, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { getStatusHistory, formatDuration, type StatusHistory } from '../api/statusHistory'
import { StatusBadge } from './board/StatusBadge'
import { ERROR_TEXT, TEXT_PRIMARY, TEXT_MUTED, LINK_COLOR, SEPARATOR } from '../constants/colors'

interface StatusHistoryTooltipProps {
  issueKey: string
  children: ReactNode
}

const TOOLTIP_WIDTH = 260

// F81 — hover wrapper that lazily loads and shows an issue's status journey
// (time spent in each status, from the starting status to the current one).
export function StatusHistoryTooltip({ issueKey, children }: StatusHistoryTooltipProps) {
  const [show, setShow] = useState(false)
  const [history, setHistory] = useState<StatusHistory | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null)

  const triggerRef = useRef<HTMLSpanElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const fetchHistory = useCallback(async () => {
    if (history) return
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    setError(false)
    try {
      const data = await getStatusHistory(issueKey, controller.signal)
      if (!controller.signal.aborted) setHistory(data)
    } catch {
      if (!controller.signal.aborted) setError(true)
    } finally {
      // Always clear loading — otherwise a mid-flight abort leaves it stuck true
      // and a re-hover would show "Загрузка…" forever (handleMouseEnter gates on !loading).
      setLoading(false)
    }
  }, [issueKey, history])

  const handleMouseEnter = () => {
    setShow(true)
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      let left = rect.left + rect.width / 2 - TOOLTIP_WIDTH / 2
      if (left + TOOLTIP_WIDTH > window.innerWidth - 8) left = window.innerWidth - TOOLTIP_WIDTH - 8
      if (left < 8) left = 8
      const spaceBelow = window.innerHeight - rect.bottom
      const top = spaceBelow >= 180 ? rect.bottom + 6 : Math.max(8, rect.top - 6 - 200)
      setPos({ top, left })
    }
    if (!history && !loading) {
      debounceRef.current = setTimeout(fetchHistory, 300)
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
      style={{ display: 'inline-flex' }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {children}
      {show && pos && createPortal(
        <div
          className="status-history-tooltip"
          style={{
            position: 'fixed',
            top: pos.top,
            left: pos.left,
            width: TOOLTIP_WIDTH,
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
          <div style={{ fontWeight: 600, color: TEXT_PRIMARY, marginBottom: 8 }}>Status path</div>

          {loading && <div style={{ color: TEXT_MUTED }}>Loading…</div>}
          {error && <div style={{ color: ERROR_TEXT }}>Failed to load</div>}

          {history && !loading && (
            <>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {history.segments.map((seg, i) => (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 8,
                      opacity: seg.current ? 1 : 0.9,
                    }}
                  >
                    <span style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                      <StatusBadge status={seg.status} />
                      {seg.current && (
                        <span style={{ fontSize: 10, color: LINK_COLOR, fontWeight: 600, whiteSpace: 'nowrap' }}>
                          now
                        </span>
                      )}
                    </span>
                    <span
                      style={{
                        color: TEXT_PRIMARY,
                        fontWeight: seg.current ? 700 : 500,
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {formatDuration(seg.durationSeconds)}
                    </span>
                  </div>
                ))}
              </div>
              <div
                style={{
                  borderTop: `1px solid ${SEPARATOR}`,
                  marginTop: 8,
                  paddingTop: 6,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                  color: TEXT_MUTED,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Total</span>
                  <span style={{ fontWeight: 600, color: TEXT_PRIMARY }}>{formatDuration(history.totalSeconds)}</span>
                </div>
                {history.segments.length > 1 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>Excl. “{history.segments[0].status}”</span>
                    <span style={{ fontWeight: 600, color: TEXT_PRIMARY }}>
                      {formatDuration(history.totalSeconds - history.segments[0].durationSeconds)}
                    </span>
                  </div>
                )}
              </div>
            </>
          )}
        </div>,
        document.body
      )}
    </span>
  )
}
