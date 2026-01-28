import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { teamsApi, Team } from '../api/teams'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, UnifiedPlanningResult, PlannedStory, PlannedEpic, UnifiedPhaseSchedule, PlanningWarning, getAvailableSnapshotDates, getUnifiedPlanningSnapshot, getForecastSnapshot } from '../api/forecast'
import { getConfig } from '../api/config'

// Issue type icons
import storyIcon from '../icons/story.png'
import bugIcon from '../icons/bug.png'
import epicIcon from '../icons/epic.png'
import subtaskIcon from '../icons/subtask.png'

type ZoomLevel = 'day' | 'week' | 'month'

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

// Get issue type icon
function getIssueTypeIcon(issueType: string | null): string {
  if (!issueType) return storyIcon
  const type = issueType.toLowerCase()
  if (type.includes('bug')) return bugIcon
  if (type.includes('epic')) return epicIcon
  if (type.includes('sub') || type.includes('подзадача')) return subtaskIcon
  return storyIcon
}

// Format seconds to hours
function formatHours(seconds: number | null): string {
  if (seconds === null || seconds === 0) return '0ч'
  return `${Math.round(seconds / 3600)}ч`
}

// Check if phase has hours
function phaseHasHours(phase: { hours?: number } | null | undefined): boolean {
  return phase != null && phase.hours != null && phase.hours > 0
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
const MIN_ROW_HEIGHT = 48

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

// --- Status badge colors (Atlassian Design System) ---
const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  'new': { bg: '#dfe1e6', text: '#42526e' },
  'backlog': { bg: '#dfe1e6', text: '#42526e' },
  'to do': { bg: '#dfe1e6', text: '#42526e' },
  'planned': { bg: '#deebff', text: '#0747a6' },
  'developing': { bg: '#e3fcef', text: '#006644' },
  'in progress': { bg: '#e3fcef', text: '#006644' },
  'e2e testing': { bg: '#eae6ff', text: '#403294' },
  'done': { bg: '#dfe1e6', text: '#42526e' },
  'новый': { bg: '#dfe1e6', text: '#42526e' },
  'бэклог': { bg: '#dfe1e6', text: '#42526e' },
  'запланировано': { bg: '#deebff', text: '#0747a6' },
  'в разработке': { bg: '#e3fcef', text: '#006644' },
  'e2e тестирование': { bg: '#eae6ff', text: '#403294' },
  'готово': { bg: '#dfe1e6', text: '#42526e' },
}

function getStatusColor(status: string | null): { bg: string; text: string } {
  if (!status) return { bg: '#dfe1e6', text: '#42526e' }
  return STATUS_COLORS[status.toLowerCase()] || { bg: '#dfe1e6', text: '#42526e' }
}

// --- Epic Label Component with Tooltip ---
interface EpicLabelProps {
  epic: PlannedEpic
  epicForecast: EpicForecast | undefined
  jiraBaseUrl: string
  rowHeight: number
}

