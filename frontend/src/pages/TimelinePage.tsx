import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { teamsApi, Team } from '../api/teams'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, UnifiedPlanningResult, PlannedStory, UnifiedPhaseSchedule, PlanningWarning } from '../api/forecast'

type ZoomLevel = 'day' | 'week' | 'month'
type EpicStatus = 'on-track' | 'at-risk' | 'late' | 'no-due-date'

interface DateRange {
  start: Date
  end: Date
}

// --- Utility functions ---

function formatDateShort(date: Date): string {
  return date.toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' })
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
  if (delta <= 0) return 'on-track'
  if (delta <= 5) return 'at-risk'
  return 'late'
}

function getStatusIcon(status: EpicStatus): string | null {
  switch (status) {
    case 'at-risk': return '⚠️'
    case 'late': return '🔴'
    default: return null
  }
}

// --- Date range & timeline ---

function calculateDateRange(unifiedPlan: UnifiedPlanningResult | null, forecast: ForecastResponse | null): DateRange {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  let minDate: Date = today
  let maxDate: Date = addDays(today, 30)

  // Use unified plan dates if available
  if (unifiedPlan) {
    for (const epic of unifiedPlan.epics) {
      for (const story of epic.stories) {
        if (story.startDate) {
          const d = new Date(story.startDate)
          if (d < minDate) minDate = d
        }
        if (story.endDate) {
          const d = new Date(story.endDate)
          if (d > maxDate) maxDate = d
        }
      }
    }
  }

  // Also consider forecast due dates
  if (forecast) {
    for (const epic of forecast.epics) {
      if (epic.dueDate) {
        const d = new Date(epic.dueDate)
        if (d > maxDate) maxDate = d
      }
    }
  }

  // Add padding
  minDate = addDays(minDate, -3)
  maxDate = addDays(maxDate, 7)

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
        label: current.toLocaleDateString('ru-RU', { month: 'short' })
      })
      current = new Date(current.getFullYear(), current.getMonth() + 1, 1)
    }
  }

  return headers
}

// Phase colors - Atlassian Design System (300 level)
const PHASE_COLORS = {
  sa: '#85B8FF',   // Blue 300 — светло-синий
  dev: '#D6A0FB',  // Purple 300 — светло-фиолетовый
  qa: '#8BDBE5'    // Teal 300 — светло-бирюзовый
}

// Allocate lanes for stories to avoid overlap
function allocateStoryLanes(stories: PlannedStory[]): Map<string, number> {
  const lanes = new Map<string, number>()
  const laneEndDates: Date[] = []

  // Sort by start date
  const sorted = [...stories].filter(s => s.startDate && s.endDate)
    .sort((a, b) => new Date(a.startDate!).getTime() - new Date(b.startDate!).getTime())

  for (const story of sorted) {
    const storyStart = new Date(story.startDate!)
    const storyEnd = new Date(story.endDate!)

    // Find first available lane
    let lane = 0
    for (let i = 0; i < laneEndDates.length; i++) {
      if (laneEndDates[i] < storyStart) {
        lane = i
        break
      }
      lane = i + 1
    }

    lanes.set(story.storyKey, lane)
    laneEndDates[lane] = storyEnd
  }

  return lanes
}

// Constants for layout
const BAR_HEIGHT = 22
const LANE_GAP = 3
const MIN_ROW_HEIGHT = 36

// Calculate row height for an epic based on its stories
function calculateRowHeight(stories: PlannedStory[]): number {
  const activeStories = stories.filter(s => {
    const isDone = s.status?.toLowerCase().includes('готов') || s.status?.toLowerCase().includes('done')
    const hasPhases = s.phases?.sa || s.phases?.dev || s.phases?.qa
    return !isDone && hasPhases && s.startDate && s.endDate
  })

  if (activeStories.length === 0) return MIN_ROW_HEIGHT

  const storyLanes = allocateStoryLanes(activeStories)
  const maxLane = Math.max(0, ...Array.from(storyLanes.values())) + 1
  return Math.max(MIN_ROW_HEIGHT, maxLane * (BAR_HEIGHT + LANE_GAP) + 8)
}

// --- Story Bars Component ---
interface StoryBarsProps {
  stories: PlannedStory[]
  dateRange: DateRange
  jiraBaseUrl: string
  globalWarnings: PlanningWarning[]
}

