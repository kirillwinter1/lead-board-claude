import { useState, useRef, useEffect, useMemo, KeyboardEvent, ReactNode } from 'react'
import { RiceScoreBadge } from '../rice/RiceScoreBadge'
import { StatusBadge } from '../board/StatusBadge'
import { getIssueIcon } from '../board/helpers'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { PlanningEpicDto, EpicRemainingDto } from '../../api/quarterlyPlanning'
import { RoughEstimateConfig } from '../../api/epics'
import { PlanningRoleChip } from './PlanningRoleChip'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  TEXT_SECONDARY,
  ERROR_TEXT,
  ERROR_DARK_TEXT,
  ERROR_BG,
  ERROR_BORDER,
  LINK_COLOR,
  BG_SUBTLE,
  BG_PAGE,
  BORDER_DEFAULT,
  WARNING_BG,
  WARNING_BORDER,
  WARNING_TEXT,
  SUCCESS_BG,
  SUCCESS_TEXT,
  INFO_BG,
  INFO_TEXT,
  INFO_BORDER,
  lightenColor,
} from '../../constants/colors'

interface EpicCardProps {
  epic: PlanningEpicDto
  mode: 'backlog' | 'in-quarter'
  targetQuarter: string
  /**
   * F70: the currently-selected quarter on the planning page. Used to decide
   * whether to show the `PM желает {desired}` badge — we only show it when the
   * project's desired quarter differs from the quarter the tech lead is viewing.
   */
  currentQuarter: string
  jiraBaseUrl: string
  /**
   * F86: remaining work for this epic (now vs at quarter start). Loaded lazily
   * by the page — undefined until it arrives, or when the epic has no estimate.
   */
  remaining?: EpicRemainingDto
  /**
   * Whether to render the `Project: …` row. Columns pass false when the card
   * already sits under a project group header that names the same project.
   */
  showProject?: boolean
  /**
   * Rough-estimate editing (same rules as the Board page): chips become
   * click-to-edit when the config allows the epic's status. Null/undefined
   * config keeps chips read-only.
   */
  estimateConfig?: RoughEstimateConfig | null
  onEstimateChange?: (epicKey: string, role: string, days: number | null) => Promise<void>
  onMove: (epicKey: string, toQuarter: string | null) => void
  onBoostChange: (epicKey: string, boost: number) => void
}

function clampBoost(value: number): number {
  if (Number.isNaN(value)) return 0
  return Math.max(-50, Math.min(50, Math.round(value)))
}

function formatBoost(boost: number): string {
  if (boost === 0) return '0'
  return boost > 0 ? `+${boost}` : String(boost)
}

/**
 * F86: quarterly planning is deliberately coarse — person-days are shown as
 * whole numbers (5.3 → 5), fractional tails carry no signal at this scale.
 */
function formatDays(value: number): string {
  return String(Math.round(value))
}

