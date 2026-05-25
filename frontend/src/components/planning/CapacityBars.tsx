import { useMemo } from 'react'
import { TeamBadge } from '../TeamBadge'
import { QuarterlyTeamOverviewDto } from '../../api/quarterlyPlanning'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
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
//   green  <80     -> ✓ ok
//   yellow 80..100 -> !  near limit
//   red    >100    -> ‼ overloaded (distinct glyph from yellow for color-blind users)
function statusGlyph(utilization: number): { glyph: string; color: string; label: string } {
  if (utilization < 80) return { glyph: '✓', color: DSR_GREEN, label: 'ok' }
  if (utilization <= 100) return { glyph: '!', color: DSR_YELLOW, label: 'near limit' }
  return { glyph: '‼', color: DSR_RED, label: 'overloaded' }
}

export function CapacityBars({ teams }: CapacityBarsProps) {
  const { getRoleCodes, getRoleColor } = useWorkflowConfig()

  // Stable union of roles present across all teams (capacity OR demand),
  // ordered by workflow config first, then any extras seen in data.
  const roleOrder = useMemo(() => {
    const configured = getRoleCodes()
    const present = new Set<string>()
    teams.forEach(t => {
      Object.keys(t.capacityByRole || {}).forEach(c => present.add(c))
      Object.keys(t.demandByRole || {}).forEach(c => present.add(c))
    })
    const ordered: string[] = []
    configured.forEach(c => { if (present.has(c)) ordered.push(c) })
    present.forEach(c => { if (!configured.includes(c)) ordered.push(c) })
    return ordered
  }, [teams, getRoleCodes])

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
            <RoleMiniBars team={team} roleOrder={roleOrder} getRoleColor={getRoleColor} />
          </div>
        )
      })}
    </div>
  )
}

interface RoleMiniBarsProps {
  team: QuarterlyTeamOverviewDto
  roleOrder: string[]
  getRoleColor: (code: string) => string
}

function RoleMiniBars({ team, roleOrder, getRoleColor }: RoleMiniBarsProps) {
  // Only render roles that this team actually has capacity or demand for —
  // hiding zero rows keeps the sticky header compact for teams with fewer roles.
  const rows = roleOrder.filter(code => {
    const cap = team.capacityByRole?.[code] || 0
    const dem = team.demandByRole?.[code] || 0
    return cap > 0 || dem > 0
  })
  if (rows.length === 0) return null
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3, marginTop: 2 }}>
      {rows.map(code => {
        const cap = team.capacityByRole?.[code] || 0
        const dem = team.demandByRole?.[code] || 0
        const pct = cap > 0 ? Math.round((dem / cap) * 100) : (dem > 0 ? 999 : 0)
        const baseColor = getRoleColor(code)
        // Mirror getUtilizationColor thresholds (<80 green, 80..100 yellow, >100 red).
        // Under-utilization keeps role colour (green = informational, not alarm).
        const barColor = pct > 100 || cap === 0 ? DSR_RED : pct >= 80 ? DSR_YELLOW : baseColor
        return (
          <div key={code} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10 }}>
            <span style={{
              minWidth: 28,
              fontWeight: 700,
              color: baseColor,
              fontVariantNumeric: 'tabular-nums',
            }}>
              {code}
            </span>
            <div style={{
              position: 'relative',
              flex: 1,
              height: 5,
              background: BG_SUBTLE,
              borderRadius: 999,
              overflow: 'hidden',
            }}>
              <div style={{
                position: 'absolute',
                left: 0,
                top: 0,
                bottom: 0,
                width: cap > 0 ? `${Math.min(pct, 100)}%` : (dem > 0 ? '100%' : '0%'),
                background: barColor,
                borderRadius: 999,
                transition: 'width 0.2s ease, background-color 0.2s ease',
              }} />
            </div>
            <span style={{
              minWidth: 56,
              textAlign: 'right',
              color: TEXT_MUTED,
              fontWeight: 600,
              fontVariantNumeric: 'tabular-nums',
            }}>
              {Math.round(dem)}/{Math.round(cap)}d
            </span>
            <span style={{
              minWidth: 32,
              textAlign: 'right',
              color: barColor,
              fontWeight: 700,
              fontVariantNumeric: 'tabular-nums',
            }}>
              {cap > 0 ? `${pct}%` : (dem > 0 ? '∞' : '—')}
            </span>
          </div>
        )
      })}
    </div>
  )
}
