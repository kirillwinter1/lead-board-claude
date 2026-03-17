import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useSearchParams } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, UnifiedPlanningResult, PlannedStory, PlannedEpic, UnifiedPhaseSchedule, PlanningWarning, getAvailableSnapshotDates, getUnifiedPlanningSnapshot, getForecastSnapshot, getRetrospective, RetrospectiveResult, RetroStory, WorklogDay } from '../api/forecast'
import { getConfig } from '../api/config'
import { getStatusStyles, StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { useStatusStyles } from '../components/board/StatusStylesContext'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterBar } from '../components/FilterBar'
import type { FilterChip } from '../components/FilterChips'
import { GanttSkeleton } from '../components/skeletons'
import { getApiCache, setApiCache } from '../hooks/useApiCache'
import './TimelinePage.css'

import { getIssueIcon } from '../components/board/helpers'
import { lightenColor } from '../constants/colors'

type ZoomLevel = 'day' | 'week' | 'month'
type PhaseSource = 'retro' | 'forecast' | 'hybrid'
type TimelineCache = { forecast: ForecastResponse; unifiedPlan: UnifiedPlanningResult }

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
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
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

  // Use unified plan dates if available (hybrid data includes both retro and forecast)
  if (unifiedPlan) {
    for (const epic of unifiedPlan.epics) {
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
        label: current.toLocaleDateString('en-US', { month: 'short' })
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
            label: new Date(currentYear, currentMonth, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
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
        label: new Date(currentYear, currentMonth, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
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
          label: `Week ${getWeekNumber(currentWeekStart)}`,
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
      label: `Week ${getWeekNumber(currentWeekStart)}`,
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


// Format seconds to hours
function formatHours(seconds: number | null): string {
  if (seconds === null || seconds === 0) return '0h'
  return `${Math.round(seconds / 3600)}h`
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

  // Show all stories that have dates (including done stories with retro dates)
  const visibleStories = epic.stories.filter(s => {
    return s.startDate && s.endDate && s.phases && Object.keys(s.phases).length > 0
  })

  if (visibleStories.length === 0) return MIN_ROW_HEIGHT

  // Each story on its own lane
  const numLanes = visibleStories.length
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
  const epicIconUrl = getIssueIcon('Epic', getIssueTypeIconUrl('Epic'))
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })
  const labelRef = useRef<HTMLDivElement>(null)

  const handleMouseEnter = (e: React.MouseEvent) => {
    setTooltipPos({ x: e.clientX + 12, y: e.clientY + 12 })
    setShowTooltip(true)
  }
  const handleMouseMove = (e: React.MouseEvent) => {
    setTooltipPos({ x: e.clientX + 12, y: e.clientY + 12 })
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
        onMouseMove={handleMouseMove}
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
            {epic.flagged && <span style={{ fontSize: 9, fontWeight: 700, padding: '0 4px', borderRadius: 3, color: '#ff5630', backgroundColor: '#ffebe6', lineHeight: '16px' }} title="Flagged">FLG</span>}
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
              <span style={{ color: '#8993A4', fontSize: 11 }}>Progress</span>
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
                  {dueDateDelta !== null && dueDateDelta > 0 && ` (+${dueDateDelta}d)`}
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
            <span>📋 Stories: {epic.storiesActive} active / {epic.storiesTotal} total</span>
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

  // Determine story source for visual styling
  const storySource: PhaseSource = (story as PlannedStory & { _source?: PhaseSource })._source || 'forecast'
  const worklogDays: WorklogDay[] | null = (story as PlannedStory & { _worklogDays?: WorklogDay[] | null })._worklogDays || null

  const getPhaseColor = (role: string) => lightenColor(getRoleColor(role), 0.65)

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  // Render per-day worklog segments when worklog data is available
  const renderWorklogSegments = () => {
    if (!worklogDays || worklogDays.length === 0) return null

    // Build a map: date string -> dominant role (by most seconds)
    const dayRoleMap = new Map<string, { roleCode: string; totalSeconds: number }>()
    for (const wl of worklogDays) {
      const existing = dayRoleMap.get(wl.date)
      if (!existing || wl.timeSpentSeconds > existing.totalSeconds) {
        dayRoleMap.set(wl.date, { roleCode: wl.roleCode, totalSeconds: wl.timeSpentSeconds })
      }
    }

    const segments: React.ReactNode[] = []
    // Iterate over each day in the story range
    const current = new Date(startDate)
    let dayIndex = 0
    while (current <= endDate) {
      const dateStr = current.toISOString().split('T')[0]
      const dayData = dayRoleMap.get(dateStr)

      if (dayData) {
        const dayLeftPercent = (dayIndex / duration) * 100
        const dayWidthPercent = Math.max(1 / duration * 100, 1) // At least 1% width for visibility

        segments.push(
          <div
            key={dateStr}
            style={{
              position: 'absolute',
              left: `${dayLeftPercent}%`,
              width: `${dayWidthPercent}%`,
              height: '100%',
              backgroundColor: getPhaseColor(dayData.roleCode),
              opacity: 1
            }}
          />
        )
      }

      current.setDate(current.getDate() + 1)
      dayIndex++
    }

    return segments
  }

  const renderPhaseSegment = (
    phase: UnifiedPhaseSchedule | null,
    phaseType: string
  ) => {
    if (!phase || !phase.startDate || !phase.endDate) return null

    const phaseStart = new Date(phase.startDate)
    const phaseEnd = new Date(phase.endDate)
    const phaseStartOffset = daysBetween(startDate, phaseStart)
    const phaseDuration = daysBetween(phaseStart, phaseEnd) + 1
    const phaseLeftPercent = Math.max(0, (phaseStartOffset / duration) * 100)
    const phaseWidthPercent = Math.min(100 - phaseLeftPercent, (phaseDuration / duration) * 100)

    const color = getPhaseColor(phaseType)

    // Determine if this phase is retro (past), forecast (future), or hybrid (crosses today)
    const isForecast = storySource === 'forecast' || (storySource === 'hybrid' && phaseStart >= today)
    const isHybrid = storySource === 'hybrid' && phaseStart < today && phaseEnd >= today

    if (isHybrid) {
      // Split into solid (past) + striped (future) segments
      const pastDays = daysBetween(phaseStart, today)
      const totalPhaseDays = phaseDuration
      const pastPercent = (pastDays / totalPhaseDays) * phaseWidthPercent
      const futurePercent = phaseWidthPercent - pastPercent

      return (
        <div key={phaseType}>
          {/* Solid part (retro/actual) */}
          <div
            style={{
              position: 'absolute',
              left: `${phaseLeftPercent}%`,
              width: `${pastPercent}%`,
              height: '100%',
              backgroundColor: color,
              opacity: phase.noCapacity ? 0.4 : 1
            }}
          />
          {/* Striped part (forecast) */}
          <div
            className="phase-bar-forecast"
            style={{
              position: 'absolute',
              left: `${phaseLeftPercent + pastPercent}%`,
              width: `${futurePercent}%`,
              height: '100%',
              '--phase-color': color,
              opacity: phase.noCapacity ? 0.4 : 0.7
            } as React.CSSProperties}
          />
        </div>
      )
    }

    return (
      <div
        key={phaseType}
        className={isForecast ? 'phase-bar-forecast' : undefined}
        style={{
          position: 'absolute',
          left: `${phaseLeftPercent}%`,
          width: `${phaseWidthPercent}%`,
          height: '100%',
          ...(isForecast
            ? { '--phase-color': color, opacity: phase.noCapacity ? 0.4 : 0.7 } as React.CSSProperties
            : { backgroundColor: color, opacity: phase.noCapacity ? 0.4 : 1 })
        }}
      />
    )
  }

  const handleMouseEnter = (e: React.MouseEvent) => {
    onHover(story, { x: e.clientX, y: e.clientY - 12 })
  }

  return (
    <div
      className="story-bar"
      role="button"
      aria-label={`Story ${story.storyKey}`}
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
      onMouseMove={e => onHover(story, { x: e.clientX, y: e.clientY - 12 })}
      onMouseLeave={() => onHover(null)}
    >
      {/* Per-day worklog rendering for retro/hybrid stories with worklog data */}
      {(storySource === 'retro' || storySource === 'hybrid') && worklogDays && worklogDays.length > 0
        ? renderWorklogSegments()
        : story.phases && Object.entries(story.phases).map(([role, phase]) =>
            renderPhaseSegment(phase, role)
          )
      }

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
        {storyNumber}
        {story.flagged && <span style={{ marginLeft: 3, fontSize: 9, fontWeight: 700, padding: '0 3px', borderRadius: 3, color: '#ff5630', backgroundColor: '#ffebe6' }}>FLG</span>}
        {hasWarning && <span style={{ marginLeft: 3, fontSize: 9, fontWeight: 700, padding: '0 3px', borderRadius: 3, color: '#ff8b00', backgroundColor: '#fffae6' }}>!</span>}
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

  // Show all stories that have dates and phases (including done stories with retro data)
  const activeStories = stories.filter(s => {
    const hasPhases = s.phases && Object.keys(s.phases).length > 0
    return hasPhases && s.startDate && s.endDate
  })

  if (activeStories.length === 0) {
    return <div className="story-empty-text">No stories with data</div>
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
            left: tooltipPos.x + 12,
            top: tooltipPos.y + 12,
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
                <span style={{ fontSize: 9, fontWeight: 700, padding: '0 4px', borderRadius: 3, color: '#ff5630', backgroundColor: '#ffebe6', lineHeight: '16px' }} title="Flagged">FLG</span>
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
                <span style={{ color: '#9ca3af' }}>Progress</span>
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
                          <span>{phase?.hours.toFixed(0)}h</span>
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
    onHover(epic, { x: e.clientX, y: e.clientY - 12 })
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
      onMouseMove={e => onHover(epic, { x: e.clientX, y: e.clientY - 12 })}
      onMouseLeave={() => onHover(null)}
      onClick={() => window.open(`${jiraBaseUrl}${epic.epicKey}`, '_blank')}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') window.open(`${jiraBaseUrl}${epic.epicKey}`, '_blank') }}
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
          : '0'}d
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
  const epicIconUrl = getIssueIcon('Epic', getIssueTypeIconUrl('Epic'))
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
            left: tooltipPos.x + 12,
            top: tooltipPos.y + 12,
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
              Rough estimates
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
                Estimates (days):
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
                        {hoveredEpic.roughEstimates?.[role]} days
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
            Epic without stories, planned by rough estimates
          </div>
        </div>,
        document.body
      )}
    </>
  )
}

// --- Hybrid Merge ---
// Merges forecast (unified plan) data with retrospective data.
// Past phases use retro dates (solid bars), future phases use forecast dates (striped bars).
function mergeHybridEpics(
  planEpics: PlannedEpic[],
  retroResult: RetrospectiveResult | null
): PlannedEpic[] {
  if (!retroResult || retroResult.epics.length === 0) {
    // No retro data — mark all stories as forecast source
    return planEpics.map(epic => ({
      ...epic,
      stories: epic.stories.map(s => {
        const tagged = { ...s, _source: 'forecast' as PhaseSource, _worklogDays: null }
        return tagged
      })
    }))
  }

  const today = new Date().toISOString().split('T')[0]

  // Build retro lookup: epicKey -> { storyKey -> RetroStory }
  const retroEpicMap = new Map<string, Map<string, RetroStory>>()
  for (const retroEpic of retroResult.epics) {
    const storyMap = new Map<string, typeof retroEpic.stories[0]>()
    for (const story of retroEpic.stories) {
      storyMap.set(story.storyKey, story)
    }
    retroEpicMap.set(retroEpic.epicKey, storyMap)
  }

  const planEpicKeys = new Set(planEpics.map(e => e.epicKey))

  const mergedPlanEpics = planEpics.map(epic => {
    const retroStories = retroEpicMap.get(epic.epicKey)

    const mergedStories: PlannedStory[] = epic.stories.map(story => {
      const retroStory = retroStories?.get(story.storyKey)

      if (!retroStory) {
        // No retro data — pure forecast
        return { ...story, _source: 'forecast' as PhaseSource, _worklogDays: null }
      }

      // Has retro data — merge phases
      const mergedPhases: Record<string, UnifiedPhaseSchedule> = {}
      let source: PhaseSource = 'retro'

      // Collect all role codes from both sources
      const allRoles = new Set<string>([
        ...Object.keys(retroStory.phases || {}),
        ...Object.keys(story.phases || {})
      ])

      for (const role of allRoles) {
        const retroPhase = retroStory.phases?.[role]
        const forecastPhase = story.phases?.[role]

        if (retroPhase && retroPhase.startDate) {
          // Retro phase exists
          const retroEnd = retroPhase.endDate ?? (retroPhase.active ? today : retroPhase.startDate)

          if (forecastPhase && forecastPhase.startDate && forecastPhase.endDate) {
            // Both retro and forecast exist — take retro start, forecast end if still in progress
            if (retroPhase.active) {
              // Phase still active — hybrid: retro start + forecast end
              source = 'hybrid'
              mergedPhases[role] = {
                assigneeAccountId: forecastPhase.assigneeAccountId,
                assigneeDisplayName: forecastPhase.assigneeDisplayName,
                startDate: retroPhase.startDate,
                endDate: forecastPhase.endDate,
                hours: forecastPhase.hours,
                noCapacity: forecastPhase.noCapacity
              }
            } else {
              // Phase completed — use retro data entirely
              mergedPhases[role] = {
                assigneeAccountId: forecastPhase.assigneeAccountId,
                assigneeDisplayName: forecastPhase.assigneeDisplayName,
                startDate: retroPhase.startDate,
                endDate: retroEnd,
                hours: retroPhase.durationDays * 8,
                noCapacity: false
              }
            }
          } else {
            // Only retro — completed phase
            mergedPhases[role] = {
              assigneeAccountId: null,
              assigneeDisplayName: null,
              startDate: retroPhase.startDate,
              endDate: retroEnd,
              hours: retroPhase.durationDays * 8,
              noCapacity: false
            }
          }
        } else if (forecastPhase && forecastPhase.startDate && forecastPhase.endDate) {
          // Only forecast — future phase
          source = source === 'retro' ? 'hybrid' : source
          mergedPhases[role] = forecastPhase
        }
      }

      // Determine merged story dates
      const phaseStarts = Object.values(mergedPhases)
        .map(p => p.startDate).filter(Boolean) as string[]
      const phaseEnds = Object.values(mergedPhases)
        .map(p => p.endDate).filter(Boolean) as string[]

      const mergedStart = phaseStarts.length > 0 ? phaseStarts.sort()[0] : story.startDate
      const mergedEnd = phaseEnds.length > 0 ? phaseEnds.sort().reverse()[0] : story.endDate

      // If story is fully done with no forecast phases, source is 'retro'
      if (retroStory.completed && !Object.keys(story.phases || {}).length) {
        source = 'retro'
      }

      return {
        ...story,
        startDate: mergedStart,
        endDate: mergedEnd,
        phases: mergedPhases,
        _source: source,
        _worklogDays: retroStory.worklogDays || null
      }
    })

    // Recalculate epic dates from merged stories
    const storyStarts = mergedStories
      .map(s => s.startDate).filter(Boolean) as string[]
    const storyEnds = mergedStories
      .map(s => s.endDate).filter(Boolean) as string[]

    const epicStart = storyStarts.length > 0
      ? storyStarts.sort()[0]
      : epic.startDate
    const epicEnd = storyEnds.length > 0
      ? storyEnds.sort().reverse()[0]
      : epic.endDate

    return {
      ...epic,
      startDate: epicStart,
      endDate: epicEnd,
      stories: mergedStories
    }
  })

  // Add retro-only epics (not in plan — removed/moved epics with historical data)
  const retroOnlyEpics: PlannedEpic[] = []
  for (const retroEpic of retroResult.epics) {
    if (planEpicKeys.has(retroEpic.epicKey)) continue

    const retroStories: PlannedStory[] = retroEpic.stories.map(rs => {
      const phases: Record<string, UnifiedPhaseSchedule> = {}
      for (const [role, phase] of Object.entries(rs.phases || {})) {
        if (phase.startDate) {
          const endDate = phase.endDate ?? (phase.active ? today : phase.startDate)
          phases[role] = {
            assigneeAccountId: null,
            assigneeDisplayName: null,
            startDate: phase.startDate,
            endDate,
            hours: phase.durationDays * 8,
            noCapacity: false
          }
        }
      }
      return {
        storyKey: rs.storyKey,
        summary: '',
        autoScore: null,
        status: rs.completed ? 'Done' : '',
        startDate: rs.startDate,
        endDate: rs.endDate,
        phases,
        blockedBy: [],
        warnings: [],
        issueType: null,
        priority: null,
        flagged: null,
        totalEstimateSeconds: null,
        totalLoggedSeconds: null,
        progressPercent: rs.completed ? 100 : null,
        roleProgress: null,
        _source: 'retro' as PhaseSource,
        _worklogDays: rs.worklogDays || null
      } as PlannedStory
    })

    retroOnlyEpics.push({
      epicKey: retroEpic.epicKey,
      summary: retroEpic.summary,
      autoScore: 0,
      startDate: retroEpic.startDate,
      endDate: retroEpic.endDate,
      stories: retroStories,
      phaseAggregation: {},
      status: retroEpic.status,
      dueDate: null,
      totalEstimateSeconds: null,
      totalLoggedSeconds: null,
      progressPercent: retroEpic.progressPercent,
      roleProgress: null,
      storiesTotal: retroStories.length,
      storiesActive: 0,
      isRoughEstimate: false,
      roughEstimates: null,
      flagged: null
    })
  }

  return [...mergedPlanEpics, ...retroOnlyEpics]
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

interface TimelineContentProps {
  selectedTeamId?: number | null
  filteredEpicKeys?: Set<string>
  refreshToken?: number
  showFilterBar?: boolean
}

export function TimelineContent({
  selectedTeamId: controlledTeamId,
  filteredEpicKeys,
  refreshToken = 0,
  showFilterBar = true,
}: TimelineContentProps = {}) {
  const { getRoleColor, getRoleCodes } = useWorkflowConfig()
  const [searchParams, setSearchParams] = useSearchParams()
  const [teams, setTeams] = useState<Team[]>([])
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const isTeamControlled = controlledTeamId !== undefined

  // Sync teamId with URL
  const selectedTeamId = isTeamControlled
    ? controlledTeamId
    : (searchParams.get('teamId') ? Number(searchParams.get('teamId')) : null)
  const setSelectedTeamId = (id: number | null) => {
    if (isTeamControlled) return
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (id) {
        next.set('teamId', String(id))
      } else {
        next.delete('teamId')
      }
      return next
    }, { replace: true })
  }

  // SWR: restore from cache on mount to avoid skeleton flash on revisit
  const initialCache = selectedTeamId ? getApiCache<TimelineCache>(`timeline-${selectedTeamId}`) : undefined
  const [forecast, setForecast] = useState<ForecastResponse | null>(initialCache?.forecast ?? null)
  const [unifiedPlan, setUnifiedPlan] = useState<UnifiedPlanningResult | null>(initialCache?.unifiedPlan ?? null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [loading, setLoading] = useState(selectedTeamId ? !initialCache : false)
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

  // Ref to capture mount-time values without triggering re-runs
  const mountRef = useRef({ searchParams, isTeamControlled, setSearchParams })

  // Load config and teams (once on mount)
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))

    getStatusStyles()
      .then(setStatusStyles)
      .catch(err => console.error('Failed to load status styles:', err))

    const { searchParams: sp, isTeamControlled: controlled, setSearchParams: setSP } = mountRef.current
    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        // If no team in URL and teams available, select first one
        const urlTeamId = sp.get('teamId')
        if (!controlled && activeTeams.length > 0 && !urlTeamId) {
          setSP(prev => {
            const next = new URLSearchParams(prev)
            next.set('teamId', String(activeTeams[0].id))
            return next
          }, { replace: true })
        }
      })
      .catch(err => setError('Failed to load teams: ' + err.message))
  }, []) // Run once on mount, read URL synchronously

  // Load available snapshot dates when team changes
  useEffect(() => {
    if (!selectedTeamId) {
      setAvailableDates([])
      setSelectedHistoricalDate('')
      setIsHistoricalMode(false)
      return
    }

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
    if (!selectedTeamId) {
      setForecast(null)
      setUnifiedPlan(null)
      setLoading(false)
      setError(null)
      return
    }

    const abortController = new AbortController()

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

    // SWR: check cache, show cached data immediately, then fetch in background
    const cacheKey = `timeline-${selectedTeamId}`
    const cached = !isHistoricalMode ? getApiCache<TimelineCache>(cacheKey) : undefined
    if (cached) {
      setForecast(cached.forecast)
      setUnifiedPlan(cached.unifiedPlan)
      setLoading(false)
    } else {
      setLoading(true)
    }

    if (selectedHistoricalDate && isHistoricalMode) {
      // Load from historical snapshot + retro for hybrid view
      Promise.all([
        getForecastSnapshot(selectedTeamId, selectedHistoricalDate),
        getUnifiedPlanningSnapshot(selectedTeamId, selectedHistoricalDate),
        getRetrospective(selectedTeamId)
      ])
        .then(([forecastData, planData, retroData]) => {
          if (abortController.signal.aborted) return
          setForecast(forecastData)
          const hybridEpics = mergeHybridEpics(planData.epics, retroData)
          setUnifiedPlan({ ...planData, epics: hybridEpics })
          setLoading(false)
          triggerAnimation()
        })
        .catch(err => {
          if (abortController.signal.aborted) return
          setError('Failed to load historical snapshot: ' + err.message)
          setLoading(false)
        })
    } else {
      // Load live data: all 3 APIs in parallel (hybrid mode)
      Promise.all([
        getForecast(selectedTeamId),
        getUnifiedPlanning(selectedTeamId),
        getRetrospective(selectedTeamId)
      ])
        .then(([forecastData, planData, retroData]) => {
          if (abortController.signal.aborted) return
          setForecast(forecastData)
          // Merge hybrid: retro dates for past, forecast for future
          const hybridEpics = mergeHybridEpics(planData.epics, retroData)
          const hybridPlan = { ...planData, epics: hybridEpics }
          setUnifiedPlan(hybridPlan)
          setApiCache(cacheKey, { forecast: forecastData, unifiedPlan: hybridPlan })
          setLoading(false)
          triggerAnimation()
        })
        .catch(err => {
          if (abortController.signal.aborted) return
          setError('Failed to load data: ' + err.message)
          setLoading(false)
        })
    }

    return () => {
      abortController.abort()
      if (animationTimeoutRef.current) {
        clearTimeout(animationTimeoutRef.current)
      }
    }
  }, [selectedTeamId, selectedHistoricalDate, isHistoricalMode, refreshToken])

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
    if (!filteredEpicKeys) return unifiedPlan.epics
    return unifiedPlan.epics.filter(epic => filteredEpicKeys.has(epic.epicKey))
  }, [unifiedPlan, filteredEpicKeys])

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

  const chips = useMemo<FilterChip[]>(() => {
    const result: FilterChip[] = []

    if (showFilterBar && selectedTeamId !== null) {
      const selectedTeam = teams.find(team => team.id === selectedTeamId)
      result.push({
        category: 'Team',
        value: selectedTeam?.name || `Team ${selectedTeamId}`,
        color: selectedTeam?.color ?? undefined,
        onRemove: () => {
          if (teams.length > 1) {
            setSelectedTeamId(null)
          }
        },
      })
    }

    if (zoom !== 'week') {
      result.push({
        category: 'Scale',
        value: zoom.charAt(0).toUpperCase() + zoom.slice(1),
        onRemove: () => setZoom('week'),
      })
    }

    if (isHistoricalMode && selectedHistoricalDate) {
      result.push({
        category: 'Date',
        value: new Date(selectedHistoricalDate).toLocaleDateString('en-US', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        }),
        onRemove: () => handleHistoricalDateChange(''),
      })
    }

    return result
  }, [isHistoricalMode, selectedHistoricalDate, selectedTeamId, showFilterBar, teams, zoom])

  const clearFilters = () => {
    if (teams.length > 1) {
      setSelectedTeamId(null)
    }
    setZoom('week')
    handleHistoricalDateChange('')
  }

  const needsTeamSelection = isTeamControlled && selectedTeamId === null

  return (
    <StatusStylesProvider value={statusStyles}>
      {showFilterBar ? (
        <div style={{ padding: '0 16px' }}>
          <FilterBar
            chips={chips}
            onClearAll={chips.length > 0 ? clearFilters : undefined}
            trailing={
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
                <span className="legend-item legend-retro">Actual</span>
                <span className="legend-item legend-forecast-striped">Forecast</span>
                <span className="legend-item legend-today">Today</span>
                <span className="legend-item legend-due">Due Date</span>
              </div>
            }
          >
            <SingleSelectDropdown
              label="Team"
              options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color ?? undefined }))}
              selected={selectedTeamId !== null ? String(selectedTeamId) : null}
              onChange={v => setSelectedTeamId(v ? Number(v) : null)}
              placeholder="Select team..."
              allowClear={teams.length > 1}
            />

            <SingleSelectDropdown
              label="Scale"
              options={[
                { value: 'day', label: 'Day' },
                { value: 'week', label: 'Week' },
                { value: 'month', label: 'Month' },
              ]}
              selected={zoom}
              onChange={v => v && setZoom(v as ZoomLevel)}
              allowClear={false}
            />

            <SingleSelectDropdown
              label="Date"
              options={availableDates.map(date => ({
                value: date,
                label: new Date(date).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' }),
              }))}
              selected={selectedHistoricalDate || null}
              onChange={v => handleHistoricalDateChange(v ?? '')}
              placeholder="Today (live)"
            />
          </FilterBar>
        </div>
      ) : (
        <div style={{ marginBottom: 16 }}>
          <FilterBar
            chips={chips}
            onClearAll={chips.length > 0 ? clearFilters : undefined}
            trailing={
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
                <span className="legend-item legend-retro">Actual</span>
                <span className="legend-item legend-forecast-striped">Forecast</span>
                <span className="legend-item legend-today">Today</span>
                <span className="legend-item legend-due">Due Date</span>
              </div>
            }
          >
            <SingleSelectDropdown
              label="Scale"
              options={[
                { value: 'day', label: 'Day' },
                { value: 'week', label: 'Week' },
                { value: 'month', label: 'Month' },
              ]}
              selected={zoom}
              onChange={v => v && setZoom(v as ZoomLevel)}
              allowClear={false}
            />

            <SingleSelectDropdown
              label="Date"
              options={availableDates.map(date => ({
                value: date,
                label: new Date(date).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' }),
              }))}
              selected={selectedHistoricalDate || null}
              onChange={v => handleHistoricalDateChange(v ?? '')}
              placeholder="Today (live)"
            />
          </FilterBar>
        </div>
      )}

      {isHistoricalMode && (
        <div className="historical-mode-banner">
          📜 Viewing historical snapshot from {new Date(selectedHistoricalDate).toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric' })}
          <button
            onClick={() => handleHistoricalDateChange('')}
            style={{ marginLeft: 12, padding: '2px 8px', fontSize: 12, cursor: 'pointer' }}
          >
            Return to current data
          </button>
        </div>
      )}

      {loading && <GanttSkeleton />}
      {error && <div className="error">{error}</div>}

      {!loading && !error && needsTeamSelection && (
        <div className="empty">Select exactly one team in filters to see the timeline</div>
      )}

      {!loading && !error && !needsTeamSelection && epics.length === 0 && (
        <div className="empty">No epics with planning data</div>
      )}

      {!loading && !error && !needsTeamSelection && epics.length > 0 && (
        <div className="gantt-container">
          <div className="gantt-labels">
            <div className="gantt-labels-header">Epic</div>
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

          <div className="gantt-chart" ref={chartRef} role="region" aria-label="Timeline Gantt chart">
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
    </StatusStylesProvider>
  )
}

export function TimelinePage() {
  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Timeline</h2>
      </div>
      <TimelineContent />
    </main>
  )
}
