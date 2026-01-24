import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { teamsApi, Team } from '../api/teams'
import { getForecast, ForecastResponse, EpicForecast, PhaseInfo, WipStatus, RoleWipStatus } from '../api/forecast'

type ZoomLevel = 'day' | 'week' | 'month'
type EpicStatus = 'on-track' | 'at-risk' | 'late' | 'no-due-date'

interface DateRange {
  start: Date
  end: Date
}

// --- Utility functions ---

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

// --- Status calculation ---

function getEpicStatus(epic: EpicForecast): EpicStatus {
  if (!epic.dueDate) return 'no-due-date'

  const delta = epic.dueDateDeltaDays ?? 0

  if (delta <= 0) return 'on-track'      // On time or early
  if (delta <= 5) return 'at-risk'       // 1-5 days late
  return 'late'                          // More than 5 days late
}

function getStatusColor(status: EpicStatus): string {
  switch (status) {
    case 'on-track': return '#22c55e'      // Green
    case 'at-risk': return '#eab308'       // Yellow
    case 'late': return '#ef4444'          // Red
    case 'no-due-date': return '#6b7280'   // Gray
  }
}

function getStatusIcon(status: EpicStatus): string | null {
  switch (status) {
    case 'at-risk': return '⚠️'
    case 'late': return '🔴'
    default: return null
  }
}

// --- Date range & timeline ---

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

// --- Progress calculation ---

function calculateEpicProgress(epic: EpicForecast): number {
  const { sa, dev, qa } = epic.phaseSchedule

  const totalDays = (sa.workDays ?? 0) + (dev.workDays ?? 0) + (qa.workDays ?? 0)
  if (totalDays === 0) return 0

  // Calculate progress based on current date vs phase dates
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  let completedDays = 0

  const phases = [sa, dev, qa]
  for (const phase of phases) {
    if (!phase.startDate || !phase.endDate) continue

    const startDate = new Date(phase.startDate)
    const endDate = new Date(phase.endDate)
    const phaseDays = phase.workDays ?? 0

    if (today >= endDate) {
      // Phase complete
      completedDays += phaseDays
    } else if (today >= startDate) {
      // Phase in progress
      const totalPhaseDays = daysBetween(startDate, endDate)
      const elapsedDays = daysBetween(startDate, today)
      const fraction = Math.min(elapsedDays / totalPhaseDays, 1)
      completedDays += phaseDays * fraction
    }
  }

  return Math.round((completedDays / totalDays) * 100)
}

// --- Epic date range (unified bar) ---

function getEpicDateRange(epic: EpicForecast): { start: Date | null; end: Date | null } {
  const { sa, dev, qa } = epic.phaseSchedule

  let start: Date | null = null
  let end: Date | null = null

  for (const phase of [sa, dev, qa]) {
    if (phase.startDate) {
      const d = new Date(phase.startDate)
      if (!start || d < start) start = d
    }
    if (phase.endDate) {
      const d = new Date(phase.endDate)
      if (!end || d > end) end = d
    }
  }

  return { start, end }
}

// --- Components ---

interface TooltipProps {
  epic: EpicForecast
  progress: number
}

