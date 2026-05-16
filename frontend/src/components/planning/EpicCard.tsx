import { useState, useRef, useEffect, useMemo, KeyboardEvent, ReactNode } from 'react'
import { TeamBadge } from '../TeamBadge'
import { RiceScoreBadge } from '../rice/RiceScoreBadge'
import { getIssueIcon } from '../board/helpers'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { PlanningEpicDto, TeamRef } from '../../api/quarterlyPlanning'
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
  DSR_RED,
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
  teamsById: Map<number, Pick<TeamRef, 'id' | 'name' | 'color'>>
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

export function EpicCard({
  epic,
  mode,
  targetQuarter,
  currentQuarter,
  jiraBaseUrl,
  teamsById,
  onMove,
  onBoostChange,
}: EpicCardProps) {
  const { getIssueTypeIconUrl, getRoleColor, getRoleCodes } = useWorkflowConfig()
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

  const iconUrl = getIssueIcon(epic.typeName, epic.iconUrl ?? getIssueTypeIconUrl(epic.typeName))

  const overloadedTeamNames: string[] = epic.overloadedTeams
    .map(id => teamsById.get(id)?.name)
    .filter((n): n is string => Boolean(n))

  const overloads = mode === 'in-quarter' && overloadedTeamNames.length > 0
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

  // Build a stable order of roles based on workflow config
  const orderedRoles: string[] = useMemo(() => {
    const codes = getRoleCodes()
    const present = new Set(Object.keys(epic.demandByRole))
    const ordered: string[] = []
    codes.forEach(code => { if (present.has(code)) ordered.push(code) })
    present.forEach(code => { if (!codes.includes(code)) ordered.push(code) })
    return ordered.filter(code => (epic.demandByRole[code] || 0) > 0)
  }, [epic.demandByRole, getRoleCodes])

  const moveAction = mode === 'backlog'
    ? { label: 'В квартал →', handler: () => onMove(epic.epicKey, targetQuarter) }
    : { label: '← Вернуть', handler: () => onMove(epic.epicKey, null) }

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
        border: `1px solid ${overloads ? DSR_RED : BORDER_DEFAULT}`,
        borderLeft: overloads ? `4px solid ${DSR_RED}` : `1px solid ${BORDER_DEFAULT}`,
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

      {/* Project link (hidden for orphan epics without a parent project) */}
      {epic.projectKey && (
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

      {/* Teams */}
      {epic.teams.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {epic.teams.map(t => (
            <TeamBadge key={t.id} name={t.name} color={t.color} />
          ))}
        </div>
      )}

      {/* Roles demand */}
      {orderedRoles.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
          {orderedRoles.map(code => {
            const days = epic.demandByRole[code] || 0
            const color = getRoleColor(code)
            // Only call lightenColor for full 6-digit hex (#RRGGBB). For other
            // formats (3-digit hex `#abc`, 4-char fallback `#666`, rgb(...),
            // CSS vars), fall back to a neutral subtle background so the chip
            // remains visible — otherwise bg would equal text color.
            const bg = color.startsWith('#') && color.length === 7
              ? lightenColor(color, 0.92)
              : BG_SUBTLE
            return (
              <span
                key={code}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: '2px 8px',
                  borderRadius: 3,
                  fontSize: 11,
                  fontWeight: 700,
                  color,
                  background: bg,
                  borderLeft: `2px solid ${color}`,
                }}
              >
                {code} {days}d
              </span>
            )
          })}
          <span style={{ marginLeft: 'auto', fontSize: 11, color: TEXT_MUTED, fontWeight: 600 }}>
            Σ {Math.round(epic.totalDemandDays)}d
          </span>
        </div>
      )}

      {/* Warning badges */}
      {(!epic.hasEstimate
        || !epic.hasTeamMapping
        || inOtherQuarter
        || overloads
        || pmDesiresDifferentQuarter
        || epic.isStandalone) && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {!epic.hasEstimate && (
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
          {epic.isStandalone && (
            <WarningBadge tone="neutral">Standalone</WarningBadge>
          )}
          {overloads && (
            <WarningBadge tone="error">перегруз: {overloadedTeamNames.join(', ')}</WarningBadge>
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
