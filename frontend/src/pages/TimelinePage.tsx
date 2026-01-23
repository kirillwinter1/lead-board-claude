import { useState, useEffect, useMemo } from 'react'
import { teamsApi, Team } from '../api/teams'
import { getForecast, ForecastResponse, EpicForecast, PhaseInfo } from '../api/forecast'

type ZoomLevel = 'day' | 'week' | 'month'

interface DateRange {
  start: Date
  end: Date
}

function parseDate(dateStr: string | null): Date | null {
  if (!dateStr) return null
  return new Date(dateStr)
}

function formatDateShort(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function formatDateFull(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function daysBetween(start: Date, end: Date): number {
  const diffTime = end.getTime() - start.getTime()
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24))
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

function startOfWeek(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  d.setDate(diff)
  return d
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function getConfidenceOpacity(confidence: string): number {
  switch (confidence) {
    case 'HIGH': return 1.0
    case 'MEDIUM': return 0.7
    case 'LOW': return 0.4
    default: return 1.0
  }
}

function calculateDateRange(forecast: ForecastResponse): DateRange {
  let minDate: Date | null = null
  let maxDate: Date | null = null

  for (const epic of forecast.epics) {
    const phases = [epic.phaseSchedule.sa, epic.phaseSchedule.dev, epic.phaseSchedule.qa]
    for (const phase of phases) {
      if (phase.startDate) {
        const start = new Date(phase.startDate)
        if (!minDate || start < minDate) minDate = start
      }
      if (phase.endDate) {
        const end = new Date(phase.endDate)
        if (!maxDate || end > maxDate) maxDate = end
      }
    }
    if (epic.dueDate) {
      const due = new Date(epic.dueDate)
      if (!maxDate || due > maxDate) maxDate = due
    }
  }

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  if (!minDate) minDate = today
  if (!maxDate) maxDate = addDays(today, 30)

  // Add padding
  minDate = addDays(minDate, -3)
  maxDate = addDays(maxDate, 3)

  return { start: minDate, end: maxDate }
}

function generateTimelineHeaders(range: DateRange, zoom: ZoomLevel): { date: Date; label: string }[] {
  const headers: { date: Date; label: string }[] = []
  let current = new Date(range.start)

  if (zoom === 'day') {
    while (current <= range.end) {
      headers.push({ date: new Date(current), label: current.getDate().toString() })
      current = addDays(current, 1)
    }
  } else if (zoom === 'week') {
    current = startOfWeek(current)
    while (current <= range.end) {
      headers.push({ date: new Date(current), label: formatDateShort(current) })
      current = addDays(current, 7)
    }
  } else {
    current = startOfMonth(current)
    while (current <= range.end) {
      headers.push({
        date: new Date(current),
        label: current.toLocaleDateString('en-US', { month: 'short' })
      })
      current = new Date(current.getFullYear(), current.getMonth() + 1, 1)
    }
  }

  return headers
}

interface GanttBarProps {
  phase: PhaseInfo
  role: 'sa' | 'dev' | 'qa'
  rangeStart: Date
  totalDays: number
  confidence: string
}

function GanttBar({ phase, role, rangeStart, totalDays, confidence }: GanttBarProps) {
  if (!phase.startDate || !phase.endDate) return null

  const startDate = new Date(phase.startDate)
  const endDate = new Date(phase.endDate)
  const startOffset = daysBetween(rangeStart, startDate)
  const duration = daysBetween(startDate, endDate) + 1

  const leftPercent = (startOffset / totalDays) * 100
  const widthPercent = (duration / totalDays) * 100

  const roleLabels = { sa: 'SA', dev: 'DEV', qa: 'QA' }
  const opacity = getConfidenceOpacity(confidence)
  const noCapacity = phase.noCapacity === true

  const title = noCapacity
    ? `${roleLabels[role]}: ${formatDateFull(startDate)} - ${formatDateFull(endDate)} (${phase.workDays} work days) - НЕТ РЕСУРСОВ!`
    : `${roleLabels[role]}: ${formatDateFull(startDate)} - ${formatDateFull(endDate)} (${phase.workDays} work days)`

  return (
    <div
      className={`gantt-bar gantt-bar-${role} ${noCapacity ? 'gantt-bar-no-capacity' : ''}`}
      style={{ left: `${leftPercent}%`, width: `${Math.max(widthPercent, 0.5)}%`, opacity }}
      title={title}
    >
      <span className="gantt-bar-label">{noCapacity ? '!' : roleLabels[role]}</span>
    </div>
  )
}

interface GanttRowProps {
  epic: EpicForecast
  rangeStart: Date
  totalDays: number
}

function GanttRow({ epic, rangeStart, totalDays }: GanttRowProps) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayOffset = daysBetween(rangeStart, today)
  const todayPercent = (todayOffset / totalDays) * 100

  const dueDate = parseDate(epic.dueDate)
  const dueDateOffset = dueDate ? daysBetween(rangeStart, dueDate) : null
  const dueDatePercent = dueDateOffset !== null ? (dueDateOffset / totalDays) * 100 : null

  return (
    <div className="gantt-row">
      <div className="gantt-row-content">
        {/* Today line */}
        {todayPercent >= 0 && todayPercent <= 100 && (
          <div
            className="gantt-today-line"
            style={{ left: `${todayPercent}%` }}
          />
        )}

        {/* Due date line */}
        {dueDatePercent !== null && dueDatePercent >= 0 && dueDatePercent <= 100 && (
          <div
            className="gantt-due-line"
            style={{ left: `${dueDatePercent}%` }}
            title={`Due: ${formatDateFull(dueDate!)}`}
          />
        )}

        {/* Phase bars */}
        <GanttBar
          phase={epic.phaseSchedule.sa}
          role="sa"
          rangeStart={rangeStart}
          totalDays={totalDays}
          confidence={epic.confidence}
        />
        <GanttBar
          phase={epic.phaseSchedule.dev}
          role="dev"
          rangeStart={rangeStart}
          totalDays={totalDays}
          confidence={epic.confidence}
        />
        <GanttBar
          phase={epic.phaseSchedule.qa}
          role="qa"
          rangeStart={rangeStart}
          totalDays={totalDays}
          confidence={epic.confidence}
        />
      </div>
    </div>
  )
}

export function TimelinePage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [forecast, setForecast] = useState<ForecastResponse | null>(null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    teamsApi.getAll()
      .then(data => {
        setTeams(data.filter(t => t.active))
        if (data.length > 0 && !selectedTeamId) {
          setSelectedTeamId(data[0].id)
        }
      })
      .catch(err => setError('Failed to load teams: ' + err.message))
  }, [])

  useEffect(() => {
    if (!selectedTeamId) return

    setLoading(true)
    setError(null)
    getForecast(selectedTeamId)
      .then(data => {
        setForecast(data)
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load forecast: ' + err.message)
        setLoading(false)
      })
  }, [selectedTeamId])

  const dateRange = useMemo(() => {
    if (!forecast) return null
    return calculateDateRange(forecast)
  }, [forecast])

  const headers = useMemo(() => {
    if (!dateRange) return []
    return generateTimelineHeaders(dateRange, zoom)
  }, [dateRange, zoom])

  const totalDays = dateRange ? daysBetween(dateRange.start, dateRange.end) : 0

  // Filter epics that have schedule data
  const scheduledEpics = useMemo(() => {
    if (!forecast) return []
    return forecast.epics.filter(epic => {
      const { sa, dev, qa } = epic.phaseSchedule
      return sa.startDate || dev.startDate || qa.startDate
    })
  }, [forecast])

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Timeline</h2>
      </div>

      <div className="timeline-controls">
        <div className="filter-group">
          <label className="filter-label">Team</label>
          <select
            className="filter-input"
            value={selectedTeamId ?? ''}
            onChange={e => setSelectedTeamId(Number(e.target.value))}
          >
            <option value="" disabled>Select team...</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label className="filter-label">Zoom</label>
          <select
            className="filter-input"
            value={zoom}
            onChange={e => setZoom(e.target.value as ZoomLevel)}
          >
            <option value="day">Day</option>
            <option value="week">Week</option>
            <option value="month">Month</option>
          </select>
        </div>

        <div className="timeline-legend">
          <span className="legend-item legend-sa">SA</span>
          <span className="legend-item legend-dev">DEV</span>
          <span className="legend-item legend-qa">QA</span>
          <span className="legend-item legend-no-capacity">No Resource</span>
          <span className="legend-item legend-today">Today</span>
          <span className="legend-item legend-due">Due Date</span>
        </div>
      </div>

      {loading && <div className="loading">Loading forecast...</div>}
      {error && <div className="error">{error}</div>}

      {!loading && !error && selectedTeamId && forecast && scheduledEpics.length === 0 && (
        <div className="empty">No epics with schedule data found. Make sure epics have estimates.</div>
      )}

      {!loading && !error && forecast && scheduledEpics.length > 0 && dateRange && (
        <div className="gantt-container">
          <div className="gantt-labels">
            <div className="gantt-labels-header">Epic</div>
            {scheduledEpics.map(epic => (
              <div key={epic.epicKey} className="gantt-label-row">
                <a
                  href={`https://jira.atlassian.com/browse/${epic.epicKey}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="issue-key"
                >
                  {epic.epicKey}
                </a>
                <span className="gantt-label-title" title={epic.summary}>
                  {epic.summary}
                </span>
              </div>
            ))}
          </div>

          <div className="gantt-chart">
            <div className="gantt-header">
              {headers.map((header, i) => (
                <div key={i} className="gantt-header-cell">
                  {header.label}
                </div>
              ))}
            </div>

            <div className="gantt-body">
              {scheduledEpics.map(epic => (
                <GanttRow
                  key={epic.epicKey}
                  epic={epic}
                  rangeStart={dateRange.start}
                  totalDays={totalDays}
                />
              ))}
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