function EpicTooltip({ epic, progress }: TooltipProps) {
  const phaseWait = epic.phaseWaitInfo

  const formatPhase = (phase: PhaseInfo, label: string, roleKey: 'sa' | 'dev' | 'qa') => {
    if (!phase.startDate || !phase.endDate) {
      return <div className="tooltip-phase tooltip-phase-na">{label}: Not scheduled</div>
    }

    const workDays = phase.workDays ?? 0
    const noCapacity = phase.noCapacity
    const waitInfo = phaseWait?.[roleKey]
    const isWaiting = waitInfo?.waiting

    return (
      <div className={`tooltip-phase ${noCapacity ? 'tooltip-phase-warning' : ''} ${isWaiting ? 'tooltip-phase-waiting' : ''}`}>
        <span className="tooltip-phase-label">{label}:</span>
        <span className="tooltip-phase-dates">
          {formatDateShort(new Date(phase.startDate))} - {formatDateShort(new Date(phase.endDate))}
        </span>
        <span className="tooltip-phase-days">({workDays}d)</span>
        {noCapacity && <span className="tooltip-phase-alert">No resource!</span>}
        {isWaiting && waitInfo.waitingUntil && (
          <span className="tooltip-phase-wait">
            ⏳ waited until {formatDateShort(new Date(waitInfo.waitingUntil))}
          </span>
        )}
      </div>
    )
  }

  const deltaText = () => {
    if (!epic.dueDate) return null
    const delta = epic.dueDateDeltaDays ?? 0
    if (delta < 0) return <span className="tooltip-delta tooltip-delta-early">{Math.abs(delta)} days early</span>
    if (delta === 0) return <span className="tooltip-delta tooltip-delta-ontime">On time</span>
    return <span className="tooltip-delta tooltip-delta-late">{delta} days late</span>
  }

  const isQueued = !epic.isWithinWip
  const hasPhaseWaiting = phaseWait && (phaseWait.sa?.waiting || phaseWait.dev?.waiting || phaseWait.qa?.waiting)

  return (
    <div className="epic-tooltip">
      <div className="tooltip-header">
        <span className="tooltip-key">{epic.epicKey}</span>
        <span className="tooltip-summary">{epic.summary}</span>
      </div>

      {isQueued && (
        <div className="tooltip-section tooltip-queue-info">
          <div className="tooltip-queue-badge">⏳ In Queue #{epic.queuePosition}</div>
          {epic.queuedUntil && (
            <div className="tooltip-row">
              <span>Waiting until:</span>
              <strong>{formatDateFull(new Date(epic.queuedUntil))}</strong>
            </div>
          )}
        </div>
      )}

      {hasPhaseWaiting && !isQueued && (
        <div className="tooltip-section tooltip-phase-queue-info">
          <div className="tooltip-phase-queue-badge">Phase WIP delays present</div>
        </div>
      )}

      <div className="tooltip-section">
        <div className="tooltip-row">
          <span>Expected:</span>
          <strong>{epic.expectedDone ? formatDateFull(new Date(epic.expectedDone)) : 'Unknown'}</strong>
          {deltaText()}
        </div>
        <div className="tooltip-row">
          <span>Progress:</span>
          <strong>{progress}%</strong>
        </div>
        <div className="tooltip-row">
          <span>Confidence:</span>
          <strong className={`confidence-${epic.confidence.toLowerCase()}`}>{epic.confidence}</strong>
        </div>
      </div>

      <div className="tooltip-section tooltip-phases">
        {formatPhase(epic.phaseSchedule.sa, 'SA', 'sa')}
        {formatPhase(epic.phaseSchedule.dev, 'DEV', 'dev')}
        {formatPhase(epic.phaseSchedule.qa, 'QA', 'qa')}
      </div>
    </div>
  )
}

interface PhaseBarProps {
  phase: PhaseInfo
  role: 'sa' | 'dev' | 'qa'
  rangeStart: Date
  totalDays: number
}

function PhaseBar({ phase, role, rangeStart, totalDays }: PhaseBarProps) {
  if (!phase.startDate || !phase.endDate) return null

  const startDate = new Date(phase.startDate)
  const endDate = new Date(phase.endDate)
  const startOffset = daysBetween(rangeStart, startDate)
  const duration = daysBetween(startDate, endDate) + 1

  const leftPercent = (startOffset / totalDays) * 100
  const widthPercent = (duration / totalDays) * 100

  const roleLabels = { sa: 'SA', dev: 'DEV', qa: 'QA' }
  const noCapacity = phase.noCapacity === true

  return (
    <div
      className={`gantt-phase-bar gantt-phase-${role} ${noCapacity ? 'gantt-phase-no-capacity' : ''}`}
      style={{ left: `${leftPercent}%`, width: `${Math.max(widthPercent, 0.5)}%` }}
    >
      <span className="gantt-phase-label">{roleLabels[role]}</span>
      <span className="gantt-phase-days">{phase.workDays}d</span>
    </div>
  )
}

interface GanttRowProps {
  epic: EpicForecast
  rangeStart: Date
  totalDays: number
  isExpanded: boolean
  onToggle: () => void
}

