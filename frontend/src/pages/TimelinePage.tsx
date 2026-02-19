import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useSearchParams } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, UnifiedPlanningResult, PlannedStory, PlannedEpic, UnifiedPhaseSchedule, PlanningWarning, getAvailableSnapshotDates, getUnifiedPlanningSnapshot, getForecastSnapshot, getRetrospective, RetroEpic } from '../api/forecast'
import { getConfig } from '../api/config'
import { getStatusStyles, StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { useStatusStyles } from '../components/board/StatusStylesContext'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import './TimelinePage.css'

import { getIssueIcon } from '../components/board/helpers'

type ZoomLevel = 'day' | 'week' | 'month'
type TimelineMode = 'forecast' | 'retrospective'

// Width per unit in pixels for each zoom level
const ZOOM_UNIT_WIDTH: Record<ZoomLevel, number> = {
  day: 40,    // 40px per day - detailed view
  week: 120,  // 120px per week - default view
  month: 100  // 100px per month - overview
}

interface DateRange {
  start: Date
  end: Date
}

// --- Utility functions ---

function formatDateShort(date: Date): string {
  return date.toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' })
}

function toLocalMidnight(date: Date): Date {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

function daysBetween(start: Date, end: Date): number {
  const s = toLocalMidnight(start)
  const e = toLocalMidnight(end)
  return Math.round((e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24))
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
      // For rough estimate epics, use epic dates directly
      if (epic.isRoughEstimate) {
        if (epic.startDate) {
          const d = new Date(epic.startDate)
          if (d < minDate) minDate = d
        }
        if (epic.endDate) {
          const d = new Date(epic.endDate)
          if (d > maxDate) maxDate = d
        }
      } else {
        // For regular epics, use story dates
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

  // Align to week boundaries so date % matches header grid exactly
  minDate = startOfWeek(addDays(minDate, -3))
  const paddedMax = addDays(maxDate, 7)
  const numWeeks = Math.ceil(daysBetween(minDate, paddedMax) / 7)
  maxDate = addDays(minDate, numWeeks * 7)

  return { start: minDate, end: maxDate }
}

interface TimelineHeader {
  date: Date
  label: string
}

interface GroupHeader {
  label: string
  span: number  // number of columns this group spans
}

function generateTimelineHeaders(range: DateRange, zoom: ZoomLevel): TimelineHeader[] {
  const headers: TimelineHeader[] = []
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

// Generate group headers (month for day/week zoom, quarter for month zoom)
function generateGroupHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (headers.length === 0) return []

  const groups: GroupHeader[] = []

  if (zoom === 'day' || zoom === 'week') {
    // Group by month
    let currentMonth = -1
    let currentYear = -1
    let currentSpan = 0

    for (const header of headers) {
      const month = header.date.getMonth()
      const year = header.date.getFullYear()

      if (month !== currentMonth || year !== currentYear) {
        if (currentSpan > 0) {
          groups.push({
            label: new Date(currentYear, currentMonth, 1).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' }),
            span: currentSpan
          })
        }
        currentMonth = month
        currentYear = year
        currentSpan = 1
      } else {
        currentSpan++
      }
    }

    // Add last group
    if (currentSpan > 0) {
      groups.push({
        label: new Date(currentYear, currentMonth, 1).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' }),
        span: currentSpan
      })
    }
  } else {
    // Group by quarter for month zoom
    let currentQuarter = -1
    let currentYear = -1
    let currentSpan = 0

    for (const header of headers) {
      const quarter = Math.floor(header.date.getMonth() / 3) + 1
      const year = header.date.getFullYear()

      if (quarter !== currentQuarter || year !== currentYear) {
        if (currentSpan > 0) {
          groups.push({
            label: `Q${currentQuarter} ${currentYear}`,
            span: currentSpan
          })
        }
        currentQuarter = quarter
        currentYear = year
        currentSpan = 1
      } else {
        currentSpan++
      }
    }

    // Add last group
    if (currentSpan > 0) {
      groups.push({
        label: `Q${currentQuarter} ${currentYear}`,
        span: currentSpan
      })
    }
  }

  return groups
}

// Generate week headers for day zoom (shows week numbers)
function generateWeekHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (zoom !== 'day' || headers.length === 0) return []

  const weeks: GroupHeader[] = []
  let currentWeekStart: Date | null = null
  let currentSpan = 0

  for (const header of headers) {
    const weekStart = startOfWeek(header.date)

    if (!currentWeekStart || weekStart.getTime() !== currentWeekStart.getTime()) {
      if (currentSpan > 0 && currentWeekStart) {
        weeks.push({
          label: `Нед ${getWeekNumber(currentWeekStart)}`,
          span: currentSpan
        })
      }
      currentWeekStart = weekStart
      currentSpan = 1
    } else {
      currentSpan++
    }
  }

  // Add last week
  if (currentSpan > 0 && currentWeekStart) {
    weeks.push({
      label: `Нед ${getWeekNumber(currentWeekStart)}`,
      span: currentSpan
    })
  }

  return weeks
}

// Get ISO week number
function getWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)
}

