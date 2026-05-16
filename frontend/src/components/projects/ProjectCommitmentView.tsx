import { TeamBadge } from '../TeamBadge'
import { ProjectQuarterCommitmentDto, TeamCommitmentDto } from '../../api/quarterlyPlanning'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  TEXT_SECONDARY,
  BG_SUBTLE,
  BORDER_DEFAULT,
  PROGRESS_TRACK,
  DSR_GREEN,
  DSR_YELLOW,
  DSR_RED,
} from '../../constants/colors'

interface ProjectCommitmentViewProps {
  commitment: ProjectQuarterCommitmentDto
}

type CommitmentStatus = 'all' | 'partial' | 'none' | 'empty'

function classify(team: TeamCommitmentDto): CommitmentStatus {
  if (team.totalEpics === 0) return 'empty'
  if (team.committedEpics === team.totalEpics) return 'all'
  if (team.committedEpics === 0) return 'none'
  return 'partial'
}

const STATUS_COLOR: Record<CommitmentStatus, string> = {
  all: DSR_GREEN,
  partial: DSR_YELLOW,
  none: DSR_RED,
  empty: TEXT_MUTED,
}

const STATUS_ICON: Record<CommitmentStatus, string> = {
  all: '✓',
  partial: '⚠',
  none: '✗',
  empty: '—',
}

function pluralEpics(n: number): string {
  // ru pluralization: 1 эпик / 2-4 эпика / 5+ эпиков. Numbers ending 11..14 are
  // always genitive plural, otherwise last digit rules apply.
  const mod100 = n % 100
  const mod10 = n % 10
  if (mod100 >= 11 && mod100 <= 14) return 'эпиков'
  if (mod10 === 1) return 'эпик'
  if (mod10 >= 2 && mod10 <= 4) return 'эпика'
  return 'эпиков'
}

/**
 * F70 — PM-facing aggregate of a project's commitment by team.
 *
 * Renders one row per team that owns at least one of the project's epics:
 * a progress bar, the committed/other-quarter/uncommitted breakdown, and a
 * status icon coloured by the DSR palette (green/yellow/red).
 *
 * Layout is deliberately compact so the view can sit inside an existing
 * expanded project card without adding visual weight. We avoid a separate CSS
 * file: styles are inline so the component is self-contained and reusable.
 */
export function ProjectCommitmentView({ commitment }: ProjectCommitmentViewProps) {
  const teams = commitment.commitmentByTeam

  if (teams.length === 0) {
    return (
      <div
        style={{
          padding: 12,
          background: BG_SUBTLE,
          border: `1px solid ${BORDER_DEFAULT}`,
          borderRadius: 6,
          color: TEXT_MUTED,
          fontSize: 13,
          textAlign: 'center',
        }}
      >
        {commitment.desiredQuarter
          ? `У проекта нет эпиков с привязанными командами для ${commitment.desiredQuarter}.`
          : 'Заявленный квартал не установлен — нет данных по командам.'}
      </div>
    )
  }

  // Order: by remaining ("uncommitted + otherQuarter") descending, so the team
  // that needs the PM's attention bubbles to the top.
  const ordered = [...teams].sort((a, b) => {
    const aGap = a.uncommittedEpics + a.otherQuarterEpics
    const bGap = b.uncommittedEpics + b.otherQuarterEpics
    return bGap - aGap
  })

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        padding: 12,
        background: BG_SUBTLE,
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 6,
      }}
    >
      <div style={{ fontSize: 12, color: TEXT_MUTED, fontWeight: 600 }}>
        Commitment{commitment.desiredQuarter ? ` · ${commitment.desiredQuarter}` : ''}
      </div>
      {ordered.map(team => {
        const status = classify(team)
        const color = STATUS_COLOR[status]
        const icon = STATUS_ICON[status]
        const percent = team.totalEpics > 0
          ? Math.round((team.committedEpics / team.totalEpics) * 100)
          : 0
        return (
          <div
            key={team.teamId}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '6px 0',
            }}
          >
            <div style={{ minWidth: 0, flex: '0 1 180px' }}>
              <TeamBadge name={team.teamName} color={team.teamColor} />
            </div>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={team.totalEpics}
                aria-valuenow={team.committedEpics}
                aria-label={`${team.teamName}: ${team.committedEpics} of ${team.totalEpics} epics committed`}
                style={{
                  width: '100%',
                  height: 6,
                  background: PROGRESS_TRACK,
                  borderRadius: 3,
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${percent}%`,
                    minWidth: percent > 0 ? 2 : 0,
                    height: '100%',
                    background: color,
                    transition: 'width 0.15s ease',
                  }}
                />
              </div>
              <div style={{ fontSize: 12, color: TEXT_SECONDARY, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                <strong style={{ color: TEXT_PRIMARY }}>
                  {team.committedEpics}/{team.totalEpics} {pluralEpics(team.totalEpics)}
                </strong>
                {team.otherQuarterEpics > 0 && (
                  <span style={{ color: TEXT_MUTED }}>
                    · {team.otherQuarterEpics} в другом квартале
                  </span>
                )}
                {team.uncommittedEpics > 0 && (
                  <span style={{ color: TEXT_MUTED }}>
                    · {team.uncommittedEpics} без коммита
                  </span>
                )}
              </div>
            </div>
            <div
              aria-label={`Status: ${status}`}
              style={{
                flexShrink: 0,
                width: 24,
                textAlign: 'center',
                fontSize: 16,
                fontWeight: 700,
                color,
              }}
            >
              {icon}
            </div>
          </div>
        )
      })}
    </div>
  )
}