function GanttRow({ epic, rangeStart, totalDays, isExpanded, onToggle }: GanttRowProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })
  const tooltipTimeoutRef = useRef<number | null>(null)

  const status = getEpicStatus(epic)
  const progress = calculateEpicProgress(epic)
  const { start: epicStart, end: epicEnd } = getEpicDateRange(epic)

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayOffset = daysBetween(rangeStart, today)
  const todayPercent = (todayOffset / totalDays) * 100

  const dueDate = parseDate(epic.dueDate)
  const dueDateOffset = dueDate ? daysBetween(rangeStart, dueDate) : null
  const dueDatePercent = dueDateOffset !== null ? (dueDateOffset / totalDays) * 100 : null

  // Unified bar position
  let barLeft = 0
  let barWidth = 0
  if (epicStart && epicEnd) {
    const startOffset = daysBetween(rangeStart, epicStart)
    const duration = daysBetween(epicStart, epicEnd) + 1
    barLeft = (startOffset / totalDays) * 100
    barWidth = (duration / totalDays) * 100
  }

  const handleMouseEnter = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltipPos({ x: rect.left, y: rect.top - 8 })
    tooltipTimeoutRef.current = window.setTimeout(() => setShowTooltip(true), 300)
  }

  const handleMouseLeave = () => {
    if (tooltipTimeoutRef.current) {
      clearTimeout(tooltipTimeoutRef.current)
    }
    setShowTooltip(false)
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltipPos({ x: rect.left, y: rect.top - 8 })
  }

  return (
    <>
      <div
        className={`gantt-row ${isExpanded ? 'gantt-row-expanded' : ''}`}
        onClick={onToggle}
      >
        <div className="gantt-row-content">
          {/* Today line */}
          {todayPercent >= 0 && todayPercent <= 100 && (
            <div className="gantt-today-line" style={{ left: `${todayPercent}%` }} />
          )}

          {/* Due date line */}
          {dueDatePercent !== null && dueDatePercent >= 0 && dueDatePercent <= 100 && (
            <div
              className="gantt-due-line"
              style={{ left: `${dueDatePercent}%` }}
              title={`Due: ${formatDateFull(dueDate!)}`}
            />
          )}

          {/* Unified epic bar */}
          {epicStart && epicEnd && (
            <div
              className="gantt-unified-bar"
              style={{
                left: `${barLeft}%`,
                width: `${Math.max(barWidth, 0.5)}%`,
                backgroundColor: getStatusColor(status)
              }}
              onMouseEnter={handleMouseEnter}
              onMouseLeave={handleMouseLeave}
              onMouseMove={handleMouseMove}
            >
              {/* Progress fill */}
              <div
                className="gantt-unified-progress"
                style={{ width: `${progress}%` }}
              />
              {/* Label */}
              <span className="gantt-unified-label">
                {progress > 0 && `${progress}%`}
              </span>
            </div>
          )}

          {/* Tooltip */}
          {showTooltip && (
            <div
              className="gantt-tooltip-wrapper"
              style={{
                left: `${tooltipPos.x}px`,
                top: `${tooltipPos.y}px`,
                transform: 'translateY(-100%)'
              }}
            >
              <EpicTooltip epic={epic} progress={progress} />
            </div>
          )}
        </div>
      </div>

      {/* Expanded phase bars */}
      {isExpanded && (
        <div className="gantt-row gantt-row-phases">
          <div className="gantt-row-content">
            {todayPercent >= 0 && todayPercent <= 100 && (
              <div className="gantt-today-line" style={{ left: `${todayPercent}%` }} />
            )}
            <PhaseBar phase={epic.phaseSchedule.sa} role="sa" rangeStart={rangeStart} totalDays={totalDays} />
            <PhaseBar phase={epic.phaseSchedule.dev} role="dev" rangeStart={rangeStart} totalDays={totalDays} />
            <PhaseBar phase={epic.phaseSchedule.qa} role="qa" rangeStart={rangeStart} totalDays={totalDays} />
          </div>
        </div>
      )}
    </>
  )
}

interface SummaryPanelProps {
  epics: EpicForecast[]
  wipStatus: WipStatus | null
}

function RoleWipBadge({ label, roleWip }: { label: string; roleWip: RoleWipStatus | null }) {
  if (!roleWip) return null
  const exceeded = roleWip.exceeded
  return (
    <span className={`summary-role-wip ${exceeded ? 'summary-role-wip-exceeded' : ''}`}>
      {label}: {roleWip.current}/{roleWip.limit}
    </span>
  )
}

function SummaryPanel({ epics, wipStatus }: SummaryPanelProps) {
  const stats = useMemo(() => {
    let onTrack = 0
    let atRisk = 0
    let late = 0
    let noDueDate = 0
    let inQueue = 0

    for (const epic of epics) {
      const status = getEpicStatus(epic)
      switch (status) {
        case 'on-track': onTrack++; break
        case 'at-risk': atRisk++; break
        case 'late': late++; break
        case 'no-due-date': noDueDate++; break
      }
      if (!epic.isWithinWip) {
        inQueue++
      }
    }

    return { onTrack, atRisk, late, noDueDate, inQueue, total: epics.length }
  }, [epics])

  const hasRoleWip = wipStatus?.sa || wipStatus?.dev || wipStatus?.qa

  return (
    <div className="timeline-summary">
      <span className="summary-total">{stats.total} epics</span>
      <span className="summary-separator">·</span>
      <span className="summary-stat summary-on-track">{stats.onTrack} on track</span>
      {stats.atRisk > 0 && (
        <>
          <span className="summary-separator">·</span>
          <span className="summary-stat summary-at-risk">⚠️ {stats.atRisk} at risk</span>
        </>
      )}
      {stats.late > 0 && (
        <>
          <span className="summary-separator">·</span>
          <span className="summary-stat summary-late">🔴 {stats.late} late</span>
        </>
      )}
      {wipStatus && (
        <>
          <span className="summary-separator">·</span>
          <span className={`summary-stat summary-wip ${wipStatus.exceeded ? 'summary-wip-exceeded' : ''}`}>
            WIP {wipStatus.current}/{wipStatus.limit}
          </span>
        </>
      )}
      {hasRoleWip && (
        <>
          <span className="summary-separator">·</span>
          <span className="summary-role-wip-group">
            <RoleWipBadge label="SA" roleWip={wipStatus?.sa ?? null} />
            <RoleWipBadge label="DEV" roleWip={wipStatus?.dev ?? null} />
            <RoleWipBadge label="QA" roleWip={wipStatus?.qa ?? null} />
          </span>
        </>
      )}
      {stats.inQueue > 0 && (
        <>
          <span className="summary-separator">·</span>
          <span className="summary-stat summary-queue">⏳ {stats.inQueue} in queue</span>
        </>
      )}
    </div>
  )
}