function StoryBars({ stories, dateRange, jiraBaseUrl, globalWarnings }: StoryBarsProps) {
  const [hoveredStory, setHoveredStory] = useState<PlannedStory | null>(null)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })

  // Filter active stories with phases
  const activeStories = stories.filter(s => {
    const isDone = s.status?.toLowerCase().includes('готов') || s.status?.toLowerCase().includes('done')
    const hasPhases = s.phases?.sa || s.phases?.dev || s.phases?.qa
    return !isDone && hasPhases && s.startDate && s.endDate
  })

  if (activeStories.length === 0) {
    return <div className="story-empty-text">Нет активных сторей</div>
  }

  const totalDays = daysBetween(dateRange.start, dateRange.end)
  const storyLanes = allocateStoryLanes(activeStories)
  const maxLane = Math.max(0, ...Array.from(storyLanes.values())) + 1
  const containerHeight = maxLane * (BAR_HEIGHT + LANE_GAP)

  const handleMouseEnter = (e: React.MouseEvent, story: PlannedStory) => {
    e.stopPropagation()
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltipPos({ x: rect.left + rect.width / 2, y: rect.top - 8 })
    setHoveredStory(story)
  }

  const renderPhaseSegment = (
    phase: UnifiedPhaseSchedule | null,
    phaseType: 'sa' | 'dev' | 'qa',
    storyStart: Date,
    storyDuration: number
  ) => {
    if (!phase || !phase.startDate || !phase.endDate || phase.hours <= 0) return null

    const phaseStart = new Date(phase.startDate)
    const phaseEnd = new Date(phase.endDate)
    const phaseStartOffset = daysBetween(storyStart, phaseStart)
    const phaseDuration = daysBetween(phaseStart, phaseEnd) + 1
    const leftPercent = Math.max(0, (phaseStartOffset / storyDuration) * 100)
    const widthPercent = Math.min(100 - leftPercent, (phaseDuration / storyDuration) * 100)

    return (
      <div
        key={phaseType}
        style={{
          position: 'absolute',
          left: `${leftPercent}%`,
          width: `${widthPercent}%`,
          height: '100%',
          backgroundColor: PHASE_COLORS[phaseType],
          opacity: phase.noCapacity ? 0.4 : 1
        }}
      />
    )
  }

  return (
    <>
      <div style={{ height: `${containerHeight}px`, position: 'relative', width: '100%' }}>
        {activeStories.map(story => {
          const startDate = new Date(story.startDate!)
          const endDate = new Date(story.endDate!)
          const lane = storyLanes.get(story.storyKey) || 0

          const daysFromStart = daysBetween(dateRange.start, startDate)
          const duration = daysBetween(startDate, endDate) + 1
          const leftPercent = (daysFromStart / totalDays) * 100
          const widthPercent = (duration / totalDays) * 100

          const storyNumber = story.storyKey.split('-')[1] || story.storyKey
          const isBlocked = story.blockedBy && story.blockedBy.length > 0
          const hasWarning = story.warnings?.length > 0 || globalWarnings?.some(w => w.issueKey === story.storyKey)

          return (
            <div
              key={story.storyKey}
              style={{
                position: 'absolute',
                left: `${leftPercent}%`,
                width: `${Math.max(widthPercent, 1)}%`,
                top: `${lane * (BAR_HEIGHT + LANE_GAP)}px`,
                height: `${BAR_HEIGHT}px`,
                borderRadius: '3px',
                border: isBlocked ? '2px solid #ef4444' : '1px solid rgba(0,0,0,0.12)',
                overflow: 'hidden',
                cursor: 'pointer',
                background: '#e5e7eb',
                boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
              }}
              onMouseEnter={e => handleMouseEnter(e, story)}
              onMouseLeave={() => setHoveredStory(null)}
            >
              {renderPhaseSegment(story.phases?.sa ?? null, 'sa', startDate, duration)}
              {renderPhaseSegment(story.phases?.dev ?? null, 'dev', startDate, duration)}
              {renderPhaseSegment(story.phases?.qa ?? null, 'qa', startDate, duration)}

              <span style={{
                position: 'absolute',
                left: '50%',
                top: '50%',
                transform: 'translate(-50%, -50%)',
                fontSize: '11px',
                fontWeight: 600,
                color: 'white',
                textShadow: '0 1px 2px rgba(0,0,0,0.6)',
                zIndex: 2,
                pointerEvents: 'none'
              }}>
                {storyNumber}{hasWarning ? ' ⚠' : ''}
              </span>
            </div>
          )
        })}
      </div>

      {hoveredStory && createPortal(
        <div
          style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            transform: 'translate(-50%, -100%)',
            zIndex: 10000,
            pointerEvents: 'none',
            background: 'rgba(0,0,0,0.9)',
            borderRadius: '6px',
            padding: '12px',
            minWidth: '280px',
            maxWidth: '400px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
            color: 'white',
            fontSize: '13px'
          }}
        >
          <div style={{ fontWeight: 600, marginBottom: '8px' }}>
            <a
              href={`${jiraBaseUrl}${hoveredStory.storyKey}`}
              target="_blank"
              rel="noopener noreferrer"
              style={{ pointerEvents: 'auto', color: '#60a5fa', textDecoration: 'none' }}
            >
              {hoveredStory.storyKey}
            </a>
            {hoveredStory.autoScore !== null && (
              <span style={{ color: '#9ca3af', marginLeft: '8px' }}>({hoveredStory.autoScore?.toFixed(0)})</span>
            )}
          </div>

          <div style={{ color: '#d1d5db', marginBottom: '8px', fontSize: '12px' }}>
            {hoveredStory.summary || 'No summary'}
          </div>

          {hoveredStory.startDate && hoveredStory.endDate && (
            <div style={{ marginBottom: '8px', color: '#9ca3af' }}>
              📅 {formatDateShort(new Date(hoveredStory.startDate))} → {formatDateShort(new Date(hoveredStory.endDate))}
            </div>
          )}

          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <tbody>
              {hoveredStory.phases?.sa && hoveredStory.phases.sa.hours > 0 && (
                <tr>
                  <td style={{ padding: '2px 4px' }}><span style={{ color: PHASE_COLORS.sa }}>●</span> SA</td>
                  <td style={{ padding: '2px 4px' }}>{hoveredStory.phases.sa.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '2px 4px', textAlign: 'right' }}>{hoveredStory.phases.sa.hours.toFixed(0)}ч</td>
                </tr>
              )}
              {hoveredStory.phases?.dev && hoveredStory.phases.dev.hours > 0 && (
                <tr>
                  <td style={{ padding: '2px 4px' }}><span style={{ color: PHASE_COLORS.dev }}>●</span> DEV</td>
                  <td style={{ padding: '2px 4px' }}>{hoveredStory.phases.dev.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '2px 4px', textAlign: 'right' }}>{hoveredStory.phases.dev.hours.toFixed(0)}ч</td>
                </tr>
              )}
              {hoveredStory.phases?.qa && hoveredStory.phases.qa.hours > 0 && (
                <tr>
                  <td style={{ padding: '2px 4px' }}><span style={{ color: PHASE_COLORS.qa }}>●</span> QA</td>
                  <td style={{ padding: '2px 4px' }}>{hoveredStory.phases.qa.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '2px 4px', textAlign: 'right' }}>{hoveredStory.phases.qa.hours.toFixed(0)}ч</td>
                </tr>
              )}
            </tbody>
          </table>

          {hoveredStory.blockedBy && hoveredStory.blockedBy.length > 0 && (
            <div style={{ color: '#f87171', marginTop: '8px', fontSize: '12px' }}>
              🚫 Blocked by: {hoveredStory.blockedBy.join(', ')}
            </div>
          )}
        </div>,
        document.body
      )}
    </>
  )
}

