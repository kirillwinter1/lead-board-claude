import { useEffect, useMemo, useRef, useState } from 'react'
import axios from 'axios'
import { Modal } from '../Modal'
import { SingleSelectDropdown } from '../SingleSelectDropdown'
import { RiceForm } from '../rice/RiceForm'
import { StatusBadge } from '../board/StatusBadge'
import { getIssueIcon } from '../board/helpers'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import {
  applyFix,
  getFixPreview,
  type FixChange,
  type FixInput,
  type FixPreview,
} from '../../api/dataQuality'
import {
  TEXT_PRIMARY,
  TEXT_SECONDARY,
  TEXT_MUTED,
  BG_SUBTLE,
  BG_PAGE,
  BORDER_DEFAULT,
  LINK_COLOR,
  ERROR_TEXT,
  ERROR_BG,
  ERROR_BORDER,
  ERROR_DARK_TEXT,
  SUCCESS_BG,
  SUCCESS_TEXT,
  INFO_BG,
  INFO_TEXT,
  INFO_BORDER,
} from '../../constants/colors'
import './FixModal.css'

const RICE_RULE = 'RICE_MISSING_ASSESSMENT'
const SUCCESS_CLOSE_MS = 800

interface FixModalProps {
  issueKey: string
  rule: string
  ruleLabel: string
  onClose: () => void
  onApplied: () => void
}

/** Extract a human message from an axios error, falling back gracefully. */
function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { message?: string } | undefined
    return data?.message ?? e.message
  }
  return e instanceof Error ? e.message : 'Unexpected error'
}

/**
 * A single change line, styled like the DataQuality table rows:
 * one header line (icon + key + truncated summary), then a from → to detail line.
 * `toOverride` lets the parent reflect a live input selection (e.g. targetStatus)
 * in the right-hand value instead of the static preview `to`.
 */
function ChangeRow({ change, toOverride }: { change: FixChange; toOverride?: string }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()
  const isStatus = change.field === 'Status'
  const toValue = toOverride ?? change.to
  return (
    <div className="fix-change-card" style={{ borderColor: BORDER_DEFAULT, background: BG_SUBTLE }}>
      <div className="fix-change-head">
        {change.issueType && (
          <img
            className="fix-change-icon"
            src={getIssueIcon(change.issueType, getIssueTypeIconUrl(change.issueType), getIssueTypeCategory(change.issueType))}
            alt={change.issueType}
          />
        )}
        <span className="fix-change-key" style={{ color: LINK_COLOR }}>{change.issueKey}</span>
        {change.summary && (
          <span className="fix-change-summary" style={{ color: TEXT_MUTED }}>{change.summary}</span>
        )}
        {change.local && (
          <span className="fix-local-hint" style={{ color: TEXT_MUTED, borderColor: BORDER_DEFAULT }}>
            local
          </span>
        )}
      </div>
      <div className="fix-change-detail" style={{ color: TEXT_SECONDARY }}>
        {change.field && <span className="fix-change-field">{change.field}: </span>}
        {isStatus ? (
          <span className="fix-change-status">
            {change.from ? <StatusBadge status={change.from} /> : <span>∅</span>}
            <span className="fix-change-arrow" style={{ color: TEXT_MUTED }}> → </span>
            {toValue ? <StatusBadge status={toValue} /> : <span>∅</span>}
          </span>
        ) : (
          <>
            <span>{change.from || '∅'}</span>
            <span className="fix-change-arrow" style={{ color: TEXT_MUTED }}> → </span>
            <span style={{ color: TEXT_PRIMARY, fontWeight: 600 }}>{toValue || '∅'}</span>
          </>
        )}
      </div>
    </div>
  )
}

