import { useState } from 'react'
import { Modal } from '../Modal'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  TEXT_SECONDARY,
  BG_SUBTLE,
  BORDER_DEFAULT,
  LINK_COLOR,
  ERROR_TEXT,
  DSR_GREEN,
} from '../../constants/colors'

export type PendingActionType = 'add' | 'remove' | 'move' | 'boost'

export interface PendingChange {
  epicKey: string
  epicSummary: string
  action: PendingActionType
  fromQuarter?: string | null
  toQuarter?: string | null
  fromBoost?: number
  toBoost?: number
}

export interface PublishResultItem {
  change: PendingChange
  ok: boolean
  error?: string
}

interface PublishToJiraModalProps {
  isOpen: boolean
  onClose: () => void
  pendingChanges: PendingChange[]
  onConfirm: () => Promise<PublishResultItem[]>
}

function describeChange(c: PendingChange): string {
  if (c.action === 'add') return `Add quarter label ${c.toQuarter ?? ''}`
  if (c.action === 'remove') return `Remove quarter label ${c.fromQuarter ?? ''}`
  if (c.action === 'move') return `Move from ${c.fromQuarter ?? '∅'} → ${c.toQuarter ?? '∅'}`
  if (c.action === 'boost') return `Boost ${formatBoost(c.fromBoost ?? 0)} → ${formatBoost(c.toBoost ?? 0)}`
  return ''
}

function formatBoost(value: number): string {
  if (value === 0) return '0'
  return value > 0 ? `+${value}` : String(value)
}

function actionColor(action: PendingActionType): string {
  if (action === 'add') return DSR_GREEN
  if (action === 'remove') return ERROR_TEXT
  if (action === 'move') return LINK_COLOR
  return TEXT_MUTED
}

export function PublishToJiraModal({
  isOpen,
  onClose,
  pendingChanges,
  onConfirm,
}: PublishToJiraModalProps) {
  const [publishing, setPublishing] = useState(false)
  const [results, setResults] = useState<PublishResultItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setError(null)
    setPublishing(true)
    try {
      const res = await onConfirm()
      setResults(res)
      const allOk = res.every(r => r.ok)
      if (allOk) {
        // Auto-close after a short delay so user sees the success
        setTimeout(() => {
          handleReset()
          onClose()
        }, 800)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unexpected error during publish')
    } finally {
      setPublishing(false)
    }
  }

  const handleReset = () => {
    setPublishing(false)
    setResults(null)
    setError(null)
  }

  const handleClose = () => {
    if (publishing) return
    handleReset()
    onClose()
  }

  const failedChanges = (results ?? []).filter(r => !r.ok).map(r => r.change)
  const hasFailures = failedChanges.length > 0

  const visibleChanges: PendingChange[] = hasFailures ? failedChanges : pendingChanges

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Опубликовать → Jira" maxWidth={640}>
      {pendingChanges.length === 0 && !results && (
        <div style={{ color: TEXT_MUTED, fontSize: 13 }}>No pending changes.</div>
      )}

      {pendingChanges.length > 0 && (
        <>
          <p style={{ margin: '0 0 16px', color: TEXT_SECONDARY, fontSize: 13 }}>
            {hasFailures
              ? `${failedChanges.length} change${failedChanges.length === 1 ? '' : 's'} failed. Review and retry.`
              : `${pendingChanges.length} change${pendingChanges.length === 1 ? '' : 's'} will be written to Jira.`}
          </p>

          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              maxHeight: 320,
              overflowY: 'auto',
              border: `1px solid ${BORDER_DEFAULT}`,
              borderRadius: 6,
              background: BG_SUBTLE,
              padding: 8,
            }}
          >
            {visibleChanges.map((c, idx) => {
              const result = results?.find(r => r.change.epicKey === c.epicKey && r.change.action === c.action)
              return (
                <div
                  key={`${c.epicKey}-${c.action}-${idx}`}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '8px 10px',
                    background: '#fff',
                    border: `1px solid ${BORDER_DEFAULT}`,
                    borderRadius: 4,
                  }}
                >
                  <span
                    style={{
                      flexShrink: 0,
                      fontSize: 11,
                      fontWeight: 700,
                      color: actionColor(c.action),
                      textTransform: 'uppercase',
                      width: 60,
                    }}
                  >
                    {c.action}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: TEXT_PRIMARY }}>
                      {c.epicKey}
                    </div>
                    <div style={{
                      fontSize: 12,
                      color: TEXT_MUTED,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
                      {c.epicSummary}
                    </div>
                    <div style={{ fontSize: 12, color: TEXT_SECONDARY, marginTop: 2 }}>
                      {describeChange(c)}
                    </div>
                    {result && !result.ok && result.error && (
                      <div style={{ fontSize: 12, color: ERROR_TEXT, marginTop: 2 }}>
                        {result.error}
                      </div>
                    )}
                  </div>
                  {result && (
                    <span style={{ fontSize: 12, fontWeight: 700, color: result.ok ? DSR_GREEN : ERROR_TEXT }}>
                      {result.ok ? 'OK' : 'FAILED'}
                    </span>
                  )}
                </div>
              )
            })}
          </div>

          {error && (
            <div style={{ marginTop: 12, color: ERROR_TEXT, fontSize: 13 }}>{error}</div>
          )}

          <div className="modal-actions" style={{ marginTop: 16 }}>
            <button
              type="button"
              onClick={handleClose}
              disabled={publishing}
              style={{
                padding: '8px 16px',
                background: '#fff',
                color: TEXT_PRIMARY,
                border: `1px solid ${BORDER_DEFAULT}`,
                borderRadius: 4,
                fontWeight: 600,
                cursor: publishing ? 'not-allowed' : 'pointer',
              }}
            >
              {results ? 'Close' : 'Cancel'}
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={publishing || pendingChanges.length === 0}
              style={{
                padding: '8px 16px',
                background: LINK_COLOR,
                color: '#fff',
                border: 'none',
                borderRadius: 4,
                fontWeight: 600,
                cursor: publishing ? 'wait' : 'pointer',
                opacity: pendingChanges.length === 0 ? 0.5 : 1,
              }}
            >
              {publishing ? 'Publishing...' : (hasFailures ? 'Retry failed' : 'Publish')}
            </button>
          </div>
        </>
      )}
    </Modal>
  )
}