// Check if a date is a weekend (Saturday or Sunday)
function isWeekend(date: Date): boolean {
  const day = date.getDay()
  return day === 0 || day === 6
}

// Helper to lighten a hex color by a factor (0=original, 1=white)
function lightenColor(hex: string, factor: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lr = Math.round(r + (255 - r) * factor)
  const lg = Math.round(g + (255 - g) * factor)
  const lb = Math.round(b + (255 - b) * factor)
  return `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
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

// Allocate lanes for stories - each story gets its own lane in priority order
// Stories come pre-sorted by manual_order from backend (same as Board)
function allocateStoryLanes(stories: PlannedStory[]): Map<string, number> {
  const lanes = new Map<string, number>()

  // Filter active stories with dates
  const activeStories = stories.filter(s => s.startDate && s.endDate)

  // Each story gets lane = its index (preserving backend order = priority)
  activeStories.forEach((story, index) => {
    lanes.set(story.storyKey, index)
  })

  return lanes
}

// Constants for layout
const BAR_HEIGHT = 22
const LANE_GAP = 3
const MIN_ROW_HEIGHT = 48

// Calculate row height for an epic based on its stories
// Each story gets its own lane, so height = number of active stories
function calculateRowHeight(epic: PlannedEpic): number {
  // For rough estimate epics, just one bar
  if (epic.isRoughEstimate) {
    return MIN_ROW_HEIGHT
  }

  const activeStories = epic.stories.filter(s => {
    const isDone = s.status?.toLowerCase().includes('готов') || s.status?.toLowerCase().includes('done')
    const hasPhases = s.phases && Object.keys(s.phases).length > 0
    return !isDone && hasPhases && s.startDate && s.endDate
  })

  if (activeStories.length === 0) return MIN_ROW_HEIGHT

  // Each story on its own lane
  const numLanes = activeStories.length
  return Math.max(MIN_ROW_HEIGHT, numLanes * (BAR_HEIGHT + LANE_GAP) + 8)
}

// --- Status color helper (uses StatusStylesContext data) ---
function getContrastColor(hex: string): string {
  const c = hex.replace('#', '')
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return luminance > 0.6 ? '#172b4d' : '#ffffff'
}

function getStatusColor(
  status: string | null,
  styles: Record<string, StatusStyle>
): { bg: string; text: string } {
  const fallback = { bg: '#dfe1e6', text: '#42526e' }
  if (!status) return fallback
  const style = styles[status]
  if (style?.color) return { bg: style.color, text: getContrastColor(style.color) }
  return fallback
}

// --- Epic Label Component with Tooltip ---
interface EpicLabelProps {
  epic: PlannedEpic
  epicForecast: EpicForecast | undefined
  jiraBaseUrl: string
  rowHeight: number
}

function EpicLabel({ epic, epicForecast, jiraBaseUrl, rowHeight }: EpicLabelProps) {
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl } = useWorkflowConfig()
  const statusStyles = useStatusStyles()
  const epicIconUrl = getIssueIcon('Epic', getIssueTypeIconUrl('Epic') || getIssueTypeIconUrl('Эпик'))
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })
  const labelRef = useRef<HTMLDivElement>(null)

  const handleMouseEnter = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltipPos({ x: rect.right + 8, y: rect.top })
    setShowTooltip(true)
  }

  const statusColor = getStatusColor(epic.status, statusStyles)
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
            <img src={epicIconUrl} alt="Epic" style={{ width: 16, height: 16 }} />
            <a
              href={`${jiraBaseUrl}${epic.epicKey}`}
              target="_blank"
              rel="noopener noreferrer"
              className="issue-key"
            >
              {epic.epicKey}
            </a>
            {dueDateIndicator}
            {epic.flagged && <span style={{ color: '#f97316', fontSize: 14 }} title="Flagged">🚩</span>}
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
              <img src={epicIconUrl} alt="Epic" style={{ width: 16, height: 16 }} />
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
                  {getRoleCodes()
                    .filter(role => epic.roleProgress![role])
                    .map(role => { const progress = epic.roleProgress![role]; return progress && (
                    <tr key={role}>
                      <td style={{ padding: '3px 0', width: 50 }}>
                        <span style={{ color: getRoleColor(role) }}>●</span> {role}
                        {progress.completed && <span style={{ marginLeft: 4 }}>✓</span>}
                      </td>
                      <td style={{ color: '#B3BAC5', textAlign: 'right' }}>
                        {formatHours(progress.loggedSeconds)} / {formatHours(progress.estimateSeconds)}
                      </td>
                      <td style={{ color: '#8993A4', textAlign: 'right', width: 45 }}>
                        {progress.estimateSeconds
                          ? Math.min(100, Math.round((progress.loggedSeconds || 0) * 100 / progress.estimateSeconds))
                          : 0}%
                      </td>
                    </tr>
                  ); })}
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

// --- Story Bar Component ---
interface StoryBarProps {
  story: PlannedStory
  lane: number
  dateRange: DateRange
  jiraBaseUrl: string
  globalWarnings: PlanningWarning[]
  onHover: (story: PlannedStory | null, pos?: { x: number; y: number }) => void
}

function StoryBar({ story, lane, dateRange, jiraBaseUrl, globalWarnings, onHover }: StoryBarProps) {
  const { getRoleColor } = useWorkflowConfig()
  const totalDays = daysBetween(dateRange.start, dateRange.end)
  const startDate = new Date(story.startDate!)
  const endDate = new Date(story.endDate!)

  const daysFromStart = daysBetween(dateRange.start, startDate)
  const duration = daysBetween(startDate, endDate) + 1
  const leftPercent = (daysFromStart / totalDays) * 100
  const widthPercent = (duration / totalDays) * 100

  const storyNumber = story.storyKey.split('-')[1] || story.storyKey
  const isBlocked = story.blockedBy && story.blockedBy.length > 0
  const hasWarning = story.warnings?.length > 0 || globalWarnings?.some(w => w.issueKey === story.storyKey)

  const getPhaseColor = (role: string) => lightenColor(getRoleColor(role), 0.65)

  const renderPhaseSegment = (
    phase: UnifiedPhaseSchedule | null,
    phaseType: string
  ) => {
    if (!phase || !phase.startDate || !phase.endDate || phase.hours <= 0) return null

    const phaseStart = new Date(phase.startDate)
    const phaseEnd = new Date(phase.endDate)
    const phaseStartOffset = daysBetween(startDate, phaseStart)
    const phaseDuration = daysBetween(phaseStart, phaseEnd) + 1
    const phaseLeftPercent = Math.max(0, (phaseStartOffset / duration) * 100)
    const phaseWidthPercent = Math.min(100 - phaseLeftPercent, (phaseDuration / duration) * 100)

    return (
      <div
        key={phaseType}
        style={{
          position: 'absolute',
          left: `${phaseLeftPercent}%`,
          width: `${phaseWidthPercent}%`,
          height: '100%',
          backgroundColor: getPhaseColor(phaseType),
          opacity: phase.noCapacity ? 0.4 : 1
        }}
      />
    )
  }

  const handleMouseEnter = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    onHover(story, { x: rect.left + rect.width / 2, y: rect.top - 8 })
  }

  return (
    <div
      className="story-bar"
      style={{
        position: 'absolute',
        left: `${leftPercent}%`,
        width: `${Math.max(widthPercent, 1)}%`,
        top: `${lane * (BAR_HEIGHT + LANE_GAP)}px`,
        height: `${BAR_HEIGHT}px`,
        borderRadius: '4px',
        border: story.flagged ? '2px solid #f97316' : isBlocked ? '2px solid #ef4444' : '1px solid rgba(0,0,0,0.15)',
        overflow: 'hidden',
        background: '#e5e7eb',
        boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
      }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => onHover(null)}
    >
      {story.phases && Object.entries(story.phases).map(([role, phase]) =>
        renderPhaseSegment(phase, role)
      )}

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
          pointerEvents: 'auto',
          cursor: 'pointer'
        }}
        onClick={(e) => {
          e.stopPropagation()
          window.open(`${jiraBaseUrl}${story.storyKey}`, '_blank')
        }}
      >
        {storyNumber}{story.flagged ? ' 🚩' : ''}{hasWarning ? ' ⚠' : ''}
      </span>
    </div>
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
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl } = useWorkflowConfig()
  const statusStyles = useStatusStyles()
  const [hoveredStory, setHoveredStory] = useState<PlannedStory | null>(null)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })

  // Filter active stories with phases
  const activeStories = stories.filter(s => {
    const isDone = s.status?.toLowerCase().includes('готов') || s.status?.toLowerCase().includes('done')
    const hasPhases = s.phases && Object.keys(s.phases).length > 0
    return !isDone && hasPhases && s.startDate && s.endDate
  })

  if (activeStories.length === 0) {
    return <div className="story-empty-text">Нет активных сторей</div>
  }

  const storyLanes = allocateStoryLanes(activeStories)
  const maxLane = Math.max(0, ...Array.from(storyLanes.values())) + 1
  const containerHeight = maxLane * (BAR_HEIGHT + LANE_GAP)

  const handleHover = (story: PlannedStory | null, pos?: { x: number; y: number }) => {
    setHoveredStory(story)
    if (pos) setTooltipPos(pos)
  }

  return (
    <>
      <div style={{ height: `${containerHeight}px`, position: 'relative', width: '100%' }}>
        {activeStories.map((story, index) => (
          <StoryBar
            key={story.storyKey}
            story={story}
            lane={index}
            dateRange={dateRange}
            jiraBaseUrl={jiraBaseUrl}
            globalWarnings={globalWarnings}
            onHover={handleHover}
          />
        ))}
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
                src={getIssueIcon(hoveredStory.issueType || 'Story', getIssueTypeIconUrl(hoveredStory.issueType))}
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
                background: getStatusColor(hoveredStory.status, statusStyles).bg,
                color: getStatusColor(hoveredStory.status, statusStyles).text,
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
              {(() => {
                // Collect all roles from phases and roleProgress, ordered by config
                const dataRoles = new Set<string>()
                if (hoveredStory.phases) Object.keys(hoveredStory.phases).forEach(r => dataRoles.add(r))
                if (hoveredStory.roleProgress) Object.keys(hoveredStory.roleProgress).forEach(r => dataRoles.add(r))
                const configOrder = getRoleCodes()
                const sortedRoles = configOrder.filter(r => dataRoles.has(r))
                // Append any roles not in config at the end
                dataRoles.forEach(r => { if (!configOrder.includes(r)) sortedRoles.push(r) })
                return sortedRoles.map(role => {
                  const phase = hoveredStory.phases?.[role]
                  const progress = hoveredStory.roleProgress?.[role]
                  if (!phaseHasHours(phase) && !progress) return null
                  return (
                    <tr key={role}>
                      <td style={{ padding: '3px 4px' }}>
                        <span style={{ color: getRoleColor(role) }}>●</span> {role}
                        {progress?.completed && (
                          <span style={{ color: '#22c55e', marginLeft: '4px' }}>✓</span>
                        )}
                      </td>
                      <td style={{ padding: '3px 4px', color: '#d1d5db' }}>{phase?.assigneeDisplayName || '-'}</td>
                      <td style={{ padding: '3px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                        {progress ? (
                          <span>
                            {formatHours(progress.loggedSeconds)}/{formatHours(progress.estimateSeconds)}
                          </span>
                        ) : (
                          <span>{phase?.hours.toFixed(0)}ч</span>
                        )}
                      </td>
                    </tr>
                  )
                })
              })()}
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

// --- Rough Estimate Bar Component (for epics without stories) ---
interface RoughEstimateBarProps {
  epic: PlannedEpic
  dateRange: DateRange
  jiraBaseUrl: string
  onHover: (epic: PlannedEpic | null, pos?: { x: number; y: number }) => void
}

function RoughEstimateBar({ epic, dateRange, jiraBaseUrl, onHover }: RoughEstimateBarProps) {
  const { getRoleColor } = useWorkflowConfig()
  const totalDays = daysBetween(dateRange.start, dateRange.end)
  const agg = epic.phaseAggregation

  // Calculate overall bar position from epic dates
  if (!epic.startDate || !epic.endDate) return null

  const startDate = new Date(epic.startDate)
  const endDate = new Date(epic.endDate)
  const daysFromStart = daysBetween(dateRange.start, startDate)
  const duration = daysBetween(startDate, endDate) + 1
  const leftPercent = (daysFromStart / totalDays) * 100
  const widthPercent = (duration / totalDays) * 100

  const getPhaseColorDimmed = (role: string) => lightenColor(getRoleColor(role), 0.8)

  // Calculate phase segments within the bar
  const renderPhaseSegment = (
    phaseStartDate: string | null,
    phaseEndDate: string | null,
    phaseType: string
  ) => {
    if (!phaseStartDate || !phaseEndDate) return null

    const phaseStart = new Date(phaseStartDate)
    const phaseEnd = new Date(phaseEndDate)
    const phaseStartOffset = daysBetween(startDate, phaseStart)
    const phaseDuration = daysBetween(phaseStart, phaseEnd) + 1
    const phaseLeftPercent = Math.max(0, (phaseStartOffset / duration) * 100)
    const phaseWidthPercent = Math.min(100 - phaseLeftPercent, (phaseDuration / duration) * 100)

    return (
      <div
        key={phaseType}
        style={{
          position: 'absolute',
          left: `${phaseLeftPercent}%`,
          width: `${phaseWidthPercent}%`,
          height: '100%',
          backgroundColor: getPhaseColorDimmed(phaseType),
          backgroundImage: `repeating-linear-gradient(
            135deg,
            transparent,
            transparent 3px,
            rgba(255,255,255,0.4) 3px,
            rgba(255,255,255,0.4) 6px
          )`,
        }}
      />
    )
  }

  const handleMouseEnter = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect()
    onHover(epic, { x: rect.left + rect.width / 2, y: rect.top - 8 })
  }

  return (
    <div
      className="rough-estimate-bar"
      style={{
        position: 'absolute',
        left: `${leftPercent}%`,
        width: `${Math.max(widthPercent, 1)}%`,
        top: '0px',
        height: `${BAR_HEIGHT}px`,
        borderRadius: '4px',
        border: '1px dashed rgba(0,0,0,0.3)',
        overflow: 'hidden',
        background: '#f0f0f0',
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
        cursor: 'pointer',
      }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => onHover(null)}
      onClick={() => window.open(`${jiraBaseUrl}${epic.epicKey}`, '_blank')}
    >
      {agg && Object.entries(agg).map(([role, entry]) =>
        renderPhaseSegment(entry.startDate, entry.endDate, role)
      )}

      <span
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          transform: 'translate(-50%, -50%)',
          fontSize: '10px',
          fontWeight: 600,
          color: '#666',
          textShadow: '0 1px 1px rgba(255,255,255,0.8)',
          zIndex: 2,
          whiteSpace: 'nowrap',
        }}
      >
        ~{epic.roughEstimates
          ? Object.entries(epic.roughEstimates).map(([role, days]) => `${role}:${days}`).join('/')
          : '0'}д
      </span>
    </div>
  )
}

// --- Rough Estimate Bars Container ---
interface RoughEstimateBarsProps {
  epic: PlannedEpic
  dateRange: DateRange
  jiraBaseUrl: string
}

function RoughEstimateBars({ epic, dateRange, jiraBaseUrl }: RoughEstimateBarsProps) {
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl } = useWorkflowConfig()
  const epicIconUrl = getIssueIcon('Epic', getIssueTypeIconUrl('Epic') || getIssueTypeIconUrl('Эпик'))
  const [hoveredEpic, setHoveredEpic] = useState<PlannedEpic | null>(null)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })

  const handleHover = (ep: PlannedEpic | null, pos?: { x: number; y: number }) => {
    setHoveredEpic(ep)
    if (pos) setTooltipPos(pos)
  }

  const containerHeight = BAR_HEIGHT + LANE_GAP

  return (
    <>
      <div style={{ height: `${containerHeight}px`, position: 'relative', width: '100%' }}>
        <RoughEstimateBar
          epic={epic}
          dateRange={dateRange}
          jiraBaseUrl={jiraBaseUrl}
          onHover={handleHover}
        />
      </div>

      {hoveredEpic && createPortal(
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
            minWidth: '280px',
            maxWidth: '380px',
            boxShadow: '0 4px 16px rgba(0,0,0,0.4)',
            color: 'white',
            fontSize: '13px'
          }}
        >
          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <img src={epicIconUrl} alt="Epic" style={{ width: '16px', height: '16px' }} />
              <span style={{ fontWeight: 600, color: '#60a5fa' }}>{hoveredEpic.epicKey}</span>
            </div>
            <span
              style={{
                fontSize: '10px',
                padding: '2px 6px',
                borderRadius: '4px',
                background: '#fef3c7',
                color: '#92400e',
                fontWeight: 500
              }}
            >
              Грязные оценки
            </span>
          </div>

          {/* Summary */}
          <div style={{ color: '#d1d5db', marginBottom: '10px', fontSize: '12px', lineHeight: 1.4 }}>
            {hoveredEpic.summary}
          </div>

          {/* Rough estimates breakdown */}
          {hoveredEpic.roughEstimates && Object.keys(hoveredEpic.roughEstimates).length > 0 && (
            <div style={{ marginBottom: '10px', borderTop: '1px solid #374151', paddingTop: '10px' }}>
              <div style={{ color: '#9ca3af', fontSize: '11px', marginBottom: '6px' }}>
                Оценки (дней):
              </div>
              <table style={{ width: '100%', fontSize: '12px' }}>
                <tbody>
                  {getRoleCodes()
                    .filter(role => (hoveredEpic.roughEstimates?.[role] || 0) > 0)
                    .map(role => (
                    <tr key={role}>
                      <td style={{ padding: '2px 4px' }}>
                        <span style={{ color: getRoleColor(role) }}>●</span> {role}
                      </td>
                      <td style={{ padding: '2px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                        {hoveredEpic.roughEstimates?.[role]} дней
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Dates */}
          {hoveredEpic.startDate && hoveredEpic.endDate && (
            <div style={{ color: '#9ca3af', fontSize: '12px' }}>
              📅 {formatDateShort(new Date(hoveredEpic.startDate))} → {formatDateShort(new Date(hoveredEpic.endDate))}
            </div>
          )}

          {/* Note */}
          <div style={{ marginTop: '8px', color: '#6b7280', fontSize: '11px', fontStyle: 'italic' }}>
            Эпик без сторей, планируется по грязным оценкам
          </div>
        </div>,
        document.body
      )}
    </>
  )
}

// --- Retrospective Adapter ---
// Converts RetroEpic[] to PlannedEpic[] so existing Gantt rendering works unchanged
function retroToPlannedEpics(retroEpics: RetroEpic[]): PlannedEpic[] {
  const today = new Date().toISOString().split('T')[0]
  return retroEpics.map(epic => {
    const stories: PlannedStory[] = epic.stories.map(story => {
      const phases: Record<string, UnifiedPhaseSchedule> = {}
      for (const [role, phase] of Object.entries(story.phases)) {
        phases[role] = {
          assigneeAccountId: null,
          assigneeDisplayName: null,
          startDate: phase.startDate,
          endDate: phase.endDate ?? (phase.active ? today : null),
          hours: phase.durationDays * 8,
          noCapacity: false,
        }
      }
      return {
        storyKey: story.storyKey,
        summary: story.summary,
        autoScore: null,
        status: story.status,
        startDate: story.startDate,
        endDate: story.endDate ?? (story.completed ? story.startDate : today),
        phases,
        blockedBy: [],
        warnings: [],
        issueType: null,
        priority: null,
        flagged: null,
        totalEstimateSeconds: null,
        totalLoggedSeconds: null,
        progressPercent: story.progressPercent,
        roleProgress: null,
      }
    })

    return {
      epicKey: epic.epicKey,
      summary: epic.summary,
      autoScore: 0,
      startDate: epic.startDate,
      endDate: epic.endDate ?? today,
      stories,
      phaseAggregation: {},
      status: epic.status,
      dueDate: null,
      totalEstimateSeconds: null,
      totalLoggedSeconds: null,
      progressPercent: epic.progressPercent,
      roleProgress: null,
      storiesTotal: epic.stories.length,
      storiesActive: epic.stories.filter(s => !s.completed).length,
      isRoughEstimate: false,
      roughEstimates: null,
      flagged: null,
    }
  })
}

// --- Gantt Row ---
interface GanttRowProps {
  plannedEpic: PlannedEpic
  stories: PlannedStory[]
  globalWarnings: PlanningWarning[]
  dateRange: DateRange
  jiraBaseUrl: string
  rowHeight: number
  epicIndex: number
  shouldAnimate: boolean
}

function GanttRow({ plannedEpic, stories, globalWarnings, dateRange, jiraBaseUrl, rowHeight, epicIndex, shouldAnimate }: GanttRowProps) {
  const totalDays = daysBetween(dateRange.start, dateRange.end)

  // Due date line calculation
  const dueDate = plannedEpic.dueDate ? new Date(plannedEpic.dueDate) : null
  const dueDateOffset = dueDate ? daysBetween(dateRange.start, dueDate) : null
  const dueDatePercent = dueDateOffset !== null ? (dueDateOffset / totalDays) * 100 : null

  // Animation delay based on epic row index (150ms between each row)
  const animationStyle = shouldAnimate ? {
    animationDelay: `${epicIndex * 150}ms`
  } : {}

  // Check if this is a rough estimate epic (no stories, uses rough estimates)
  const isRoughEstimate = plannedEpic.isRoughEstimate

  return (
    <div className="gantt-row" style={{ height: `${rowHeight}px` }}>
      <div
        className={`gantt-row-content ${shouldAnimate ? 'gantt-row-animate' : ''}`}
        style={{ position: 'relative', padding: '4px 0', ...animationStyle }}
      >
        {/* Due date line */}
        {dueDatePercent !== null && dueDatePercent >= 0 && dueDatePercent <= 100 && (
          <div className="gantt-due-line" style={{ left: `${dueDatePercent}%` }} />
        )}

        {/* Rough estimate bar OR Story bars */}
        {isRoughEstimate ? (
          <RoughEstimateBars
            epic={plannedEpic}
            dateRange={dateRange}
            jiraBaseUrl={jiraBaseUrl}
          />
        ) : (
          <StoryBars
            stories={stories}
            dateRange={dateRange}
            jiraBaseUrl={jiraBaseUrl}
            globalWarnings={globalWarnings}
          />
        )}
      </div>
    </div>
  )
}

// --- Main component ---

export function TimelinePage() {
  const { getRoleColor, getRoleCodes } = useWorkflowConfig()
  const [searchParams, setSearchParams] = useSearchParams()
  const [teams, setTeams] = useState<Team[]>([])
  const [forecast, setForecast] = useState<ForecastResponse | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  // Sync teamId with URL
  const selectedTeamId = searchParams.get('teamId') ? Number(searchParams.get('teamId')) : null
  const setSelectedTeamId = (id: number | null) => {
    if (id) {
      setSearchParams({ teamId: String(id) })
    } else {
      setSearchParams({})
    }
  }
  const [unifiedPlan, setUnifiedPlan] = useState<UnifiedPlanningResult | null>(null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [mode, setMode] = useState<TimelineMode>('forecast')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')

  // Historical snapshot state
  const [availableDates, setAvailableDates] = useState<string[]>([])
  const [selectedHistoricalDate, setSelectedHistoricalDate] = useState<string>('') // empty = live data
  const [isHistoricalMode, setIsHistoricalMode] = useState(false)

  // Animation state - animate only on fresh data load
  const [shouldAnimate, setShouldAnimate] = useState(false)
  const animationTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const chartRef = useRef<HTMLDivElement>(null)

  // Load config and teams (once on mount)
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))

    getStatusStyles().then(setStatusStyles).catch(() => {})

    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        // If no team in URL and teams available, select first one
        const urlTeamId = searchParams.get('teamId')
        if (activeTeams.length > 0 && !urlTeamId) {
          setSelectedTeamId(activeTeams[0].id)
        }
      })
      .catch(err => setError('Failed to load teams: ' + err.message))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Run once on mount, read URL synchronously

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

    // Clear any pending animation timeout
    if (animationTimeoutRef.current) {
      clearTimeout(animationTimeoutRef.current)
    }

    const triggerAnimation = () => {
      // Enable animation for fresh data
      setShouldAnimate(true)
      // Disable animation after all bars have animated (prevent re-animation on hover/tooltip)
      animationTimeoutRef.current = setTimeout(() => {
        setShouldAnimate(false)
      }, 2000) // 2 seconds should cover all staggered animations
    }

    if (mode === 'retrospective') {
      // Load retrospective data
      getRetrospective(selectedTeamId)
        .then(data => {
          // Convert to unified plan format for rendering
          const retroEpics = retroToPlannedEpics(data.epics)
          setUnifiedPlan({
            teamId: data.teamId,
            planningDate: data.calculatedAt,
            epics: retroEpics,
            warnings: [],
            assigneeUtilization: {},
          })
          setForecast(null)
          setLoading(false)
          triggerAnimation()
        })
        .catch(err => {
          setError('Failed to load retrospective: ' + err.message)
          setLoading(false)
        })
    } else if (selectedHistoricalDate && isHistoricalMode) {
      // Load from historical snapshot
      Promise.all([
        getForecastSnapshot(selectedTeamId, selectedHistoricalDate),
        getUnifiedPlanningSnapshot(selectedTeamId, selectedHistoricalDate)
      ])
        .then(([forecastData, planData]) => {
          setForecast(forecastData)
          setUnifiedPlan(planData)
          setLoading(false)
          triggerAnimation()
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
          triggerAnimation()
        })
        .catch(err => {
          setError('Failed to load data: ' + err.message)
          setLoading(false)
        })
    }

    return () => {
      if (animationTimeoutRef.current) {
        clearTimeout(animationTimeoutRef.current)
      }
    }
  }, [selectedTeamId, selectedHistoricalDate, isHistoricalMode, mode])

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

  // Generate group headers (month/quarter)
  const groupHeaders = useMemo(() => {
    return generateGroupHeaders(headers, zoom)
  }, [headers, zoom])

  // Generate week headers for day zoom
  const weekHeaders = useMemo(() => {
    return generateWeekHeaders(headers, zoom)
  }, [headers, zoom])

  // Calculate total chart width based on zoom level
  const chartWidth = useMemo(() => {
    return headers.length * ZOOM_UNIT_WIDTH[zoom]
  }, [headers.length, zoom])

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
      heights.set(epic.epicKey, calculateRowHeight(epic))
    }
    return heights
  }, [epics])

  // Calculate today line position
  const todayPercent = useMemo(() => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const todayOffset = daysBetween(dateRange.start, today)
    return (todayOffset / totalDays) * 100
  }, [dateRange])

  return (
    <StatusStylesProvider value={statusStyles}>
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
          <label className="filter-label">Режим</label>
          <div className="timeline-mode-toggle">
            <button
              className={`mode-toggle-btn ${mode === 'forecast' ? 'mode-toggle-active' : ''}`}
              onClick={() => setMode('forecast')}
            >
              Прогноз
            </button>
            <button
              className={`mode-toggle-btn ${mode === 'retrospective' ? 'mode-toggle-active' : ''}`}
              onClick={() => setMode('retrospective')}
            >
              Ретроспектива
            </button>
          </div>
        </div>

        {mode === 'forecast' && (
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
        )}

        <div className="timeline-legend">
          {getRoleCodes().map(code => (
            <span
              key={code}
              className="legend-item"
              style={{
                borderLeft: `3px solid ${lightenColor(getRoleColor(code), 0.5)}`,
                paddingLeft: 6,
              }}
            >
              {code}
            </span>
          ))}
          <span className="legend-item legend-today">Сегодня</span>
          <span className="legend-item legend-due">Due Date</span>
        </div>
      </div>

      {isHistoricalMode && mode === 'forecast' && (
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

      {mode === 'retrospective' && (
        <div className="retrospective-mode-banner">
          Ретроспективный таймлайн: фактическое прохождение стори через фазы на основе данных Jira.
          Активные стори отображаются до сегодняшнего дня.
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
            <div className="gantt-header-container" style={{ width: `${chartWidth}px`, minWidth: `${chartWidth}px` }}>
              {/* Group header row (month/quarter) */}
              <div className="gantt-header-group">
                {groupHeaders.map((group, i) => (
                  <div
                    key={i}
                    className="gantt-header-group-cell"
                    style={{ width: `${group.span * ZOOM_UNIT_WIDTH[zoom]}px`, minWidth: `${group.span * ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                  >
                    {group.label}
                  </div>
                ))}
              </div>
              {/* Week header row (only for day zoom) */}
              {zoom === 'day' && weekHeaders.length > 0 && (
                <div className="gantt-header-week">
                  {weekHeaders.map((week, i) => (
                    <div
                      key={i}
                      className="gantt-header-week-cell"
                      style={{ width: `${week.span * ZOOM_UNIT_WIDTH[zoom]}px`, minWidth: `${week.span * ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                    >
                      {week.label}
                    </div>
                  ))}
                </div>
              )}
              {/* Unit header row (day/week/month) */}
              <div className="gantt-header">
                {headers.map((header, i) => (
                  <div
                    key={i}
                    className={`gantt-header-cell ${zoom === 'day' && isWeekend(header.date) ? 'gantt-header-cell-weekend' : ''}`}
                    style={{ width: `${ZOOM_UNIT_WIDTH[zoom]}px`, minWidth: `${ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                  >
                    {header.label}
                  </div>
                ))}
              </div>
            </div>

            <div
              className="gantt-body"
              style={{
                width: `${chartWidth}px`,
                minWidth: `${chartWidth}px`,
                backgroundImage: `repeating-linear-gradient(to right, transparent, transparent ${ZOOM_UNIT_WIDTH[zoom] - 1}px, #ebecf0 ${ZOOM_UNIT_WIDTH[zoom] - 1}px, #ebecf0 ${ZOOM_UNIT_WIDTH[zoom]}px)`,
                backgroundSize: `${ZOOM_UNIT_WIDTH[zoom]}px 100%`,
                position: 'relative'
              }}
            >
              {/* Weekend columns overlay (only for day zoom) */}
              {zoom === 'day' && headers.map((header, i) => (
                isWeekend(header.date) && (
                  <div
                    key={`weekend-${i}`}
                    className="gantt-weekend-column"
                    style={{
                      left: `${i * ZOOM_UNIT_WIDTH[zoom]}px`,
                      width: `${ZOOM_UNIT_WIDTH[zoom]}px`
                    }}
                  />
                )
              ))}

              {/* Today line - single continuous line */}
              {todayPercent >= 0 && todayPercent <= 100 && (
                <div className="gantt-today-line" style={{ left: `${todayPercent}%` }} />
              )}

              {epics.map((epic, epicIndex) => {
                const rowHeight = rowHeights.get(epic.epicKey) || MIN_ROW_HEIGHT
                return (
                  <GanttRow
                    key={epic.epicKey}
                    plannedEpic={epic}
                    stories={epic.stories}
                    globalWarnings={unifiedPlan?.warnings || []}
                    dateRange={dateRange}
                    jiraBaseUrl={jiraBaseUrl}
                    rowHeight={rowHeight}
                    epicIndex={epicIndex}
                    shouldAnimate={shouldAnimate}
                  />
                )
              })}
            </div>
          </div>
        </div>
      )}
    </main>
    </StatusStylesProvider>
  )
}