// --- Main component ---

export function TimelinePage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [forecast, setForecast] = useState<ForecastResponse | null>(null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expandedEpics, setExpandedEpics] = useState<Set<string>>(new Set())

  const chartRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        if (activeTeams.length > 0 && !selectedTeamId) {
          setSelectedTeamId(activeTeams[0].id)
        }
      })
      .catch(err => setError('Failed to load teams: ' + err.message))
  }, [])

  useEffect(() => {
    if (!selectedTeamId) return

    setLoading(true)
    setError(null)
    setExpandedEpics(new Set())

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

  // Auto-scroll to today when data loads
  useEffect(() => {
    if (!forecast || !chartRef.current) return

    const dateRange = calculateDateRange(forecast)
    const today = new Date()
    today.setHours(0, 0, 0, 0)

    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const todayOffset = daysBetween(dateRange.start, today)
    const todayPercent = todayOffset / totalDays

    // Scroll to show today at ~30% from left
    const scrollTarget = Math.max(0, (todayPercent - 0.3) * chartRef.current.scrollWidth)
    chartRef.current.scrollLeft = scrollTarget
  }, [forecast])

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

  const toggleEpic = useCallback((epicKey: string) => {
    setExpandedEpics(prev => {
      const next = new Set(prev)
      if (next.has(epicKey)) {
        next.delete(epicKey)
      } else {
        next.add(epicKey)
      }
      return next
    })
  }, [])

  const jiraBaseUrl = forecast?.epics[0]?.epicKey
    ? `https://${window.location.hostname.includes('localhost') ? 'jira.atlassian.com' : window.location.hostname}/browse/`
    : 'https://jira.atlassian.com/browse/'

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
          <span className="legend-item legend-on-track">On Track</span>
          <span className="legend-item legend-at-risk">At Risk</span>
          <span className="legend-item legend-late">Late</span>
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
        <>
          <SummaryPanel epics={scheduledEpics} wipStatus={forecast?.wipStatus ?? null} />

          <div className="gantt-container">
            <div className="gantt-labels">
              <div className="gantt-labels-header">Epic</div>
              {scheduledEpics.map(epic => {
                const status = getEpicStatus(epic)
                const statusIcon = getStatusIcon(status)
                const isExpanded = expandedEpics.has(epic.epicKey)
                const isQueued = !epic.isWithinWip

                return (
                  <div key={epic.epicKey}>
                    <div
                      className={`gantt-label-row ${isExpanded ? 'gantt-label-row-expanded' : ''} ${isQueued ? 'gantt-label-row-queued' : ''}`}
                      onClick={() => toggleEpic(epic.epicKey)}
                    >
                      <div className="gantt-label-header">
                        <span className="gantt-expand-icon">{isExpanded ? '▼' : '▶'}</span>
                        {isQueued && <span className="gantt-queue-badge">#{epic.queuePosition}</span>}
                        <a
                          href={`${jiraBaseUrl}${epic.epicKey}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="issue-key"
                          onClick={e => e.stopPropagation()}
                        >
                          {epic.epicKey}
                        </a>
                        {statusIcon && <span className="gantt-status-icon">{statusIcon}</span>}
                      </div>
                      <span className="gantt-label-title" title={epic.summary}>
                        {epic.summary}
                      </span>
                    </div>
                    {isExpanded && (
                      <div className="gantt-label-row gantt-label-phases">
                        <div className="gantt-phase-labels">
                          <span className="gantt-phase-tag gantt-phase-tag-sa">SA</span>
                          <span className="gantt-phase-tag gantt-phase-tag-dev">DEV</span>
                          <span className="gantt-phase-tag gantt-phase-tag-qa">QA</span>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>

            <div className="gantt-chart" ref={chartRef}>
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
                    isExpanded={expandedEpics.has(epic.epicKey)}
                    onToggle={() => toggleEpic(epic.epicKey)}
                  />
                ))}
              </div>
            </div>
          </div>

          <div className="timeline-hint">
            Click on an epic to expand and see SA/DEV/QA phases
          </div>
        </>
      )}
    </main>
  )
}
