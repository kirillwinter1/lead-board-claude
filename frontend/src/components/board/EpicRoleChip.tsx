import { useState, useRef } from 'react'
import { formatCompact } from './helpers'
import type { EpicRoleChipProps } from './types'
import { lightenColor, ERROR_TEXT } from '../../constants/colors'

export function EpicRoleChip({ label, role, metrics, epicInTodo, epicKey, config, onUpdate, roleColor, estimateSource }: EpicRoleChipProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
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
    setSaveError(null)
    setEditing(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const handleSave = async () => {
    setSaving(true)
    setSaveError(null)
    try {
      const days = value.trim() === '' ? null : parseFloat(value)
      if (days !== null && isNaN(days)) {
        throw new Error('Invalid number')
      }
      await onUpdate(epicKey, role, days)
      setEditing(false)
    } catch (err) {
      // F91: inline error instead of a native alert() — the input stays open.
      console.error('Failed to save rough estimate:', err)
      setSaveError(err instanceof Error ? err.message : 'Unknown error')
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
        <div
          className="epic-role-chip todo editing"
          style={{ color: roleColor, borderColor: saveError ? ERROR_TEXT : borderColor }}
          title={saveError ? `Failed to save: ${saveError}` : undefined}
        >
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
            aria-invalid={saveError ? true : undefined}
          />
          {saveError && (
            <span role="alert" className="epic-role-error">Failed to save: {saveError}</span>
          )}
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

  // If no real estimate but has rough estimate - show it read-only.
  // F23: a 'clean' epic (poker estimates exist) renders filled; a 'rough' one stays
  // outlined/muted. The tooltip spells out which kind of estimate it is.
  if (!hasEstimate && hasRoughEstimate) {
    const isClean = estimateSource === 'clean'
    return (
      <div
        className={`epic-role-chip rough-only ${isClean ? 'clean' : ''}`}
        style={isClean ? { color: '#fff', background: fillColor, borderColor: fillColor } : { color: roleColor, borderColor }}
        title={isClean ? `Poker estimate: ${roughEstimate}d` : `Rough estimate (pre-planning): ${roughEstimate}d`}
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