export function EpicCard({
  epic,
  mode,
  targetQuarter,
  currentQuarter,
  jiraBaseUrl,
  remaining,
  showProject = true,
  estimateConfig,
  onEstimateChange,
  onMove,
  onBoostChange,
}: EpicCardProps) {
  const { getIssueTypeIconUrl, getIssueTypeCategory, getRoleColor, getRoleCodes } = useWorkflowConfig()
  const [editingBoost, setEditingBoost] = useState(false)
  const [boostDraft, setBoostDraft] = useState<string>(String(epic.manualBoost))
  const boostInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setBoostDraft(String(epic.manualBoost))
  }, [epic.manualBoost])

  useEffect(() => {
    if (editingBoost) {
      boostInputRef.current?.focus()
      boostInputRef.current?.select()
    }
  }, [editingBoost])

  const iconUrl = getIssueIcon(epic.typeName, epic.iconUrl ?? getIssueTypeIconUrl(epic.typeName), getIssueTypeCategory(epic.typeName))

  const inOtherQuarter = !!epic.quarterLabel && !epic.inQuarter

  // F70: PM is asking for a different quarter than the one the tech lead is viewing.
  // We deliberately key off `currentQuarter` (the screen's selected quarter), not the
  // epic's `committed_quarter`, because the tech lead's reference frame is the column
  // they're planning. If desired == current quarter, no nudge is needed.
  const pmDesiresDifferentQuarter =
    !!epic.projectDesiredQuarter && epic.projectDesiredQuarter !== currentQuarter

  const handleBoostSave = () => {
    const parsed = parseInt(boostDraft, 10)
    const next = clampBoost(parsed)
    if (next !== epic.manualBoost) {
      onBoostChange(epic.epicKey, next)
    }
    setEditingBoost(false)
  }

  const handleBoostKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleBoostSave()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      setBoostDraft(String(epic.manualBoost))
      setEditingBoost(false)
    }
  }

  // Rough estimates are editable under the same rules as the Board page:
  // feature enabled and the epic's status is in the allowed list.
  const estimateEditable = !!estimateConfig?.enabled && !!onEstimateChange && epic.estimateEditable

  // Build a stable order of roles based on workflow config. Editable epics show
  // ALL pipeline roles (empty ones render a pencil placeholder, as on the
  // Board); read-only epics show only roles that carry an estimate.
  const orderedRoles: string[] = useMemo(() => {
    const codes = getRoleCodes()
    const present = new Set(Object.keys(epic.demandByRole))
    const ordered: string[] = []
    codes.forEach(code => { if (estimateEditable || present.has(code)) ordered.push(code) })
    present.forEach(code => { if (!codes.includes(code)) ordered.push(code) })
    return estimateEditable ? ordered : ordered.filter(code => (epic.demandByRole[code] || 0) > 0)
  }, [epic.demandByRole, getRoleCodes, estimateEditable])

  // F86: in the backlog column, flag epics whose remaining work still needs to
  // be planned into the viewed quarter — either uncommitted (no quarterLabel)
  // or a carryover tail from a past quarter (quarterLabel < currentQuarter).
  const needsPlanningWork = mode === 'backlog' && (!epic.quarterLabel || epic.quarterLabel < currentQuarter)

  // The «Осталась работа» badge is a measured fact, not an assumption: it only
  // shows when the planner actually computed a non-zero remainder. Unestimated
  // needs-planning epics get the plain «нет оценки» badge instead.
  const showRemainingWork =
    needsPlanningWork && !!remaining && remaining.hasEstimate && remaining.remainingNowDays > 0

  // Deduplicate the three number rows: a remaining row is rendered only when it
  // actually differs from the row above it (per-role, 0.05d tolerance). For an
  // untouched epic all three match — the badge alone carries the signal.
  const sameByRole = (a: Record<string, number>, b: Record<string, number>) => {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)])
    for (const k of keys) {
      if (Math.abs((a[k] || 0) - (b[k] || 0)) >= 0.05) return false
    }
    return true
  }
  // Compare against the estimate row only when that row is actually visible
  // (estimation phase) — for in-work epics the remaining row is the only
  // numbers row and must always render.
  const remainingEqualsEstimate =
    epic.estimateEditable && !!remaining && sameByRole(epic.demandByRole, remaining.remainingNowByRole)
  const atStartEqualsNow =
    !!remaining && sameByRole(remaining.remainingNowByRole, remaining.remainingAtQuarterStartByRole)

  // Stable role order for the remaining-work rows, union of both maps, ordered
  // by workflow config first (matching the demand row above).
  const orderedRemainingRoles: string[] = useMemo(() => {
    if (!remaining) return []
    const keys = new Set([
      ...Object.keys(remaining.remainingNowByRole || {}),
      ...Object.keys(remaining.remainingAtQuarterStartByRole || {}),
    ])
    const codes = getRoleCodes()
    const ordered: string[] = []
    codes.forEach(code => { if (keys.has(code)) ordered.push(code) })
    keys.forEach(code => { if (!codes.includes(code)) ordered.push(code) })
    return ordered
  }, [remaining, getRoleCodes])

  const moveAction = mode === 'backlog'
    ? { label: 'В квартал →', handler: () => onMove(epic.epicKey, targetQuarter) }
    : { label: '← Вернуть', handler: () => onMove(epic.epicKey, null) }

  // Role-chips row for remaining work — same chip styling as the demand row,
  // colored via getRoleColor (never hardcode role colors).
  const renderRemainingLine = (label: string, byRole: Record<string, number>) => {
    const totalDays = orderedRemainingRoles.reduce((sum, code) => sum + Math.round(byRole[code] || 0), 0)
    return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center', fontSize: 11 }}>
      <span style={{ color: TEXT_MUTED, fontWeight: 600 }}>{label}</span>
      {orderedRemainingRoles.map(code => {
        const val = byRole[code] || 0
        const color = getRoleColor(code)
        return (
          <div
            key={code}
            className="planning-role-chip epic-role-chip todo readonly"
            style={{ color, borderColor: lightenColor(color, 0.6) }}
          >
            <span className="epic-role-label">{code}</span>
            <span className="epic-role-value">{formatDays(val)}d</span>
          </div>
        )
      })}
      <span style={{ marginLeft: 'auto', color: TEXT_MUTED, fontWeight: 600 }}>
        Σ {totalDays}d
      </span>
    </div>
    )
  }

  return (
    <div
      className="planning-epic-card"
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        padding: 12,
        background: BG_PAGE,
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 8,
      }}
    >
      {/* Header row: icon + key/link + RICE + boost */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        <img
          src={iconUrl}
          alt={epic.typeName}
          width={16}
          height={16}
          style={{ flexShrink: 0, marginTop: 2 }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            {jiraBaseUrl ? (
              <a
                href={`${jiraBaseUrl}${epic.epicKey}`}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: LINK_COLOR, fontSize: 13, fontWeight: 600, textDecoration: 'none' }}
                onMouseEnter={e => (e.currentTarget.style.textDecoration = 'underline')}
                onMouseLeave={e => (e.currentTarget.style.textDecoration = 'none')}
              >
                {epic.epicKey}
              </a>
            ) : (
              <span style={{ color: LINK_COLOR, fontSize: 13, fontWeight: 600 }}>{epic.epicKey}</span>
            )}
            <RiceScoreBadge score={epic.riceScore} normalized={epic.priorityScore} />
            {/* Boost chip */}
            {editingBoost ? (
              <input
                ref={boostInputRef}
                type="number"
                value={boostDraft}
                min={-50}
                max={50}
                onChange={e => setBoostDraft(e.target.value)}
                onBlur={handleBoostSave}
                onKeyDown={handleBoostKey}
                style={{
                  width: 60,
                  padding: '2px 6px',
                  fontSize: 12,
                  fontWeight: 700,
                  border: `1px solid ${LINK_COLOR}`,
                  borderRadius: 3,
                  textAlign: 'center',
                }}
              />
            ) : (
              <button
                type="button"
                onClick={() => { setBoostDraft(String(epic.manualBoost)); setEditingBoost(true) }}
                title="Boost: -50..+50"
                style={{
                  cursor: 'pointer',
                  background: epic.manualBoost === 0 ? BG_SUBTLE : (epic.manualBoost > 0 ? SUCCESS_BG : ERROR_BG),
                  color: epic.manualBoost === 0 ? TEXT_MUTED : (epic.manualBoost > 0 ? SUCCESS_TEXT : ERROR_DARK_TEXT),
                  border: 'none',
                  borderRadius: 3,
                  padding: '2px 6px',
                  fontSize: 11,
                  fontWeight: 700,
                }}
              >
                Boost {formatBoost(epic.manualBoost)}
              </button>
            )}
            {epic.status && (
              <span style={{ marginLeft: 'auto', flexShrink: 0 }}>
                <StatusBadge status={epic.status} />
              </span>
            )}
          </div>
          <div
            title={epic.epicSummary}
            style={{
              color: TEXT_PRIMARY,
              fontSize: 13,
              fontWeight: 600,
              lineHeight: 1.35,
              marginTop: 4,
              wordBreak: 'break-word',
            }}
          >
            {epic.epicSummary}
          </div>
        </div>
      </div>

      {/* Project link (hidden for orphan epics and when the group header already names the project) */}
      {showProject && epic.projectKey && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: TEXT_SECONDARY }}>
          <span style={{ color: TEXT_MUTED }}>Project:</span>
          <a
            href={`/projects?project=${encodeURIComponent(epic.projectKey)}`}
            style={{ color: LINK_COLOR, textDecoration: 'none', fontWeight: 600 }}
            onMouseEnter={e => (e.currentTarget.style.textDecoration = 'underline')}
            onMouseLeave={e => (e.currentTarget.style.textDecoration = 'none')}
            title={epic.projectSummary ?? undefined}
          >
            {epic.projectKey}
          </a>
          {epic.projectSummary && (
            <span style={{ color: TEXT_MUTED, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {epic.projectSummary}
            </span>
          )}
        </div>
      )}

      {/* Roles estimate — Board-style chips, click-to-edit when the config allows it.
          Shown only while the epic is still in its estimation phase (server flag);
          once work starts the stale rough estimate hides and the card speaks in
          remaining work only. */}
      {epic.estimateEditable && orderedRoles.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
          {orderedRoles.map(code => (
            <PlanningRoleChip
              key={code}
              epicKey={epic.epicKey}
              role={code}
              days={epic.demandByRole[code] ?? null}
              roleColor={getRoleColor(code)}
              editable={estimateEditable}
              config={estimateConfig ?? null}
              onSave={onEstimateChange ?? (async () => {})}
            />
          ))}
          {epic.totalDemandDays > 0 && (
            <span style={{ marginLeft: 'auto', fontSize: 11, color: TEXT_MUTED, fontWeight: 600 }}>
              Σ {Math.round(epic.totalDemandDays)}d
            </span>
          )}
        </div>
      )}

      {/* F86: remaining-work section — needs-planning backlog epics with a computed remainder */}
      {showRemainingWork && remaining && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            <WarningBadge tone="warn">Осталась работа</WarningBadge>
          </div>
          {!remainingEqualsEstimate &&
            renderRemainingLine('Осталось сейчас:', remaining.remainingNowByRole)}
          {!atStartEqualsNow &&
            renderRemainingLine(`На старте ${currentQuarter}:`, remaining.remainingAtQuarterStartByRole)}
        </div>
      )}

      {/* Warning badges. Standalone and team-overload are deliberately NOT
          shown here: standalone epics already sit under the «Без проекта»
          group header, and overload is a team-level signal covered by the
          capacity bars above the columns. */}
      {((!epic.hasEstimate && !showRemainingWork && !estimateEditable)
        || !epic.hasTeamMapping
        || inOtherQuarter
        || pmDesiresDifferentQuarter) && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {/* Pencil chips already scream "no estimate" on editable epics */}
          {!epic.hasEstimate && !showRemainingWork && !estimateEditable && (
            <WarningBadge tone="warn">нет оценки</WarningBadge>
          )}
          {!epic.hasTeamMapping && (
            <WarningBadge tone="warn">нет команды</WarningBadge>
          )}
          {inOtherQuarter && epic.quarterLabel && (
            <WarningBadge tone="info">в {epic.quarterLabel}</WarningBadge>
          )}
          {pmDesiresDifferentQuarter && (
            <WarningBadge tone="info">PM желает {epic.projectDesiredQuarter}</WarningBadge>
          )}
        </div>
      )}

      {/* Move action */}
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <button
          type="button"
          onClick={moveAction.handler}
          className="planning-epic-move-btn"
          style={{
            padding: '6px 12px',
            border: `1px solid ${LINK_COLOR}`,
            borderRadius: 4,
            background: mode === 'backlog' ? LINK_COLOR : BG_PAGE,
            color: mode === 'backlog' ? BG_PAGE : LINK_COLOR,
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          {moveAction.label}
        </button>
      </div>
    </div>
  )
}

function WarningBadge({ tone, children }: { tone: 'warn' | 'info' | 'error' | 'neutral'; children: ReactNode }) {
  const map = {
    warn: { bg: WARNING_BG, color: WARNING_TEXT, border: WARNING_BORDER },
    info: { bg: INFO_BG, color: INFO_TEXT, border: INFO_BORDER },
    error: { bg: ERROR_BG, color: ERROR_TEXT, border: ERROR_BORDER },
    neutral: { bg: BG_SUBTLE, color: TEXT_SECONDARY, border: BORDER_DEFAULT },
  }[tone]
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '2px 8px',
        borderRadius: 3,
        fontSize: 11,
        fontWeight: 700,
        color: map.color,
        background: map.bg,
        border: `1px solid ${map.border}`,
      }}
    >
      {children}
    </span>
  )
}