// --- Gantt Row ---
interface GanttRowProps {
  epic: EpicForecast | undefined
  stories: PlannedStory[]
  globalWarnings: PlanningWarning[]
  dateRange: DateRange
  jiraBaseUrl: string
  rowHeight: number
}

function GanttRow({ epic, stories, globalWarnings, dateRange, jiraBaseUrl, rowHeight }: GanttRowProps) {
  const totalDays = daysBetween(dateRange.start, dateRange.end)

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayOffset = daysBetween(dateRange.start, today)
  const todayPercent = (todayOffset / totalDays) * 100

  const dueDate = epic?.dueDate ? new Date(epic.dueDate) : null
  const dueDateOffset = dueDate ? daysBetween(dateRange.start, dueDate) : null
  const dueDatePercent = dueDateOffset !== null ? (dueDateOffset / totalDays) * 100 : null

  return (
    <div className="gantt-row" style={{ height: `${rowHeight}px` }}>
      <div className="gantt-row-content" style={{ position: 'relative', padding: '4px 0' }}>
        {/* Today line */}
        {todayPercent >= 0 && todayPercent <= 100 && (
          <div className="gantt-today-line" style={{ left: `${todayPercent}%` }} />
        )}

        {/* Due date line */}
        {dueDatePercent !== null && dueDatePercent >= 0 && dueDatePercent <= 100 && (
          <div className="gantt-due-line" style={{ left: `${dueDatePercent}%` }} />
        )}

        {/* Story bars */}
        <StoryBars
          stories={stories}
          dateRange={dateRange}
          jiraBaseUrl={jiraBaseUrl}
          globalWarnings={globalWarnings}
        />
      </div>
    </div>
  )
}

// --- Main component ---

