import { useState, useEffect, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { teamsApi, TeamMember, WorklogTimelineResponse, WorklogMember, WorklogDayInfo } from '../api/teams'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { ABSENCE_COLORS } from './AbsenceModal'
import { AbsenceType } from '../api/teams'
import './WorklogTimeline.css'

const DAY_WIDTH = 32
const ROW_HEIGHT = 40
const HEADER_HEIGHT = 48
const LEFT_PANEL_WIDTH = 360
const SEPARATOR_HEIGHT = 28

const ABSENCE_SHORT: Record<string, string> = {
  VACATION: 'VAC',
  SICK_LEAVE: 'SICK',
  DAY_OFF: 'OFF',
  OTHER: 'OTH',
}

function toDateStr(d: Date): string {
  return d.toISOString().slice(0, 10)
}

function getRatioClass(ratio: number): string {
  if (ratio >= 90) return 'good'
  if (ratio >= 70) return 'warning'
  return 'danger'
}

interface RoleGroup {
  role: string
  members: WorklogMember[]
}

function groupByRole(members: WorklogMember[]): RoleGroup[] {
  const groups: RoleGroup[] = []
  let currentRole: string | null = null
  let currentMembers: WorklogMember[] = []

  for (const m of members) {
    if (m.role !== currentRole) {
      if (currentRole !== null && currentMembers.length > 0) {
        groups.push({ role: currentRole, members: currentMembers })
      }
      currentRole = m.role
      currentMembers = [m]
    } else {
      currentMembers.push(m)
    }
  }

  if (currentRole !== null && currentMembers.length > 0) {
    groups.push({ role: currentRole, members: currentMembers })
  }

  return groups
}

interface WorklogTimelineProps {
  teamId: number
  members?: TeamMember[]
  teamColor?: string | null
  from?: string
  to?: string
}

export function WorklogTimeline({ teamId, from: propFrom, to: propTo }: WorklogTimelineProps) {
  const { getRoleColor, getRoleDisplayName } = useWorkflowConfig()
  const [data, setData] = useState<WorklogTimelineResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Date range: use props if provided, else last 30 days
  const { from, to } = useMemo(() => {
    if (propFrom && propTo) return { from: propFrom, to: propTo }
    const now = new Date()
    now.setHours(0, 0, 0, 0)
    const f = new Date(now)
    f.setDate(f.getDate() - 29) // 30 days including today
    return { from: toDateStr(f), to: toDateStr(now) }
  }, [propFrom, propTo])

  useEffect(() => {
    setLoading(true)
    setError(null)
    teamsApi.getWorklogTimeline(teamId, from, to)
      .then(setData)
      .catch(err => {
        console.error('Failed to load worklog timeline:', err)
        setError('Failed to load worklog data')
      })
      .finally(() => setLoading(false))
  }, [teamId, from, to])

  const todayStr = useMemo(() => toDateStr(new Date()), [])

  const roleGroups = useMemo(() => {
    if (!data) return []
    return groupByRole(data.members)
  }, [data])

  // Month labels for header
  const monthLabels = useMemo(() => {
    if (!data) return []
    const labels: { text: string; startIdx: number; count: number }[] = []
    let currentMonth = ''
    let startIdx = 0

    for (let i = 0; i < data.days.length; i++) {
      const d = new Date(data.days[i].date + 'T00:00:00')
      const month = d.toLocaleDateString('en-US', { month: 'short' })
      if (month !== currentMonth) {
        if (currentMonth) {
          labels.push({ text: currentMonth, startIdx, count: i - startIdx })
        }
        currentMonth = month
        startIdx = i
      }
    }
    if (currentMonth) {
      labels.push({ text: currentMonth, startIdx, count: data.days.length - startIdx })
    }
    return labels
  }, [data])

  if (loading) {
    return <div className="worklog-loading">Loading worklog data...</div>
  }

  if (error) {
    return <div className="worklog-error">{error}</div>
  }

  if (!data || data.members.length === 0) {
    return <div className="worklog-empty">No worklog data available for this period.</div>
  }

  const days = data.days
  const gridWidth = days.length * DAY_WIDTH

  const renderMemberLeftPanel = (member: WorklogMember) => {
    const roleColor = getRoleColor(member.role)
    const ratioPercent = Math.round(member.summary.ratio)
    const ratioClass = getRatioClass(ratioPercent)

    return (
      <div
        key={`left-${member.memberId}`}
        className="worklog-member-row"
        style={{ height: ROW_HEIGHT }}
      >
        <div className="worklog-member-info">
          {member.avatarUrl ? (
            <img
              src={member.avatarUrl}
              alt=""
              className="worklog-member-avatar"
            />
          ) : (
            <span
              className="worklog-member-avatar-placeholder"
              style={{
                backgroundColor: roleColor + '20',
                color: roleColor,
                borderColor: roleColor,
              }}
            >
              {(member.displayName || '?')[0].toUpperCase()}
            </span>
          )}
          <Link
            to={`/teams/${teamId}/member/${member.memberId}`}
            className="worklog-member-name worklog-member-link"
          >
            {member.displayName || 'Unknown'}
          </Link>
          <span
            className="worklog-role-badge"
            style={{
              backgroundColor: roleColor + '20',
              color: roleColor,
            }}
          >
            {getRoleDisplayName(member.role)}
          </span>
        </div>
        <div className="worklog-summary">
          <span className="worklog-summary-hours">
            {member.summary.totalLogged.toFixed(0)}/{member.summary.capacityHours.toFixed(0)}h
          </span>
          <span className={`worklog-ratio ${ratioClass}`}>
            {ratioPercent}%
          </span>
        </div>
      </div>
    )
  }

  const renderMemberRow = (member: WorklogMember, daysInfo: WorklogDayInfo[]) => {
    // Build a lookup for entries by date
    const entryMap = new Map(member.entries.map(e => [e.date, e]))

    return (
      <div
        key={`right-${member.memberId}`}
        className="worklog-timeline-row"
        style={{ height: ROW_HEIGHT }}
      >
        {daysInfo.map((day, i) => {
          const entry = entryMap.get(day.date)
          const isToday = day.date === todayStr
          const isWeekend = day.dayType === 'WEEKEND'
          const isHoliday = day.dayType === 'HOLIDAY'

          // Check for absence
          if (entry?.absenceType) {
            const absenceColor = ABSENCE_COLORS[entry.absenceType as AbsenceType] || ABSENCE_COLORS.OTHER
            const shortLabel = ABSENCE_SHORT[entry.absenceType] || entry.absenceType.slice(0, 3)
            return (
              <div
                key={i}
                className={`worklog-cell absence-cell ${isToday ? 'today' : ''}`}
                style={{
                  width: DAY_WIDTH,
                  height: ROW_HEIGHT,
                  backgroundColor: absenceColor,
                }}
                title={`${entry.absenceType.replace('_', ' ')}`}
              >
                {shortLabel}
              </div>
            )
          }

          const hours = entry?.hoursLogged
          const hasHours = hours !== null && hours !== undefined && hours > 0
          const isLowHours = hasHours && hours < member.hoursPerDay * 0.5 && !isWeekend && !isHoliday

          const cellClasses = [
            'worklog-cell',
            isWeekend ? 'weekend' : '',
            isHoliday ? 'holiday' : '',
            isToday ? 'today' : '',
            hasHours ? 'has-hours' : '',
            isLowHours ? 'low-hours' : '',
          ].filter(Boolean).join(' ')

          // Format hours: show integer if whole, one decimal otherwise
          let hoursLabel = ''
          if (hasHours) {
            hoursLabel = hours % 1 === 0 ? hours.toFixed(0) : hours.toFixed(1)
          }

          return (
            <div
              key={i}
              className={cellClasses}
              style={{ width: DAY_WIDTH, height: ROW_HEIGHT }}
              title={hasHours ? `${hoursLabel}h logged on ${day.date}` : undefined}
            >
              {hoursLabel}
            </div>
          )
        })}
      </div>
    )
  }

  return (
    <div className="worklog-timeline">
      <div className="worklog-timeline-grid">
        {/* Left panel */}
        <div className="worklog-timeline-left" style={{ width: LEFT_PANEL_WIDTH }}>
          <div className="worklog-timeline-left-header" style={{ height: HEADER_HEIGHT }}>
            <span>Member</span>
            <span className="header-summary">Logged / Capacity</span>
          </div>
          {roleGroups.map(group => (
            <div key={`left-group-${group.role}`}>
              <div
                className="worklog-role-separator"
                style={{
                  height: SEPARATOR_HEIGHT,
                  backgroundColor: getRoleColor(group.role),
                }}
              >
                {getRoleDisplayName(group.role)}
              </div>
              {group.members.map(m => renderMemberLeftPanel(m))}
            </div>
          ))}
        </div>

        {/* Right panel (scrollable) */}
        <div className="worklog-timeline-right">
          <div style={{ minWidth: gridWidth, position: 'relative' }}>
            {/* Header: month labels + day numbers */}
            <div className="worklog-timeline-header" style={{ height: HEADER_HEIGHT }}>
              {/* Month row */}
              <div className="worklog-timeline-months">
                {monthLabels.map((ml, i) => (
                  <div
                    key={i}
                    className="worklog-month-label"
                    style={{
                      left: ml.startIdx * DAY_WIDTH,
                      width: ml.count * DAY_WIDTH,
                    }}
                  >
                    {ml.text}
                  </div>
                ))}
              </div>
              {/* Day number row */}
              <div className="worklog-timeline-days">
                {days.map((day, i) => {
                  const d = new Date(day.date + 'T00:00:00')
                  const isToday = day.date === todayStr
                  const isWeekend = day.dayType === 'WEEKEND'
                  const isHoliday = day.dayType === 'HOLIDAY'
                  return (
                    <div
                      key={i}
                      className={`worklog-day-header ${isWeekend ? 'weekend' : ''} ${isHoliday ? 'holiday' : ''} ${isToday ? 'today' : ''}`}
                      style={{ width: DAY_WIDTH }}
                    >
                      {d.getDate()}
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Rows grouped by role */}
            {roleGroups.map(group => (
              <div key={`right-group-${group.role}`}>
                <div
                  className="worklog-role-separator-right"
                  style={{
                    height: SEPARATOR_HEIGHT,
                    backgroundColor: getRoleColor(group.role) + '15',
                  }}
                />
                {group.members.map(m => renderMemberRow(m, days))}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="worklog-legend">
        <span className="worklog-legend-item">
          <span className="worklog-legend-swatch" style={{ backgroundColor: '#f9fafb', border: '1px solid #dfe1e6' }} />
          Weekend
        </span>
        <span className="worklog-legend-item">
          <span className="worklog-legend-swatch" style={{ backgroundColor: '#fff3e6', border: '1px solid #ffe0b2' }} />
          Holiday
        </span>
        {Object.entries(ABSENCE_COLORS).map(([type, color]) => (
          <span key={type} className="worklog-legend-item">
            <span className="worklog-legend-swatch" style={{ backgroundColor: color }} />
            {ABSENCE_SHORT[type] || type}
          </span>
        ))}
        <span className="worklog-legend-item">
          <span className="worklog-legend-swatch" style={{ backgroundColor: '#DE350B' }} />
          Low hours (&lt;50%)
        </span>
      </div>
    </div>
  )
}
