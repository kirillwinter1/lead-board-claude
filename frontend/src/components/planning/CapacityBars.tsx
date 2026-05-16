import { TeamBadge } from '../TeamBadge'
import { QuarterlyTeamOverviewDto } from '../../api/quarterlyPlanning'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  BG_SUBTLE,
  BG_PAGE,
  BORDER_DEFAULT,
  getUtilizationColor,
  DSR_GREEN,
  DSR_YELLOW,
  DSR_RED,
} from '../../constants/colors'

interface CapacityBarsProps {
  teams: QuarterlyTeamOverviewDto[]
}

// Status glyph thresholds MUST mirror getUtilizationColor() in constants/colors.ts:
//   green  85..110   -> ✓ ok
//   yellow 70..130   -> !  near limit
//   red    otherwise -> ‼ overloaded (distinct glyph from yellow for color-blind users)
function statusGlyph(utilization: number): { glyph: string; color: string; label: string } {
  if (utilization >= 85 && utilization <= 110) return { glyph: '✓', color: DSR_GREEN, label: 'ok' }
  if (utilization >= 70 && utilization <= 130) return { glyph: '!', color: DSR_YELLOW, label: 'near limit' }
  return { glyph: '‼', color: DSR_RED, label: 'overloaded' }
}

export function CapacityBars({ teams }: CapacityBarsProps) {
  if (teams.length === 0) {
    return (
      <div style={{
        padding: 12,
        background: BG_SUBTLE,
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 6,
        color: TEXT_MUTED,
        fontSize: 13,
      }}>
        No team capacity data available for this quarter.
      </div>
    )
  }

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 10,
        padding: 12,
        background: BG_PAGE,
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 6,
      }}
    >
      {teams.map(team => {
        const utilization = Math.round(team.utilization)
        const color = getUtilizationColor(utilization)
        const status = statusGlyph(utilization)
        return (
          <div key={team.teamId} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <TeamBadge name={team.teamName} color={team.teamColor} />
              </div>
              <span
                title={status.label}
                aria-label={`Status: ${status.label}`}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: status.color,
                  color: BG_PAGE,
                  fontSize: 11,
                  fontWeight: 800,
                }}
              >
                {status.glyph}
              </span>
            </div>
            <div
              style={{
                position: 'relative',
                height: 10,
                background: BG_SUBTLE,
                borderRadius: 999,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  left: 0,
                  top: 0,
                  bottom: 0,
                  width: `${Math.min(utilization, 100)}%`,
                  background: color,
                  borderRadius: 999,
                  transition: 'width 0.2s ease, background-color 0.2s ease',
                }}
              />
              {utilization > 100 && (
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: '100%',
                    background: `repeating-linear-gradient(45deg, ${DSR_RED}40 0 8px, transparent 8px 16px)`,
                    borderRadius: 999,
                    pointerEvents: 'none',
                  }}
                />
              )}
            </div>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: 11,
              color: TEXT_MUTED,
              fontWeight: 600,
            }}>
              <span style={{ color: TEXT_PRIMARY }}>
                {Math.round(team.demandDays)}/{Math.round(team.capacityDays)}d
              </span>
              <span style={{ color }}>{utilization}%</span>
            </div>
          </div>
        )
      })}
    </div>
  )
}
