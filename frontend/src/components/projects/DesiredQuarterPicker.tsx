import { useEffect, useRef, useState } from 'react'
import { SingleSelectDropdown } from '../SingleSelectDropdown'
import { quarterlyPlanningApi, ProjectQuarterCommitmentDto } from '../../api/quarterlyPlanning'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  ERROR_TEXT,
  INFO_BG,
  INFO_TEXT,
  INFO_BORDER,
} from '../../constants/colors'

interface DesiredQuarterPickerProps {
  projectKey: string
  currentDesiredQuarter: string | null
  availableQuarters: string[]
  /**
   * Fired after the server has acknowledged the change. The parent decides what
   * to do with the fresh commitment view (typically: replace cached state and
   * re-render {@link ProjectCommitmentView}).
   */
  onChange: (commitment: ProjectQuarterCommitmentDto) => void
  /**
   * When true, render a read-only display (no editing). Used for viewers and
   * members. ROLE_ADMIN / ROLE_PROJECT_MANAGER can edit. The backend enforces
   * the same authorization, so this is purely UX gating.
   */
  readOnly?: boolean
}

const NULL_VALUE = '__none__'

/**
 * F70 — PM-facing picker for a project's desired quarter.
 *
 * Persists immediately on change (no save button) so commitment numbers
 * stay in sync with the dropdown without an extra click. Loading and error
 * states are kept tight to the control to avoid disrupting the surrounding
 * project card layout.
 */
export function DesiredQuarterPicker({
  projectKey,
  currentDesiredQuarter,
  availableQuarters,
  onChange,
  readOnly = false,
}: DesiredQuarterPickerProps) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // H3: track the latest user-requested quarter. When several rapid changes
  // overlap (Q1 -> Q2 -> Q3), only the most recent one is allowed to apply
  // its response — earlier in-flight requests are stale by the time they
  // resolve, and applying them would clobber a fresher selection.
  const latestTargetRef = useRef<string | null | undefined>(undefined)
  // Cancel state updates after unmount.
  const cancelledRef = useRef(false)
  useEffect(() => {
    cancelledRef.current = false
    return () => { cancelledRef.current = true }
  }, [])

  const options = availableQuarters.map(q => ({ value: q, label: q }))

  // Augment options with a "no desired quarter" option representing null. We
  // synthesize a sentinel value because SingleSelectDropdown surfaces clear
  // via `allowClear` but we want the picker to expose the meaning explicitly.
  const optionsWithNone = [{ value: NULL_VALUE, label: 'Не запланирован' }, ...options]

  const selected = currentDesiredQuarter ?? NULL_VALUE

  if (readOnly) {
    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          padding: '4px 10px',
          background: currentDesiredQuarter ? INFO_BG : 'transparent',
          color: currentDesiredQuarter ? INFO_TEXT : TEXT_MUTED,
          border: currentDesiredQuarter ? `1px solid ${INFO_BORDER}` : 'none',
          borderRadius: 4,
          fontSize: 12,
          fontWeight: 600,
        }}
      >
        {currentDesiredQuarter ?? 'Не запланирован'}
      </span>
    )
  }

  const handleChange = async (value: string | null) => {
    // SingleSelectDropdown will pass null when "clear" is invoked; we treat
    // both null and the explicit sentinel as the same intent.
    const target = !value || value === NULL_VALUE ? null : value
    if (target === currentDesiredQuarter) return
    // H3: mark this as the latest intended target so concurrent in-flight
    // saves know if their response is stale.
    latestTargetRef.current = target
    setSaving(true)
    setError(null)
    try {
      const result = await quarterlyPlanningApi.setProjectDesiredQuarter(projectKey, target)
      // Bail if a newer change has been requested since, or if we've unmounted.
      if (cancelledRef.current) return
      if (latestTargetRef.current !== target) return
      onChange(result)
    } catch (err) {
      if (cancelledRef.current) return
      if (latestTargetRef.current !== target) return
      setError(err instanceof Error ? err.message : 'Не удалось сохранить квартал')
    } finally {
      if (cancelledRef.current) return
      // Only the most recent request should clear the saving indicator;
      // older overlapping requests would otherwise hide the spinner while
      // a newer save is still in flight.
      if (latestTargetRef.current === target) {
        setSaving(false)
      }
    }
  }

  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
      <div
        style={{ opacity: saving ? 0.6 : 1, pointerEvents: saving ? 'none' : 'auto' }}
        aria-busy={saving}
      >
        <SingleSelectDropdown
          label="Desired quarter"
          options={optionsWithNone}
          selected={selected}
          onChange={handleChange}
          placeholder="Не запланирован"
          allowClear={false}
        />
      </div>
      {error && (
        <span role="alert" style={{ fontSize: 11, color: ERROR_TEXT }}>{error}</span>
      )}
      {saving && (
        <span role="status" aria-live="polite" style={{ fontSize: 11, color: TEXT_PRIMARY }}>
          Сохраняем…
        </span>
      )}
    </div>
  )
}
