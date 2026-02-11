import { useState, useRef } from 'react'
import { formatCompact } from './helpers'
import type { EpicRoleChipProps } from './types'

export function EpicRoleChip({ label, role, metrics, epicInTodo, epicKey, config, onUpdate }: EpicRoleChipProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const roleClass = label.toLowerCase()

  const roughEstimate = metrics.roughEstimateDays
  const hasRoughEstimate = roughEstimate !== null && roughEstimate > 0
  const hasLogged = metrics.loggedSeconds > 0

  // Calculate remaining time
  const estimateDays = metrics.estimateSeconds / 3600 / 8
  const loggedDays = metrics.loggedSeconds / 3600 / 8
  const remainingDays = Math.max(0, estimateDays - loggedDays)

  const handleClick = () => {
    if (!epicInTodo || !config?.enabled) return
    setValue(roughEstimate?.toString() ?? '')
    setEditing(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const days = value.trim() === '' ? null : parseFloat(value)
      if (days !== null && isNaN(days)) {
        throw new Error('Invalid number')
      }
      await onUpdate(epicKey, role, days)
      setEditing(false)
    } catch (err) {
      console.error('Failed to save rough estimate:', err)
      alert('Failed to save: ' + (err instanceof Error ? err.message : 'Unknown error'))
    } finally {
      setSaving(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSave()
    } else if (e.key === 'Escape') {
      setEditing(false)
    }
  }

  const handleBlur = () => {
    if (!saving) {
      handleSave()
    }
  }

  // Epic in TODO - editable chip
  if (epicInTodo) {
    if (editing) {
      return (
        <div className={`epic-role-chip ${roleClass} todo editing`}>
          <span className="epic-role-label">{label}</span>
          <input
            ref={inputRef}
            type="number"
            step={config?.stepDays || 0.5}
            min={config?.minDays || 0}
            max={config?.maxDays || 365}
            value={value}
            onChange={e => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleBlur}
            className="epic-role-input"
            disabled={saving}
          />
        </div>
      )
    }

    return (
      <div
        className={`epic-role-chip ${roleClass} todo ${hasRoughEstimate ? '' : 'needs-estimate'}`}
        onClick={handleClick}
        title="Click to set estimate"
      >
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-value">{hasRoughEstimate ? `${roughEstimate}d` : '✎'}</span>
      </div>
    )
  }

  // Epic in progress - show progress bar based on estimate from subtasks
  const hasEstimate = metrics.estimateSeconds > 0
  const progress = hasEstimate ? Math.min(100, (loggedDays / estimateDays) * 100) : 0
  const remainingText = `${formatCompact(remainingDays)}d left`

  // If no real estimate but has rough estimate - show rough estimate (read-only)
  if (!hasEstimate && hasRoughEstimate) {
    return (
      <div
        className={`epic-role-chip ${roleClass} rough-only`}
        title={`Грязная оценка: ${roughEstimate}d`}
      >
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-value rough">{roughEstimate}d</span>
      </div>
    )
  }

  return (
    <div
      className={`epic-role-chip ${roleClass} in-progress ${!hasEstimate ? 'no-estimate' : ''}`}
      title={hasEstimate ? remainingText : undefined}
    >
      <div className="epic-role-header">
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-percent">{hasEstimate ? `${Math.round(progress)}%` : '--'}</span>
      </div>
      {hasEstimate && (
        <>
          <div className="epic-role-progress-bar">
            <div
              className={`epic-role-progress-fill ${progress >= 100 ? 'overburn' : ''}`}
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
          <div className="epic-role-times">
            <span className="time-logged">{formatCompact(loggedDays)}</span>
            <span className="arrow">→</span>
            <span className="time-estimate">{formatCompact(estimateDays)}</span>
          </div>
        </>
      )}
      {!hasEstimate && hasLogged && (
        <div className="epic-role-times warning">
          {formatCompact(loggedDays)}d
        </div>
      )}
    </div>
  )
}