export function TimelinePage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [forecast, setForecast] = useState<ForecastResponse | null>(null)
  const [unifiedPlan, setUnifiedPlan] = useState<UnifiedPlanningResult | null>(null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const chartRef = useRef<HTMLDivElement>(null)

  // Load teams
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

  // Load data when team changes
  useEffect(() => {
    if (!selectedTeamId) return

    setLoading(true)
    setError(null)

    Promise.all([
      getForecast(selectedTeamId),
      getUnifiedPlanning(selectedTeamId)
    ])
      .then(([forecastData, planData]) => {
        setForecast(forecastData)
        setUnifiedPlan(planData)
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load data: ' + err.message)
        setLoading(false)
      })
  }, [selectedTeamId])

  // Auto-scroll to today
  useEffect(() => {
    if (!unifiedPlan || !chartRef.current) return

    const range = calculateDateRange(unifiedPlan, forecast)
    const today = new Date()
    today.setHours(0, 0, 0, 0)

    const totalDays = daysBetween(range.start, range.end)
    const todayOffset = daysBetween(range.start, today)
    const todayPercent = todayOffset / totalDays

    const scrollTarget = Math.max(0, (todayPercent - 0.2) * chartRef.current.scrollWidth)
    chartRef.current.scrollLeft = scrollTarget
  }, [unifiedPlan, forecast])

  const dateRange = useMemo(() => {
    return calculateDateRange(unifiedPlan, forecast)
  }, [unifiedPlan, forecast])

  const headers = useMemo(() => {
    return generateTimelineHeaders(dateRange, zoom)
  }, [dateRange, zoom])

  // Get epics from unified plan
  const epics = useMemo(() => {
    if (!unifiedPlan) return []
    return unifiedPlan.epics
  }, [unifiedPlan])

  // Match with forecast for status info
  const epicForecasts = useMemo(() => {
    if (!forecast) return new Map<string, EpicForecast>()
    return new Map(forecast.epics.map(e => [e.epicKey, e]))
  }, [forecast])

  // Calculate row heights for each epic
  const rowHeights = useMemo(() => {
    const heights = new Map<string, number>()
    for (const epic of epics) {
      heights.set(epic.epicKey, calculateRowHeight(epic.stories))
    }
    return heights
  }, [epics])

  const jiraBaseUrl = 'https://krasivye.atlassian.net/browse/'

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Timeline</h2>
      </div>

      <div className="timeline-controls">
        <div className="filter-group">
          <label className="filter-label">Команда</label>
          <select
            className="filter-input"
            value={selectedTeamId ?? ''}
            onChange={e => setSelectedTeamId(Number(e.target.value))}
          >
            <option value="" disabled>Выберите команду...</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label className="filter-label">Масштаб</label>
          <select
            className="filter-input"
            value={zoom}
            onChange={e => setZoom(e.target.value as ZoomLevel)}
          >
            <option value="day">День</option>
            <option value="week">Неделя</option>
            <option value="month">Месяц</option>
          </select>
        </div>

        <div className="timeline-legend">
          <span className="legend-item legend-phase-sa">SA</span>
          <span className="legend-item legend-phase-dev">DEV</span>
          <span className="legend-item legend-phase-qa">QA</span>
          <span className="legend-item legend-today">Сегодня</span>
          <span className="legend-item legend-due">Due Date</span>
        </div>
      </div>

      {loading && <div className="loading">Загрузка...</div>}
      {error && <div className="error">{error}</div>}

      {!loading && !error && epics.length === 0 && (
        <div className="empty">Нет эпиков с данными для планирования</div>
      )}

      {!loading && !error && epics.length > 0 && (
        <div className="gantt-container">
          <div className="gantt-labels">
            <div className="gantt-labels-header">Эпик</div>
            {epics.map(epic => {
              const epicForecast = epicForecasts.get(epic.epicKey)
              const status = epicForecast ? getEpicStatus(epicForecast) : 'no-due-date'
              const statusIcon = getStatusIcon(status)
              const rowHeight = rowHeights.get(epic.epicKey) || MIN_ROW_HEIGHT

              return (
                <div key={epic.epicKey} className="gantt-label-row" style={{ height: `${rowHeight}px` }}>
                  <div className="gantt-label-header">
                    <a
                      href={`${jiraBaseUrl}${epic.epicKey}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="issue-key"
                    >
                      {epic.epicKey}
                    </a>
                    {statusIcon && <span className="gantt-status-icon">{statusIcon}</span>}
                  </div>
                  <span className="gantt-label-title" title={epic.summary}>
                    {epic.summary}
                  </span>
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
              {epics.map(epic => {
                const epicForecast = epicForecasts.get(epic.epicKey)
                const rowHeight = rowHeights.get(epic.epicKey) || MIN_ROW_HEIGHT
                return (
                  <GanttRow
                    key={epic.epicKey}
                    epic={epicForecast}
                    stories={epic.stories}
                    globalWarnings={unifiedPlan?.warnings || []}
                    dateRange={dateRange}
                    jiraBaseUrl={jiraBaseUrl}
                    rowHeight={rowHeight}
                  />
                )
              })}
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