function EpicLabel({ epic, epicForecast, jiraBaseUrl, rowHeight }: EpicLabelProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })
  const labelRef = useRef<HTMLDivElement>(null)

  const handleMouseEnter = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltipPos({ x: rect.right + 8, y: rect.top })
    setShowTooltip(true)
  }

  const statusColor = getStatusColor(epic.status)
  const progress = epic.progressPercent ?? 0
  const dueDateDelta = epicForecast?.dueDateDeltaDays ?? null

  // Due date status indicator
  let dueDateIndicator = null
  if (epic.dueDate) {
    if (dueDateDelta === null || dueDateDelta <= 0) {
      dueDateIndicator = <span style={{ color: '#36B37E', fontSize: 10 }}>●</span>
    } else if (dueDateDelta <= 5) {
      dueDateIndicator = <span style={{ color: '#FFAB00', fontSize: 10 }}>●</span>
    } else {
      dueDateIndicator = <span style={{ color: '#FF5630', fontSize: 10 }}>●</span>
    }
  }

  // Shorten status for display
  const shortStatus = (epic.status || '').length > 18
    ? (epic.status || '').substring(0, 16) + '…'
    : (epic.status || 'Unknown')

  return (
    <>
      <div
        ref={labelRef}
        className="gantt-label-row"
        style={{ height: `${rowHeight}px` }}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={() => setShowTooltip(false)}
      >
        {/* Row 1: Icon + Key + Status badge + Progress */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <img src={epicIcon} alt="Epic" style={{ width: 16, height: 16 }} />
            <a
              href={`${jiraBaseUrl}${epic.epicKey}`}
              target="_blank"
              rel="noopener noreferrer"
              className="issue-key"
            >
              {epic.epicKey}
            </a>
            {dueDateIndicator}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{
                backgroundColor: statusColor.bg,
                color: statusColor.text,
                padding: '1px 5px',
                borderRadius: 3,
                fontSize: 9,
                fontWeight: 500,
              }}
              title={epic.status || ''}
            >
              {shortStatus}
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <div style={{
                width: 32,
                height: 5,
                backgroundColor: '#dfe1e6',
                borderRadius: 2,
                overflow: 'hidden'
              }}>
                <div style={{
                  width: `${progress}%`,
                  height: '100%',
                  backgroundColor: progress >= 100 ? '#36B37E' : '#0065FF',
                }} />
              </div>
              <span style={{ fontSize: 9, color: '#6b778c', minWidth: 22 }}>{progress}%</span>
            </div>
          </div>
        </div>
        <span className="gantt-label-title" title={epic.summary}>
          {epic.summary}
        </span>
      </div>

      {showTooltip && createPortal(
        <div
          style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            zIndex: 10000,
            pointerEvents: 'none',
            background: 'rgba(23, 43, 77, 0.98)',
            borderRadius: 8,
            padding: 14,
            minWidth: 300,
            maxWidth: 400,
            boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
            color: 'white',
            fontSize: 13
          }}
        >
          {/* Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <img src={epicIcon} alt="Epic" style={{ width: 16, height: 16 }} />
              <span style={{ fontWeight: 600, color: '#B3D4FF' }}>{epic.epicKey}</span>
              <span style={{ color: '#8993A4', fontSize: 11 }}>({epic.autoScore?.toFixed(0)})</span>
            </div>
            <span
              style={{
                backgroundColor: statusColor.bg,
                color: statusColor.text,
                padding: '2px 8px',
                borderRadius: 3,
                fontSize: 11,
                fontWeight: 500
              }}
            >
              {epic.status || 'Unknown'}
            </span>
          </div>

          {/* Summary */}
          <div style={{ color: '#B3BAC5', marginBottom: 12, fontSize: 12, lineHeight: 1.4 }}>
            {epic.summary}
          </div>

          {/* Progress section */}
          <div style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <span style={{ color: '#8993A4', fontSize: 11 }}>Прогресс</span>
              <span style={{ color: '#B3BAC5', fontSize: 11 }}>
                {formatHours(epic.totalLoggedSeconds)} / {formatHours(epic.totalEstimateSeconds)}
              </span>
            </div>
            <div style={{
              width: '100%',
              height: 8,
              backgroundColor: '#42526e',
              borderRadius: 4,
              overflow: 'hidden'
            }}>
              <div style={{
                width: `${progress}%`,
                height: '100%',
                backgroundColor: progress >= 100 ? '#36B37E' : '#0065FF',
                borderRadius: 4
              }} />
            </div>
            <div style={{ textAlign: 'right', color: '#B3BAC5', fontSize: 12, marginTop: 2 }}>
              {progress}%
            </div>
          </div>

          {/* Dates section */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, fontSize: 12 }}>
            <div>
              <span style={{ color: '#8993A4' }}>📅 </span>
              <span style={{ color: '#B3BAC5' }}>
                {epic.startDate ? formatDateShort(new Date(epic.startDate)) : '—'}
                {' → '}
                {epic.endDate ? formatDateShort(new Date(epic.endDate)) : '—'}
              </span>
            </div>
            {epic.dueDate && (
              <div>
                <span style={{ color: '#8993A4' }}>⏰ Due: </span>
                <span style={{ color: dueDateDelta && dueDateDelta > 0 ? '#FF5630' : '#36B37E' }}>
                  {formatDateShort(new Date(epic.dueDate))}
                  {dueDateDelta !== null && dueDateDelta > 0 && ` (+${dueDateDelta}д)`}
                </span>
              </div>
            )}
          </div>

          {/* Role progress */}
          {epic.roleProgress && (
            <div style={{ borderTop: '1px solid #42526e', paddingTop: 10, marginBottom: 10 }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <tbody>
                  {epic.roleProgress.sa && (
                    <tr>
                      <td style={{ padding: '3px 0', width: 50 }}>
                        <span style={{ color: PHASE_COLORS.sa }}>●</span> SA
                        {epic.roleProgress.sa.completed && <span style={{ marginLeft: 4 }}>✓</span>}
                      </td>
                      <td style={{ color: '#B3BAC5', textAlign: 'right' }}>
                        {formatHours(epic.roleProgress.sa.loggedSeconds)} / {formatHours(epic.roleProgress.sa.estimateSeconds)}
                      </td>
                      <td style={{ color: '#8993A4', textAlign: 'right', width: 45 }}>
                        {epic.roleProgress.sa.estimateSeconds
                          ? Math.min(100, Math.round((epic.roleProgress.sa.loggedSeconds || 0) * 100 / epic.roleProgress.sa.estimateSeconds))
                          : 0}%
                      </td>
                    </tr>
                  )}
                  {epic.roleProgress.dev && (
                    <tr>
                      <td style={{ padding: '3px 0' }}>
                        <span style={{ color: PHASE_COLORS.dev }}>●</span> DEV
                        {epic.roleProgress.dev.completed && <span style={{ marginLeft: 4 }}>✓</span>}
                      </td>
                      <td style={{ color: '#B3BAC5', textAlign: 'right' }}>
                        {formatHours(epic.roleProgress.dev.loggedSeconds)} / {formatHours(epic.roleProgress.dev.estimateSeconds)}
                      </td>
                      <td style={{ color: '#8993A4', textAlign: 'right' }}>
                        {epic.roleProgress.dev.estimateSeconds
                          ? Math.min(100, Math.round((epic.roleProgress.dev.loggedSeconds || 0) * 100 / epic.roleProgress.dev.estimateSeconds))
                          : 0}%
                      </td>
                    </tr>
                  )}
                  {epic.roleProgress.qa && (
                    <tr>
                      <td style={{ padding: '3px 0' }}>
                        <span style={{ color: PHASE_COLORS.qa }}>●</span> QA
                        {epic.roleProgress.qa.completed && <span style={{ marginLeft: 4 }}>✓</span>}
                      </td>
                      <td style={{ color: '#B3BAC5', textAlign: 'right' }}>
                        {formatHours(epic.roleProgress.qa.loggedSeconds)} / {formatHours(epic.roleProgress.qa.estimateSeconds)}
                      </td>
                      <td style={{ color: '#8993A4', textAlign: 'right' }}>
                        {epic.roleProgress.qa.estimateSeconds
                          ? Math.min(100, Math.round((epic.roleProgress.qa.loggedSeconds || 0) * 100 / epic.roleProgress.qa.estimateSeconds))
                          : 0}%
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}

          {/* Footer stats */}
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#8993A4', fontSize: 11 }}>
            <span>📊 AutoScore: {epic.autoScore?.toFixed(0) || '—'}</span>
            <span>📋 Stories: {epic.storiesActive} активн. / {epic.storiesTotal} всего</span>
          </div>
        </div>,
        document.body
      )}
    </>
  )
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

              <span
                style={{
                  position: 'absolute',
                  left: '50%',
                  top: '50%',
                  transform: 'translate(-50%, -50%)',
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'white',
                  textShadow: '0 1px 2px rgba(0,0,0,0.6)',
                  zIndex: 2,
                  cursor: 'pointer'
                }}
                onClick={(e) => {
                  e.stopPropagation()
                  window.open(`${jiraBaseUrl}${story.storyKey}`, '_blank')
                }}
              >
                {storyNumber}{hasWarning ? ' ⚠' : ''}
              </span>
            </div>
          )
        })}
      </div>

      {hoveredStory && createPortal(
        <div
          className="timeline-tooltip"
          style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            transform: 'translate(-50%, -100%)',
            zIndex: 10000,
            pointerEvents: 'none',
            background: 'rgba(0,0,0,0.92)',
            borderRadius: '8px',
            padding: '12px 14px',
            minWidth: '300px',
            maxWidth: '420px',
            boxShadow: '0 4px 16px rgba(0,0,0,0.4)',
            color: 'white',
            fontSize: '13px'
          }}
        >
          {/* Header: Type icon + Key + AutoScore + Status */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <img
                src={getIssueTypeIcon(hoveredStory.issueType)}
                alt={hoveredStory.issueType || 'Story'}
                style={{ width: '16px', height: '16px' }}
              />
              <a
                href={`${jiraBaseUrl}${hoveredStory.storyKey}`}
                target="_blank"
                rel="noopener noreferrer"
                style={{ pointerEvents: 'auto', color: '#60a5fa', textDecoration: 'none', fontWeight: 600 }}
              >
                {hoveredStory.storyKey}
              </a>
              {hoveredStory.autoScore !== null && (
                <span style={{ color: '#9ca3af', fontSize: '12px' }}>({hoveredStory.autoScore?.toFixed(0)})</span>
              )}
              {hoveredStory.flagged && (
                <span style={{ color: '#f97316' }} title="Flagged">🚩</span>
              )}
            </div>
            <span
              style={{
                fontSize: '11px',
                padding: '2px 8px',
                borderRadius: '4px',
                background: getStatusColor(hoveredStory.status).bg,
                color: getStatusColor(hoveredStory.status).text,
                fontWeight: 500
              }}
            >
              {hoveredStory.status}
            </span>
          </div>

          {/* Summary */}
          <div style={{ color: '#d1d5db', marginBottom: '10px', fontSize: '12px', lineHeight: 1.4 }}>
            {hoveredStory.summary || 'No summary'}
          </div>

          {/* Progress bar */}
          {hoveredStory.totalEstimateSeconds && hoveredStory.totalEstimateSeconds > 0 && (
            <div style={{ marginBottom: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px', fontSize: '12px' }}>
                <span style={{ color: '#9ca3af' }}>Прогресс</span>
                <span style={{ color: '#e5e7eb' }}>
                  {formatHours(hoveredStory.totalLoggedSeconds)} / {formatHours(hoveredStory.totalEstimateSeconds)}
                  <span style={{ color: '#9ca3af', marginLeft: '6px' }}>
                    ({hoveredStory.progressPercent ?? 0}%)
                  </span>
                </span>
              </div>
              <div style={{ height: '6px', background: '#374151', borderRadius: '3px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    width: `${hoveredStory.progressPercent ?? 0}%`,
                    background: (hoveredStory.progressPercent ?? 0) > 100 ? '#ef4444' : '#22c55e',
                    borderRadius: '3px',
                    transition: 'width 0.2s'
                  }}
                />
              </div>
            </div>
          )}

          {/* Dates */}
          {hoveredStory.startDate && hoveredStory.endDate && (
            <div style={{ marginBottom: '10px', color: '#9ca3af', fontSize: '12px' }}>
              📅 {formatDateShort(new Date(hoveredStory.startDate))} → {formatDateShort(new Date(hoveredStory.endDate))}
            </div>
          )}

          {/* Phase breakdown with progress */}
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
            <tbody>
              {(phaseHasHours(hoveredStory.phases?.sa) || hoveredStory.roleProgress?.sa) && (
                <tr>
                  <td style={{ padding: '3px 4px' }}>
                    <span style={{ color: PHASE_COLORS.sa }}>●</span> SA
                    {hoveredStory.roleProgress?.sa?.completed && (
                      <span style={{ color: '#22c55e', marginLeft: '4px' }}>✓</span>
                    )}
                  </td>
                  <td style={{ padding: '3px 4px', color: '#d1d5db' }}>{hoveredStory.phases?.sa?.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '3px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                    {hoveredStory.roleProgress?.sa ? (
                      <span>
                        {formatHours(hoveredStory.roleProgress.sa.loggedSeconds)}/{formatHours(hoveredStory.roleProgress.sa.estimateSeconds)}
                      </span>
                    ) : (
                      <span>{hoveredStory.phases?.sa?.hours.toFixed(0)}ч</span>
                    )}
                  </td>
                </tr>
              )}
              {(phaseHasHours(hoveredStory.phases?.dev) || hoveredStory.roleProgress?.dev) && (
                <tr>
                  <td style={{ padding: '3px 4px' }}>
                    <span style={{ color: PHASE_COLORS.dev }}>●</span> DEV
                    {hoveredStory.roleProgress?.dev?.completed && (
                      <span style={{ color: '#22c55e', marginLeft: '4px' }}>✓</span>
                    )}
                  </td>
                  <td style={{ padding: '3px 4px', color: '#d1d5db' }}>{hoveredStory.phases?.dev?.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '3px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                    {hoveredStory.roleProgress?.dev ? (
                      <span>
                        {formatHours(hoveredStory.roleProgress.dev.loggedSeconds)}/{formatHours(hoveredStory.roleProgress.dev.estimateSeconds)}
                      </span>
                    ) : (
                      <span>{hoveredStory.phases?.dev?.hours.toFixed(0)}ч</span>
                    )}
                  </td>
                </tr>
              )}
              {(phaseHasHours(hoveredStory.phases?.qa) || hoveredStory.roleProgress?.qa) && (
                <tr>
                  <td style={{ padding: '3px 4px' }}>
                    <span style={{ color: PHASE_COLORS.qa }}>●</span> QA
                    {hoveredStory.roleProgress?.qa?.completed && (
                      <span style={{ color: '#22c55e', marginLeft: '4px' }}>✓</span>
                    )}
                  </td>
                  <td style={{ padding: '3px 4px', color: '#d1d5db' }}>{hoveredStory.phases?.qa?.assigneeDisplayName || '-'}</td>
                  <td style={{ padding: '3px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                    {hoveredStory.roleProgress?.qa ? (
                      <span>
                        {formatHours(hoveredStory.roleProgress.qa.loggedSeconds)}/{formatHours(hoveredStory.roleProgress.qa.estimateSeconds)}
                      </span>
                    ) : (
                      <span>{hoveredStory.phases?.qa?.hours.toFixed(0)}ч</span>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          {/* Blocked by */}
          {hoveredStory.blockedBy && hoveredStory.blockedBy.length > 0 && (
            <div style={{ color: '#f87171', marginTop: '10px', fontSize: '12px', borderTop: '1px solid #374151', paddingTop: '8px' }}>
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
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')

  // Historical snapshot state
  const [availableDates, setAvailableDates] = useState<string[]>([])
  const [selectedHistoricalDate, setSelectedHistoricalDate] = useState<string>('') // empty = live data
  const [isHistoricalMode, setIsHistoricalMode] = useState(false)

  const chartRef = useRef<HTMLDivElement>(null)

  // Load config and teams
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))

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

  // Load available snapshot dates when team changes
  useEffect(() => {
    if (!selectedTeamId) return

    getAvailableSnapshotDates(selectedTeamId)
      .then(dates => {
        setAvailableDates(dates)
        // Reset historical date selection when team changes
        setSelectedHistoricalDate('')
        setIsHistoricalMode(false)
      })
      .catch(err => console.error('Failed to load snapshot dates:', err))
  }, [selectedTeamId])

  // Load data when team or historical date changes
  useEffect(() => {
    if (!selectedTeamId) return

    setLoading(true)
    setError(null)

    if (selectedHistoricalDate && isHistoricalMode) {
      // Load from historical snapshot
      Promise.all([
        getForecastSnapshot(selectedTeamId, selectedHistoricalDate),
        getUnifiedPlanningSnapshot(selectedTeamId, selectedHistoricalDate)
      ])
        .then(([forecastData, planData]) => {
          setForecast(forecastData)
          setUnifiedPlan(planData)
          setLoading(false)
        })
        .catch(err => {
          setError('Failed to load historical snapshot: ' + err.message)
          setLoading(false)
        })
    } else {
      // Load live data
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
    }
  }, [selectedTeamId, selectedHistoricalDate, isHistoricalMode])

  // Handle historical date selection
  const handleHistoricalDateChange = (date: string) => {
    if (date === '') {
      setSelectedHistoricalDate('')
      setIsHistoricalMode(false)
    } else {
      setSelectedHistoricalDate(date)
      setIsHistoricalMode(true)
    }
  }

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

        <div className="filter-group">
          <label className="filter-label">
            Дата
            {isHistoricalMode && (
              <span style={{ marginLeft: 6, fontSize: 10, color: '#f97316' }}>📜 Исторические данные</span>
            )}
          </label>
          <select
            className="filter-input"
            value={selectedHistoricalDate}
            onChange={e => handleHistoricalDateChange(e.target.value)}
            style={{ minWidth: 140 }}
          >
            <option value="">Сегодня (live)</option>
            {availableDates.map(date => (
              <option key={date} value={date}>
                {new Date(date).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' })}
              </option>
            ))}
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

      {isHistoricalMode && (
        <div className="historical-mode-banner">
          📜 Просмотр исторического снэпшота от {new Date(selectedHistoricalDate).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' })}
          <button
            onClick={() => handleHistoricalDateChange('')}
            style={{ marginLeft: 12, padding: '2px 8px', fontSize: 12, cursor: 'pointer' }}
          >
            Вернуться к текущим данным
          </button>
        </div>
      )}

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
              const rowHeight = rowHeights.get(epic.epicKey) || MIN_ROW_HEIGHT

              return (
                <EpicLabel
                  key={epic.epicKey}
                  epic={epic}
                  epicForecast={epicForecast}
                  jiraBaseUrl={jiraBaseUrl}
                  rowHeight={rowHeight}
                />
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
