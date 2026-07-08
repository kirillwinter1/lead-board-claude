import { useState, useEffect } from 'react'
import { Modal } from '../Modal'
import { myWorkApi, type LogTimePayload } from '../../api/myWork'
import { ERROR_BG, ERROR_TEXT, TEXT_MUTED } from '../../constants/colors'

export interface LogTimeTarget { key: string; summary: string }

interface LogTimeModalProps {
  target: LogTimeTarget | null
  onClose: () => void
  onLogged: () => void
}

// Local YYYY-MM-DD — NOT toISOString(), which converts to UTC and can shift
// the date by a day depending on the user's timezone.
function todayLocal(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

export function LogTimeModal({ target, onClose, onLogged }: LogTimeModalProps): JSX.Element | null {
  const [hours, setHours] = useState('')
  const [date, setDate] = useState(todayLocal)
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setHours('')
    setDate(todayLocal())
    setComment('')
    setError(null)
  }, [target?.key])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!target || !hours) return
    setSubmitting(true)
    setError(null)
    try {
      const payload: LogTimePayload = {
        issueKey: target.key,
        date,
        hours: Number(hours),
        comment: comment || undefined,
      }
      await myWorkApi.logTime(payload)
      onLogged()
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error ?? 'Failed to log time')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal isOpen={target != null} onClose={onClose} title={target ? `Log time — ${target.key}` : ''} maxWidth={420}>
      {target && (
        <form onSubmit={handleSubmit} className="modal-form">
          <div style={{ color: TEXT_MUTED, fontSize: 13 }}>{target.summary}</div>
          {error && (
            <div style={{ color: ERROR_TEXT, fontSize: 13, padding: '8px 12px', background: ERROR_BG, borderRadius: 4 }}>
              {error}
            </div>
          )}
          <div className="form-group">
            <label htmlFor="logtime-hours">Hours</label>
            <input
              id="logtime-hours"
              type="number"
              min="0.5"
              step="0.5"
              value={hours}
              onChange={e => setHours(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="logtime-date">Date</label>
            <input
              id="logtime-date"
              type="date"
              max={todayLocal()}
              value={date}
              onChange={e => setDate(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label htmlFor="logtime-comment">Comment</label>
            <textarea
              id="logtime-comment"
              rows={2}
              value={comment}
              onChange={e => setComment(e.target.value)}
              placeholder="Optional"
            />
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting || !hours}>
              {submitting ? 'Logging…' : 'Log time'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