export function FixModal({ issueKey, rule, ruleLabel, onClose, onApplied }: FixModalProps) {
  const isRice = rule === RICE_RULE

  const [preview, setPreview] = useState<FixPreview | null>(null)
  const [loading, setLoading] = useState(!isRice)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedChoiceId, setSelectedChoiceId] = useState<string | null>(null)
  const [inputValues, setInputValues] = useState<Record<string, string>>({})

  const [applying, setApplying] = useState(false)
  const [applyError, setApplyError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => {
    if (closeTimerRef.current !== null) clearTimeout(closeTimerRef.current)
  }, [])

  // Fetch the preview (skipped for the RICE special case).
  useEffect(() => {
    if (isRice) return
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    getFixPreview(issueKey, rule)
      .then(p => { if (!cancelled) setPreview(p) })
      .catch(e => { if (!cancelled) setLoadError(errorMessage(e)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [issueKey, rule, isRice])

  const hasChoices = !!preview?.choices?.length

  // The currently selected choice (defaults to the first one when choices exist).
  const activeChoice = useMemo(() => {
    if (!hasChoices || !preview) return null
    return preview.choices.find(c => c.id === selectedChoiceId) ?? preview.choices[0]
  }, [hasChoices, preview, selectedChoiceId])

  const activeChanges: FixChange[] = activeChoice ? activeChoice.changes : preview?.changes ?? []
  const activeInputs: FixInput[] = activeChoice ? activeChoice.inputs : preview?.inputs ?? []

  // A transition fix may expose a "targetStatus" select; the status change line
  // should track the currently selected value rather than the static preview `to`.
  const hasTargetStatusInput = activeInputs.some(inp => inp.name === 'targetStatus')

  // Default the selected choice to the first one once the preview loads.
  useEffect(() => {
    if (hasChoices && preview && selectedChoiceId === null) {
      setSelectedChoiceId(preview.choices[0].id)
    }
  }, [hasChoices, preview, selectedChoiceId])

  // Reset input values to defaults whenever the preview or active choice changes.
  useEffect(() => {
    if (!preview) return
    const init: Record<string, string> = {}
    for (const inp of activeInputs) {
      init[inp.name] = inp.defaultValue != null ? String(inp.defaultValue) : ''
    }
    setInputValues(init)
    // activeInputs is derived from preview + selectedChoiceId
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [preview, selectedChoiceId])

  const allRequiredFilled = activeInputs.every(
    inp => !inp.required || (inputValues[inp.name] ?? '').toString().trim() !== '',
  )

  const setInput = (name: string, value: string) =>
    setInputValues(prev => ({ ...prev, [name]: value }))

  const scheduleClose = () => {
    if (closeTimerRef.current !== null) clearTimeout(closeTimerRef.current)
    closeTimerRef.current = setTimeout(() => { onApplied() }, SUCCESS_CLOSE_MS)
  }

  const handleApply = async () => {
    if (!preview) return
    setApplyError(null)
    setApplying(true)
    try {
      const params: Record<string, unknown> = {}
      for (const inp of activeInputs) {
        const raw = inputValues[inp.name] ?? ''
        params[inp.name] = inp.type === 'number' ? Number(raw) : raw
      }
      const result = await applyFix({
        issueKey: preview.issueKey,
        rule: preview.rule,
        choiceId: activeChoice?.id,
        params,
      })
      if (result.success) {
        setSuccessMessage(result.message || 'Fix applied')
        scheduleClose()
      } else {
        setApplyError(result.message || 'Fix failed')
      }
    } catch (e) {
      const status = axios.isAxiosError(e) ? e.response?.status : undefined
      const msg = errorMessage(e)
      if (status === 409) {
        // The violation was already resolved elsewhere — refresh the report.
        setSuccessMessage(msg || 'This issue was already resolved')
        scheduleClose()
      } else {
        setApplyError(msg)
      }
    } finally {
      setApplying(false)
    }
  }

  const canApply = !applying && !successMessage && allRequiredFilled

  // ---- RICE special case: embed the existing RiceForm, no preview fetch. ----
  if (isRice) {
    return (
      <Modal isOpen onClose={onClose} title={`Fix: ${ruleLabel}`} maxWidth={640}>
        <RiceForm issueKey={issueKey} onSaved={onApplied} />
      </Modal>
    )
  }

  return (
    <Modal isOpen onClose={onClose} title={`Fix: ${ruleLabel}`} maxWidth={640}>
      {loading && <div className="fix-modal-loading" style={{ color: TEXT_MUTED }}>Loading preview...</div>}

      {!loading && loadError && (
        <>
          <div className="fix-error" style={{ color: ERROR_TEXT }}>{loadError}</div>
          <div className="modal-actions" style={{ marginTop: 16 }}>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Close
            </button>
          </div>
        </>
      )}

      {!loading && !loadError && preview && !preview.applicable && (
        <>
          <div
            className="fix-info-panel"
            style={{ background: INFO_BG, borderColor: INFO_BORDER, color: INFO_TEXT }}
          >
            {preview.notApplicableReason || 'This fix is no longer applicable.'}
          </div>
          <div className="modal-actions" style={{ marginTop: 16 }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onApplied}
            >
              Close
            </button>
          </div>
        </>
      )}

      {!loading && !loadError && preview && preview.applicable && (
        <>
          {/* Lead line — neutral description of what the fix does */}
          {preview.title && (
            <div className="fix-lead" style={{ color: TEXT_SECONDARY }}>{preview.title}</div>
          )}

          {/* Choices (radio group) */}
          {hasChoices && (
            <div className="fix-choice-group" role="radiogroup" aria-label="Fix options">
              {preview.choices.map(choice => {
                const checked = (activeChoice?.id ?? null) === choice.id
                return (
                  <label
                    key={choice.id}
                    className={`fix-choice ${checked ? 'fix-choice-active' : ''}`}
                    style={{
                      borderColor: checked ? LINK_COLOR : BORDER_DEFAULT,
                      background: checked ? BG_SUBTLE : BG_PAGE,
                      color: TEXT_PRIMARY,
                    }}
                  >
                    <input
                      type="radio"
                      name="fix-choice"
                      value={choice.id}
                      checked={checked}
                      onChange={() => setSelectedChoiceId(choice.id)}
                    />
                    <span>{choice.label}</span>
                  </label>
                )
              })}
            </div>
          )}

          {/* Change lines. When a transition fix exposes a `targetStatus` select,
              the right-hand status badge reflects the user's live selection. */}
          {activeChanges.length > 0 && (
            <div className="fix-change-list">
              {activeChanges.map((c, idx) => {
                const toOverride =
                  c.field === 'Status' && hasTargetStatusInput
                    ? inputValues.targetStatus || c.to
                    : undefined
                return (
                  <ChangeRow key={`${c.issueKey}-${c.field}-${idx}`} change={c} toOverride={toOverride} />
                )
              })}
            </div>
          )}

          {/* Inputs */}
          {activeInputs.length > 0 && (
            <div className="fix-input-list">
              {activeInputs.map(inp => (
                <div key={inp.name} className="fix-input-row">
                  {inp.type === 'select' ? (
                    <SingleSelectDropdown
                      label={inp.label}
                      options={(inp.options ?? []).map(o => ({ ...o, color: o.color ?? undefined }))}
                      selected={inputValues[inp.name] || null}
                      onChange={v => setInput(inp.name, v ?? '')}
                      placeholder={inp.label}
                      allowClear={!inp.required}
                    />
                  ) : (
                    <label className="fix-input-label" style={{ color: TEXT_SECONDARY }}>
                      <span>{inp.label}</span>
                      <input
                        type={inp.type}
                        value={inputValues[inp.name] ?? ''}
                        min={inp.min}
                        step={inp.step}
                        required={inp.required}
                        onChange={e => setInput(inp.name, e.target.value)}
                        style={{ borderColor: BORDER_DEFAULT, color: TEXT_PRIMARY, background: BG_PAGE }}
                      />
                    </label>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Risky warning + affected issues */}
          {preview.risky && (
            <div
              className="fix-warning-box"
              style={{ background: ERROR_BG, borderColor: ERROR_BORDER, color: ERROR_DARK_TEXT }}
            >
              {preview.warning && <div className="fix-warning-text">{preview.warning}</div>}
              {preview.affectedIssues.length > 0 && (
                <ul className="fix-affected-list">
                  {preview.affectedIssues.map((line, idx) => (
                    <li key={`${idx}-${line}`}>{line}</li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {/* Auth-mode hint */}
          {preview.authMode === 'BASIC' && (
            <div className="fix-auth-hint" style={{ color: TEXT_MUTED }}>
              Changes will be applied by the service account
            </div>
          )}

          {applyError && <div className="fix-error" style={{ color: ERROR_TEXT }}>{applyError}</div>}
          {successMessage && (
            <div className="fix-success" style={{ background: SUCCESS_BG, color: SUCCESS_TEXT }}>
              {successMessage}
            </div>
          )}

          <div className="modal-actions" style={{ marginTop: 16 }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onClose}
              disabled={applying}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleApply}
              disabled={!canApply}
              style={{ opacity: canApply ? 1 : 0.5 }}
            >
              {applying ? 'Applying...' : 'Apply'}
            </button>
          </div>
        </>
      )}
    </Modal>
  )
}
