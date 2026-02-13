import { useState, useRef } from 'react'
import { formatCompact } from './helpers'
import type { EpicRoleChipProps } from './types'

// Generate a lighter shade for borders/backgrounds from the role color
function lightenColor(hex: string, factor: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lr = Math.round(r + (255 - r) * factor)
  const lg = Math.round(g + (255 - g) * factor)
  const lb = Math.round(b + (255 - b) * factor)
  return `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
}

export function EpicRoleChip({ label, role, metrics, epicInTodo, epicKey, config, onUpdate, roleColor }: EpicRoleChipProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const roughEstimate = metrics.roughEstimateDays
  const hasRoughEstimate = roughEstimate !== null && roughEstimate > 0
  const hasLogged = metrics.loggedSeconds > 0

  // Calculate remaining time
  const estimateDays = metrics.estimateSeconds / 3600 / 8
  const loggedDays = metrics.loggedSeconds / 3600 / 8
  const remainingDays = Math.max(0, estimateDays - loggedDays)

  const borderColor = lightenColor(roleColor, 0.6)
  const fillColor = lightenColor(roleColor, 0.3)

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
        <div className="epic-role-chip todo editing" style={{ color: roleColor, borderColor }}>
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
        className={`epic-role-chip todo ${hasRoughEstimate ? '' : 'needs-estimate'}`}
        style={{ color: roleColor, borderColor }}
        onClick={handleClick}
        title="Click to set estimate"
      >
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-value">{hasRoughEstimate ? `${roughEstimate}d` : '\u270E'}</span>
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
        className="epic-role-chip rough-only"
        style={{ color: roleColor, borderColor }}
        title={`\u0413\u0440\u044F\u0437\u043D\u0430\u044F \u043E\u0446\u0435\u043D\u043A\u0430: ${roughEstimate}d`}
      >
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-value rough">{roughEstimate}d</span>
      </div>
    )
  }

  return (
    <div
      className={`epic-role-chip in-progress ${!hasEstimate ? 'no-estimate' : ''}`}
      style={{ color: roleColor }}
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
              style={{ width: `${Math.min(progress, 100)}%`, background: fillColor }}
            />
          </div>
          <div className="epic-role-times">
            <span className="time-logged">{formatCompact(loggedDays)}</span>
            <span className="arrow">{'\u2192'}</span>
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
