import { useState, useEffect } from 'react'
import { Modal } from '../Modal'
import { myWorkApi, type LogTimePayload } from '../../api/myWork'
import { parseDuration, formatDuration } from '../../utils/duration'
import {
  ERROR_BG, ERROR_TEXT, TEXT_MUTED, TEXT_SECONDARY,
  PROGRESS_IN_PROGRESS, PROGRESS_TRACK,
} from '../../constants/colors'

export interface LogTimeTarget {
  key: string
  summary: string
  originalEstimateH: number | null
  spentH: number | null
  remainingH: number | null
}

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

function initialRemaining(target: LogTimeTarget | null): string {
  if (!target || target.remainingH == null) return '0m'
  return formatDuration(target.remainingH * 3600)
}

export function LogTimeModal({ target, onClose, onLogged }: LogTimeModalProps): JSX.Element | null {
  const [timeSpent, setTimeSpent] = useState('')
  const [remaining, setRemaining] = useState(() => initialRemaining(target))
  // Once the user edits Remaining by hand we stop auto-recomputing it from
  // Time spent (Jira's "auto" adjust mode until it's manually overridden).
  const [remainingTouched, setRemainingTouched] = useState(false)
  const [date, setDate] = useState(todayLocal)
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const currentRemainingSec = Math.round((target?.remainingH ?? 0) * 3600)

  useEffect(() => {
    setTimeSpent('')
    setRemaining(initialRemaining(target))
    setRemainingTouched(false)
    setDate(todayLocal())
    setComment('')
    setError(null)
  }, [target?.key])

  // Auto-decrement Remaining as Time spent is typed, unless the user has taken
  // control of the field. Empty/invalid Time spent → Remaining reverts to the
  // task's current remaining estimate.
  const handleTimeSpentChange = (value: string) => {
    setTimeSpent(value)
    if (!remainingTouched) {
      const spent = parseDuration(value) ?? 0
      setRemaining(formatDuration(Math.max(0, currentRemainingSec - spent)))
    }
  }

  const handleRemainingChange = (value: string) => {
    setRemainingTouched(true)
    setRemaining(value)
  }

  const parsedSpent = parseDuration(timeSpent)
  const parsedRemaining = parseDuration(remaining)

  // Live progress bar. On invalid input, fall back to the current stored values
  // so the bar never jumps to zero / NaN while the user is typing.
  const spentSecCurrent = (target?.spentH ?? 0) * 3600
  const remainingSec = parsedRemaining ?? (target?.remainingH != null ? target.remainingH * 3600 : 0)
  const filled = spentSecCurrent + (parsedSpent ?? 0)
  const total = filled + remainingSec
  const filledPct = total > 0 ? Math.min(100, (filled / total) * 100) : 0

  const spentValid = parsedSpent != null && parsedSpent > 0
  const remainingValid = parsedRemaining != null && parsedRemaining >= 0
  const canSubmit = !submitting && timeSpent.trim() !== '' && spentValid && remainingValid

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!target) return
    if (parsedSpent == null || parsedSpent <= 0) {
      setError('Invalid time format')
      return
    }
    if (parsedRemaining == null || parsedRemaining < 0) {
      setError('Invalid remaining format')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const payload: LogTimePayload = {
        issueKey: target.key,
        date,
        timeSpentSeconds: parsedSpent,
        remainingEstimateSeconds: parsedRemaining,
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
    <Modal isOpen={target != null} onClose={onClose} title={target ? `Log time — ${target.key}` : ''} maxWidth={460}>
      {target && (
        <form onSubmit={handleSubmit} className="modal-form">
          <div style={{ color: TEXT_MUTED, fontSize: 13 }}>{target.summary}</div>

          {error && (
            <div style={{ color: ERROR_TEXT, fontSize: 13, padding: '8px 12px', background: ERROR_BG, borderRadius: 4 }}>
              {error}
            </div>
          )}

          {/* Progress bar: logged vs remaining */}
          <div>
            <div
              role="progressbar"
              aria-valuenow={Math.round(filledPct)}
              aria-valuemin={0}
              aria-valuemax={100}
              style={{ height: 8, borderRadius: 4, background: PROGRESS_TRACK, overflow: 'hidden' }}
            >
              <div style={{ width: `${filledPct}%`, height: '100%', background: PROGRESS_IN_PROGRESS, transition: 'width 120ms ease' }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4, fontSize: 12, color: TEXT_MUTED }}>
              <span>{formatDuration(filled)} logged</span>
              <span>Remaining: {formatDuration(remainingSec)}</span>
            </div>
          </div>

          {target.originalEstimateH != null && (
            <div style={{ fontSize: 12, color: TEXT_SECONDARY }}>
              Original estimate — {formatDuration(target.originalEstimateH * 3600)}
            </div>
          )}

          <div style={{ display: 'flex', gap: 12 }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label htmlFor="logtime-spent">Time spent</label>
              <input
                id="logtime-spent"
                type="text"
                value={timeSpent}
                onChange={e => handleTimeSpentChange(e.target.value)}
                placeholder="2w 4d 6h 45m"
                autoComplete="off"
                required
              />
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label htmlFor="logtime-remaining">Remaining</label>
              <input
                id="logtime-remaining"
                type="text"
                value={remaining}
                onChange={e => handleRemainingChange(e.target.value)}
                placeholder="0m"
                autoComplete="off"
              />
            </div>
          </div>

          <div style={{ fontSize: 12, color: TEXT_MUTED, marginTop: -4 }}>
            Use format: 2w 4d 6h 45m (w = week, d = day, h = hour, m = minute)
          </div>

          <div className="form-group">
            <label htmlFor="logtime-date">Date</label>
            <input
              id="logtime-date"
              type="date"
              max={todayLocal()}
              value={date}
              onChange={e => setDate(e.target.value)}
              required
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
            <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
              {submitting ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
