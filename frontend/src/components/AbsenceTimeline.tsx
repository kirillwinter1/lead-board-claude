import { useState, useEffect, useMemo, useCallback } from 'react'
import { teamsApi, Absence, TeamMember, CreateAbsenceRequest } from '../api/teams'
import { AbsenceModal, ABSENCE_TYPE_LABELS, ABSENCE_COLORS } from './AbsenceModal'
import './AbsenceTimeline.css'

const DAY_WIDTH = 32
const ROW_HEIGHT = 40
const HEADER_HEIGHT = 48
const LEFT_PANEL_WIDTH = 260
const DAYS_VISIBLE = 28

function formatDateShort(d: Date): string {
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

function toDateStr(d: Date): string {
  return d.toISOString().slice(0, 10)
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86400000)
}

function isWeekend(d: Date): boolean {
  const day = d.getDay()
  return day === 0 || day === 6
}

function addDays(d: Date, n: number): Date {
  const result = new Date(d)
  result.setDate(result.getDate() + n)
  return result
}

interface AbsenceTimelineProps {
  teamId: number
  members: TeamMember[]
  teamColor?: string | null
  canManage: boolean
}

export function AbsenceTimeline({ teamId, members, teamColor, canManage }: AbsenceTimelineProps) {
  const [absences, setAbsences] = useState<Absence[]>([])
  const [loading, setLoading] = useState(true)
  const [startDate, setStartDate] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() - 3) // Start 3 days before today
    d.setHours(0, 0, 0, 0)
    return d
  })

  // Modal state
  const [modalOpen, setModalOpen] = useState(false)
  const [editingAbsence, setEditingAbsence] = useState<Absence | null>(null)
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null)

  // Tooltip state
  const [tooltip, setTooltip] = useState<{ x: number; y: number; absence: Absence } | null>(null)

  const endDate = useMemo(() => addDays(startDate, DAYS_VISIBLE - 1), [startDate])

  const fetchAbsences = useCallback(() => {
    setLoading(true)
    teamsApi.getTeamAbsences(teamId, toDateStr(startDate), toDateStr(endDate))
      .then(setAbsences)
      .catch(err => {
        console.error('Failed to load absences:', err)
        setAbsences([])
      })
      .finally(() => setLoading(false))
  }, [teamId, startDate, endDate])

  useEffect(() => {
    fetchAbsences()
  }, [fetchAbsences])

  const days = useMemo(() => {
    const result: Date[] = []
    for (let i = 0; i < DAYS_VISIBLE; i++) {
      result.push(addDays(startDate, i))
    }
    return result
  }, [startDate])

  const today = useMemo(() => {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    return d
  }, [])

  const todayOffset = useMemo(() => {
    const diff = daysBetween(startDate, today)
    if (diff < 0 || diff >= DAYS_VISIBLE) return null
    return diff * DAY_WIDTH + DAY_WIDTH / 2
  }, [startDate, today])

  const navigate = (direction: number) => {
    setStartDate(prev => addDays(prev, direction * 7))
  }

  const goToday = () => {
    const d = new Date()
    d.setDate(d.getDate() - 3)
    d.setHours(0, 0, 0, 0)
    setStartDate(d)
  }

  const handleAddAbsence = (memberId: number) => {
    setEditingAbsence(null)
    setSelectedMemberId(memberId)
    setModalOpen(true)
  }

  const handleClickAbsence = (absence: Absence) => {
    if (!canManage) return
    setEditingAbsence(absence)
    setSelectedMemberId(absence.memberId)
    setModalOpen(true)
  }

  const handleSave = async (data: CreateAbsenceRequest) => {
    if (!selectedMemberId) return
    if (editingAbsence) {
      await teamsApi.updateAbsence(teamId, selectedMemberId, editingAbsence.id, data)
    } else {
      await teamsApi.createAbsence(teamId, selectedMemberId, data)
    }
    fetchAbsences()
  }

  const handleDelete = async () => {
    if (!editingAbsence || !selectedMemberId) return
    await teamsApi.deleteAbsence(teamId, selectedMemberId, editingAbsence.id)
    fetchAbsences()
  }

  // Group absences by memberId
  const absencesByMember = useMemo(() => {
    const map = new Map<number, Absence[]>()
    for (const a of absences) {
      const list = map.get(a.memberId) || []
      list.push(a)
      map.set(a.memberId, list)
    }
    return map
  }, [absences])

  // Month labels for header
  const monthLabels = useMemo(() => {
    const labels: { text: string; startIdx: number; count: number }[] = []
    let currentMonth = ''
    let startIdx = 0

    for (let i = 0; i < days.length; i++) {
      const month = days[i].toLocaleDateString('ru-RU', { month: 'short' })
      if (month !== currentMonth) {
        if (currentMonth) {
          labels.push({ text: currentMonth, startIdx, count: i - startIdx })
        }
        currentMonth = month
        startIdx = i
      }
    }
    if (currentMonth) {
      labels.push({ text: currentMonth, startIdx, count: days.length - startIdx })
    }
    return labels
  }, [days])

  return (
    <div className="absence-timeline">
      {/* Navigation */}
      <div className="absence-timeline-nav">
        <button className="btn btn-small btn-secondary" onClick={() => navigate(-1)}>&larr;</button>
        <button className="btn btn-small btn-secondary" onClick={goToday}>Сегодня</button>
        <button className="btn btn-small btn-secondary" onClick={() => navigate(1)}>&rarr;</button>
        <span className="absence-timeline-range">
          {formatDateShort(startDate)} — {formatDateShort(endDate)}
        </span>
      </div>

      <div className="absence-timeline-grid">
        {/* Left panel */}
        <div className="absence-timeline-left" style={{ width: LEFT_PANEL_WIDTH }}>
          <div className="absence-timeline-left-header" style={{ height: HEADER_HEIGHT }}>
            Участник
          </div>
          {members.map(m => (
            <div key={m.id} className="absence-timeline-member" style={{ height: ROW_HEIGHT }}>
              <div className="absence-member-info">
                {m.avatarUrl ? (
                  <img
                    src={m.avatarUrl}
                    alt=""
                    className="absence-member-avatar"
                    style={{ borderColor: teamColor || '#ddd' }}
                  />
                ) : (
                  <span
                    className="absence-member-avatar-placeholder"
                    style={{
                      borderColor: teamColor || '#ddd',
                      backgroundColor: teamColor ? teamColor + '20' : '#f0f0f0',
                      color: teamColor || '#666',
                    }}
                  >
                    {(m.displayName || '?')[0].toUpperCase()}
                  </span>
                )}
                <span className="absence-member-name">{m.displayName || m.jiraAccountId}</span>
              </div>
              {canManage && (
                <button
                  className="absence-add-btn"
                  onClick={() => handleAddAbsence(m.id)}
                  title="Добавить отсутствие"
                >
                  +
                </button>
              )}
            </div>
          ))}
        </div>

        {/* Right panel (scrollable) */}
        <div className="absence-timeline-right">
          <div style={{ minWidth: DAYS_VISIBLE * DAY_WIDTH, position: 'relative' }}>
            {/* Header: month labels + day numbers */}
            <div className="absence-timeline-header" style={{ height: HEADER_HEIGHT }}>
              {/* Month row */}
              <div className="absence-timeline-months">
                {monthLabels.map((ml, i) => (
                  <div
                    key={i}
                    className="absence-month-label"
                    style={{
                      left: ml.startIdx * DAY_WIDTH,
                      width: ml.count * DAY_WIDTH,
                    }}
                  >
                    {ml.text}
                  </div>
                ))}
              </div>
              {/* Day row */}
              <div className="absence-timeline-days">
                {days.map((d, i) => {
                  const isToday = d.getTime() === today.getTime()
                  return (
                    <div
                      key={i}
                      className={`absence-day-header ${isWeekend(d) ? 'weekend' : ''} ${isToday ? 'today' : ''}`}
                      style={{ width: DAY_WIDTH }}
                    >
                      {d.getDate()}
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Rows */}
            {members.map(m => {
              const memberAbsences = absencesByMember.get(m.id) || []
              return (
                <div key={m.id} className="absence-timeline-row" style={{ height: ROW_HEIGHT }}>
                  {/* Weekend backgrounds */}
                  {days.map((d, i) => (
                    isWeekend(d) && (
                      <div
                        key={`bg-${i}`}
                        className="absence-day-bg weekend"
                        style={{ left: i * DAY_WIDTH, width: DAY_WIDTH, height: ROW_HEIGHT }}
                      />
                    )
                  ))}

                  {/* Absence bars */}
                  {memberAbsences.map(a => {
                    const aStart = new Date(a.startDate + 'T00:00:00Z')
                    const aEnd = new Date(a.endDate + 'T00:00:00Z')
                    const barStart = Math.max(0, daysBetween(startDate, aStart))
                    const barEnd = Math.min(DAYS_VISIBLE - 1, daysBetween(startDate, aEnd))
                    if (barEnd < 0 || barStart >= DAYS_VISIBLE) return null
                    const left = barStart * DAY_WIDTH + 2
                    const width = (barEnd - barStart + 1) * DAY_WIDTH - 4

                    return (
                      <div
                        key={a.id}
                        className={`absence-bar ${canManage ? 'clickable' : ''}`}
                        style={{
                          left,
                          width,
                          backgroundColor: ABSENCE_COLORS[a.absenceType] || ABSENCE_COLORS.OTHER,
                        }}
                        onClick={() => handleClickAbsence(a)}
                        onMouseEnter={e => {
                          const rect = e.currentTarget.getBoundingClientRect()
                          setTooltip({ x: rect.left + rect.width / 2, y: rect.top - 8, absence: a })
                        }}
                        onMouseLeave={() => setTooltip(null)}
                      />
                    )
                  })}
                </div>
              )
            })}

            {/* Today line */}
            {todayOffset !== null && (
              <div
                className="absence-today-line"
                style={{ left: todayOffset, top: 0, height: HEADER_HEIGHT + members.length * ROW_HEIGHT }}
              />
            )}
          </div>
        </div>
      </div>

      {/* Tooltip */}
      {tooltip && (
        <div
          className="absence-tooltip"
          style={{ left: tooltip.x, top: tooltip.y }}
        >
          <div className="absence-tooltip-type" style={{ color: ABSENCE_COLORS[tooltip.absence.absenceType] }}>
            {ABSENCE_TYPE_LABELS[tooltip.absence.absenceType]}
          </div>
          <div className="absence-tooltip-dates">
            {formatDateShort(new Date(tooltip.absence.startDate + 'T00:00:00Z'))} — {formatDateShort(new Date(tooltip.absence.endDate + 'T00:00:00Z'))}
          </div>
          {tooltip.absence.comment && (
            <div className="absence-tooltip-comment">{tooltip.absence.comment}</div>
          )}
        </div>
      )}

      {/* Legend */}
      <div className="absence-legend">
        {Object.entries(ABSENCE_COLORS).map(([type, color]) => (
          <span key={type} className="absence-legend-item">
            <span className="absence-legend-dot" style={{ backgroundColor: color }} />
            {ABSENCE_TYPE_LABELS[type as keyof typeof ABSENCE_TYPE_LABELS]}
          </span>
        ))}
      </div>

      {/* Modal */}
      <AbsenceModal
        isOpen={modalOpen}
        onClose={() => { setModalOpen(false); setEditingAbsence(null) }}
        onSave={handleSave}
        onDelete={editingAbsence ? handleDelete : undefined}
        absence={editingAbsence}
      />

      {loading && <div className="absence-loading">Загрузка...</div>}
    </div>
  )
}
