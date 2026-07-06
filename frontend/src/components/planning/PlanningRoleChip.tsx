import { useState, useRef, KeyboardEvent } from 'react'
import { RoughEstimateConfig } from '../../api/epics'
import { lightenColor } from '../../constants/colors'

interface PlanningRoleChipProps {
  epicKey: string
  role: string
  days: number | null
  roleColor: string
  /** When false the chip is display-only (epic status not editable per config). */
  editable: boolean
  config: RoughEstimateConfig | null
  onSave: (epicKey: string, role: string, days: number | null) => Promise<void>
}

/**
 * Rough-estimate chip for planning cards — same look and interaction as the
 * Board page's EpicRoleChip (shared `.epic-role-chip` CSS): click → inline
 * input → Enter/blur saves, Escape cancels. Roles without an estimate show a
 * pencil placeholder when editable.
 */
export function PlanningRoleChip({ epicKey, role, days, roleColor, editable, config, onSave }: PlanningRoleChipProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [saving, setSaving] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const hasValue = days !== null && days > 0
  const borderColor = lightenColor(roleColor, 0.6)

  const handleClick = () => {
    if (!editable) return
    setValue(hasValue ? String(days) : '')
    setEditing(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const parsed = value.trim() === '' ? null : parseFloat(value)
      if (parsed !== null && isNaN(parsed)) throw new Error('Invalid number')
      await onSave(epicKey, role, parsed)
      setEditing(false)
    } catch (err) {
      console.error('Failed to save rough estimate:', err)
    } finally {
      setSaving(false)
    }
  }

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter') handleSave()
    else if (e.key === 'Escape') setEditing(false)
  }

  if (editable && editing) {
    return (
      <div className="epic-role-chip todo editing" style={{ color: roleColor, borderColor }}>
        <span className="epic-role-label">{role}</span>
        <input
          ref={inputRef}
          type="number"
          step={config?.stepDays || 0.5}
          min={config?.minDays || 0}
          max={config?.maxDays || 365}
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={() => { if (!saving) handleSave() }}
          className="epic-role-input"
          disabled={saving}
        />
      </div>
    )
  }

  if (editable) {
    return (
      <div
        className={`epic-role-chip todo ${hasValue ? '' : 'needs-estimate'}`}
        style={{ color: roleColor, borderColor }}
        onClick={handleClick}
        title="Click to set estimate"
      >
        <span className="epic-role-label">{role}</span>
        <span className="epic-role-value">{hasValue ? `${days}d` : '✎'}</span>
      </div>
    )
  }

  // Read-only: same rough-only look the Board uses for non-editable epics.
  return (
    <div className="epic-role-chip rough-only" style={{ color: roleColor, borderColor }}>
      <span className="epic-role-label">{role}</span>
      <span className="epic-role-value rough">{hasValue ? `${days}d` : '—'}</span>
    </div>
  )
}
