import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, UnifiedPlanningResult, PlannedStory, PlannedEpic, UnifiedPhaseSchedule, PlanningWarning, getAvailableSnapshotDates, getUnifiedPlanningSnapshot, getForecastSnapshot, getRetrospective, RetrospectiveResult, RetroStory, WorklogDay, StatusInterval } from '../api/forecast'
import { getConfig } from '../api/config'
import { getStatusStyles, StatusStyle } from '../api/board'
import { getStatusHistory, type StatusHistory } from '../api/statusHistory'
import { StatusPathContent } from '../components/StatusPathContent'
import { StatusStylesProvider, useStatusStyles } from '../components/board/StatusStylesContext'
import { EmptyState } from '../components/EmptyState'
import { ProgressBar } from '../components/ProgressBar'
import { resolveStatusBgColor } from '../components/board/StatusBadge'
import { StatusBadge } from '../components/board/StatusBadge'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterBar } from '../components/FilterBar'
import type { FilterChip } from '../components/FilterChips'
import { GanttSkeleton } from '../components/skeletons'
import { getApiCache, setApiCache } from '../hooks/useApiCache'
import './TimelinePage.css'

import { getIssueIcon } from '../components/board/helpers'
import {
  ERROR_BG, lightenColor,
  DSR_GREEN, DSR_YELLOW, DSR_RED, PROGRESS_IN_PROGRESS, TEXT_MUTED, WARNING_ORANGE, WARNING_BG,
  TIMELINE_PHASE_TINT, TIMELINE_PHASE_TINT_ROUGH, TIMELINE_ROLE_BORDER_TINT,
  TIMELINE_BAR_TRACK, TIMELINE_FLAGGED_BORDER, TIMELINE_BLOCKED_BORDER,
  TIMELINE_ROUGH_BG, TIMELINE_ROUGH_BADGE_BG, TIMELINE_ROUGH_BADGE_TEXT,
  TOOLTIP_HIGHLIGHT, TOOLTIP_TEXT, TOOLTIP_LABEL, TOOLTIP_VALUE,
  TOOLTIP_PROGRESS_TRACK, TOOLTIP_SUCCESS, TOOLTIP_DANGER, TOOLTIP_DIVIDER,
} from '../constants/colors'
import { DarkTooltip } from '../components/DarkTooltip'
import {
  ZoomLevel, DateRange,
  daysBetween,
  calculateDateRangeFromCandidates, generateTimelineHeaders, generateGroupHeaders,
  generateWeekHeaders, isWeekend, formatDateShort, formatHours,
} from '../utils/dateGrid'

type PhaseSource = 'retro' | 'forecast' | 'hybrid'

// Fixed width of the right meta column (status badge + progress) in epic labels.
// Shared by both label rows so badges and progress bars align to one vertical
// grid line across all epics, and summaries truncate at a consistent point.
// Sized to fit the longest status (ЗАПЛАНИРОВАНО) in the compact badge.
const LABEL_META_WIDTH = 112
// Epic present only in retrospective data (absent from the unified plan) — either a done
// epic or a not-yet-plannable epic (e.g. NEW) whose stories already started.
type TimelinePlannedEpic = PlannedEpic & { _retroOnly?: boolean }
type ActualsMode = 'worklog' | 'status'
type TimelineCache = { forecast: ForecastResponse; unifiedPlan: UnifiedPlanningResult }

// Width per unit in pixels for each zoom level
const ZOOM_UNIT_WIDTH: Record<ZoomLevel, number> = {
  day: 40,    // 40px per day - detailed view
  week: 120,  // 120px per week - default view
  month: 100  // 100px per month - overview
}

// --- Date range & timeline ---

// By default the timeline renders at most this many days of the past. Older completed
// work is hidden until the user explicitly expands via the "Show earlier" button.
export const DEFAULT_PAST_DAYS = 30

export function calculateDateRange(
  unifiedPlan: UnifiedPlanningResult | null,
  forecast: ForecastResponse | null,
  clampPastDays: number | null = null,
): DateRange {
  const startCandidates: Date[] = []
  const endCandidates: Date[] = []

  // Use unified plan dates if available (hybrid data includes both retro and forecast)
  if (unifiedPlan) {
    for (const epic of unifiedPlan.epics) {
      if (epic.isRoughEstimate) {
        if (epic.startDate) startCandidates.push(new Date(epic.startDate))
        if (epic.endDate) endCandidates.push(new Date(epic.endDate))
      } else {
        for (const story of epic.stories) {
          if (story.startDate) startCandidates.push(new Date(story.startDate))
          if (story.endDate) endCandidates.push(new Date(story.endDate))
        }
      }
    }
  }

  // Also consider forecast due dates
  if (forecast) {
    for (const epic of forecast.epics) {
      if (epic.dueDate) endCandidates.push(new Date(epic.dueDate))
    }
  }

  // Clamping (hide work completed more than clampPastDays ago) keeps the initial view
  // focused; a "Show earlier" toggle passes null to disable it.
  return calculateDateRangeFromCandidates(startCandidates, endCandidates, clampPastDays)
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

// --- Epic Label Component with Tooltip ---
interface EpicLabelProps {
  epic: PlannedEpic
  epicForecast: EpicForecast | undefined
  jiraBaseUrl: string
  rowHeight: number
}

function EpicLabel({ epic, epicForecast, jiraBaseUrl, rowHeight }: EpicLabelProps) {
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl, getTypeNameByCategory } = useWorkflowConfig()
  // Resolve the real Jira epic type name (e.g. 'Эпик') so the icon matches the board.
  const epicTypeName = getTypeNameByCategory('EPIC') ?? 'Epic'
  const epicIconUrl = getIssueIcon(epicTypeName, getIssueTypeIconUrl(epicTypeName), 'EPIC')
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

  const progress = epic.progressPercent ?? 0
  const dueDateDelta = epicForecast?.dueDateDeltaDays ?? null

  // Due date status indicator
  let dueDateIndicator = null
  if (epic.dueDate) {
    if (dueDateDelta === null || dueDateDelta <= 0) {
      dueDateIndicator = <span style={{ color: DSR_GREEN, fontSize: 10 }}>●</span>
    } else if (dueDateDelta <= 5) {
      dueDateIndicator = <span style={{ color: DSR_YELLOW, fontSize: 10 }}>●</span>
    } else {
      dueDateIndicator = <span style={{ color: DSR_RED, fontSize: 10 }}>●</span>
    }
  }

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
        {/* Row 1: Icon + Key left, status badge in the fixed meta column (tooltip-style header) */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, minWidth: 0 }}>
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
            {epic.flagged && <span style={{ fontSize: 9, fontWeight: 700, padding: '0 4px', borderRadius: 3, color: DSR_RED, backgroundColor: ERROR_BG, lineHeight: '16px' }} title="Flagged">FLG</span>}
          </div>
          <div style={{ width: LABEL_META_WIDTH, flexShrink: 0 }}>
            <StatusBadge status={epic.status || 'Unknown'} maxWidth={LABEL_META_WIDTH} compact />
          </div>
        </div>
        {/* Row 2: Summary left, progress in the same meta column. No native title attrs —
            the row already shows the rich DarkTooltip on hover; a browser tooltip is noise. */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <span className="gantt-label-title" style={{ flex: 1, minWidth: 0 }}>
            {epic.summary}
          </span>
          <div style={{ width: LABEL_META_WIDTH, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 4 }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <ProgressBar value={progress} height={4} ariaLabel={`${epic.epicKey} progress`} />
            </div>
            <span style={{ fontSize: 8, color: TEXT_MUTED, minWidth: 20, textAlign: 'right' }}>{progress}%</span>
          </div>
        </div>
      </div>

      {showTooltip && (
        <DarkTooltip top={tooltipPos.y} left={tooltipPos.x} minWidth={300} maxWidth={400}>
          {/* Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <img src={epicIconUrl} alt="Epic" style={{ width: 16, height: 16 }} />
              <DarkTooltip.Title>{epic.epicKey}</DarkTooltip.Title>
              <DarkTooltip.Label style={{ fontSize: 11 }}>({epic.autoScore?.toFixed(0)})</DarkTooltip.Label>
            </div>
            <StatusBadge status={epic.status || 'Unknown'} maxWidth={130} />
          </div>

          {/* Summary */}
          <div style={{ color: TOOLTIP_TEXT, marginBottom: 12, fontSize: 12, lineHeight: 1.4 }}>
            {epic.summary}
          </div>

          {/* Progress section */}
          <div style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <DarkTooltip.Label style={{ fontSize: 11 }}>Progress</DarkTooltip.Label>
              <span style={{ color: TOOLTIP_TEXT, fontSize: 11 }}>
                {formatHours(epic.totalLoggedSeconds)} / {formatHours(epic.totalEstimateSeconds)}
              </span>
            </div>
            {/* Epic progress keeps its blue in-progress / green complete semantics
                (deliberately different from the story bar's green/red-over scale) */}
            <div style={{
              width: '100%',
              height: 8,
              backgroundColor: TOOLTIP_PROGRESS_TRACK,
              borderRadius: 4,
              overflow: 'hidden'
            }}>
              <div style={{
                width: `${progress}%`,
                height: '100%',
                backgroundColor: progress >= 100 ? DSR_GREEN : PROGRESS_IN_PROGRESS,
                borderRadius: 4
              }} />
            </div>
            <div style={{ textAlign: 'right', color: TOOLTIP_TEXT, fontSize: 12, marginTop: 2 }}>
              {progress}%
            </div>
          </div>

          {/* Dates section */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, fontSize: 12 }}>
            <div>
              <DarkTooltip.Label>📅 </DarkTooltip.Label>
              <span style={{ color: TOOLTIP_TEXT }}>
                {epic.startDate ? formatDateShort(new Date(epic.startDate)) : '—'}
                {' → '}
                {epic.endDate ? formatDateShort(new Date(epic.endDate)) : '—'}
              </span>
            </div>
            {epic.dueDate && (
              <div>
                <DarkTooltip.Label>⏰ Due: </DarkTooltip.Label>
                <span style={{ color: dueDateDelta && dueDateDelta > 0 ? DSR_RED : DSR_GREEN }}>
                  {formatDateShort(new Date(epic.dueDate))}
                  {dueDateDelta !== null && dueDateDelta > 0 && ` (+${dueDateDelta}d)`}
                </span>
              </div>
            )}
          </div>

          {/* Role progress */}
          {epic.roleProgress && (
            <div style={{ borderTop: `1px solid ${TOOLTIP_DIVIDER}`, paddingTop: 10, marginBottom: 10 }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <tbody>
                  {getRoleCodes()
                    .filter(role => epic.roleProgress![role])
                    .map(role => { const progress = epic.roleProgress![role]; return progress && (
                    <tr key={role}>
                      <td style={{ padding: '3px 0', width: 50, whiteSpace: 'nowrap' }}>
                        <span style={{ color: getRoleColor(role) }}>●</span> {role}
                        {progress.completed && <span style={{ marginLeft: 4 }}>✓</span>}
                      </td>
                      <td style={{ color: TOOLTIP_TEXT, textAlign: 'right' }}>
                        {formatHours(progress.loggedSeconds)} / {formatHours(progress.estimateSeconds)}
                      </td>
                      <td style={{ color: TOOLTIP_LABEL, textAlign: 'right', width: 45 }}>
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
          <div style={{ display: 'flex', justifyContent: 'space-between', color: TOOLTIP_LABEL, fontSize: 11 }}>
            <span>📊 AutoScore: {epic.autoScore?.toFixed(0) || '—'}</span>
            <span>📋 Stories: {epic.storiesActive} active / {epic.storiesTotal} total</span>
          </div>
        </DarkTooltip>
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
  actualsMode: ActualsMode
}

function StoryBar({ story, lane, dateRange, jiraBaseUrl, globalWarnings, onHover, actualsMode }: StoryBarProps) {
  const { getRoleColor } = useWorkflowConfig()
  const totalDays = daysBetween(dateRange.start, dateRange.end)

  // Determine story source for visual styling
  const storySource: PhaseSource = (story as PlannedStory & { _source?: PhaseSource })._source || 'forecast'
  const worklogDays: WorklogDay[] | null = (story as PlannedStory & { _worklogDays?: WorklogDay[] | null })._worklogDays || null

  const statusIntervals: StatusInterval[] | null =
    (story as PlannedStory & { _statusIntervals?: StatusInterval[] | null })._statusIntervals || null
  const statusStyles = useStatusStyles()

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  let startDate = new Date(story.startDate!)
  let endDate = new Date(story.endDate!)

  // Story-status mode only cares about the in-progress span — NEW (not started yet)
  // and DONE (finished) intervals are noise here. Unknown statuses (no config entry)
  // are kept visible, same as before this filter existed.
  const catOf = (status: string) => statusStyles[status]?.statusCategory
  const visibleStatusIntervals: StatusInterval[] = (statusIntervals || []).filter(
    i => catOf(i.status) !== 'NEW' && catOf(i.status) !== 'DONE'
  )

  let hideBar = false

  // Story-status mode: the story's own status history often lags the subtask-derived
  // bar (statuses move after the work is logged), so the bar is clamped to the visible
  // (non-NEW/DONE) intervals' span instead of the subtask dates — this both widens it
  // (history extends beyond the subtask dates) and narrows it (drop NEW/DONE bookends).
  // A story with no visible interval at all (entire history is NEW and/or DONE — never
  // started, or done with nothing distinguishable in between) has nothing meaningful to
  // show in this mode, so its bar is hidden entirely.
  if (actualsMode === 'status' && (storySource === 'retro' || storySource === 'hybrid')
      && statusIntervals && statusIntervals.length > 0) {
    if (visibleStatusIntervals.length > 0) {
      startDate = new Date(visibleStatusIntervals[0].startDate)
      let visibleEnd = new Date(visibleStatusIntervals[visibleStatusIntervals.length - 1].endDate)
      if (visibleEnd > today) visibleEnd = today
      endDate = visibleEnd
    } else {
      hideBar = true
    }
  }

  // Logged-time mode: the bar spans first→last worklog day; remaining work stays
  // visible as the striped autoplanner forecast. A story with nothing logged shows
  // only that remainder — or no bar at all when it is already done.
  if (actualsMode === 'worklog' && (storySource === 'retro' || storySource === 'hybrid')) {
    if (worklogDays && worklogDays.length > 0) {
      const wlDates = worklogDays.map(w => w.date).sort()
      const firstWl = new Date(wlDates[0])
      const lastWl = new Date(wlDates[wlDates.length - 1])
      startDate = firstWl
      if (storySource === 'retro') {
        endDate = lastWl
      } else if (endDate < lastWl) {
        endDate = lastWl
      }
    } else if (storySource === 'hybrid') {
      if (today > startDate) startDate = today
    } else {
      hideBar = true
    }
  }

  const daysFromStart = daysBetween(dateRange.start, startDate)
  const duration = daysBetween(startDate, endDate) + 1
  const leftPercent = (daysFromStart / totalDays) * 100
  const widthPercent = (duration / totalDays) * 100

  const storyNumber = story.storyKey.split('-')[1] || story.storyKey
  const isBlocked = story.blockedBy && story.blockedBy.length > 0
  const hasWarning = story.warnings?.length > 0 || globalWarnings?.some(w => w.issueKey === story.storyKey)

  const getPhaseColor = (role: string) => lightenColor(getRoleColor(role), TIMELINE_PHASE_TINT)

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

  // Segments "in which status was the story" from transition history (Story statuses mode).
  // Clipped to bar boundaries and to today; same-day intervals overlap in DOM order —
  // last status of the day wins.
  const renderStatusSegments = () => {
    if (visibleStatusIntervals.length === 0) return null

    const segments: React.ReactNode[] = []
    visibleStatusIntervals.forEach((interval, idx) => {
      const intervalStart = new Date(interval.startDate)
      const intervalEnd = new Date(interval.endDate)

      const clipStart = intervalStart < startDate ? startDate : intervalStart
      let clipEnd = intervalEnd > endDate ? endDate : intervalEnd
      if (clipEnd > today) clipEnd = today
      if (clipEnd < clipStart) return

      const segOffset = daysBetween(startDate, clipStart)
      const segDuration = daysBetween(clipStart, clipEnd) + 1
      const segLeft = (segOffset / duration) * 100
      const segWidth = Math.min((segDuration / duration) * 100, 100 - segLeft)
      const color = resolveStatusBgColor(interval.status, statusStyles)

      segments.push(
        <div
          key={`${interval.status}-${idx}`}
          style={{
            position: 'absolute',
            left: `${segLeft}%`,
            width: `${Math.max(segWidth, 1)}%`,
            height: '100%',
            backgroundColor: color,
          }}
        />
      )
    })
    return segments
  }

  // Striped remainder of the autoplanner forecast (from today onward), drawn in
  // Logged-time mode on top of the worklog days for stories still in progress.
  const renderForecastRemainder = () => {
    if (!story.phases) return null
    return Object.entries(story.phases).map(([role, phase]) => {
      if (!phase || !phase.startDate || !phase.endDate) return null
      const phaseEnd = new Date(phase.endDate)
      if (phaseEnd < today) return null
      const phaseStart = new Date(phase.startDate)
      const segStart = phaseStart > today ? phaseStart : today
      if (segStart > endDate || phaseEnd < startDate) return null
      const clipStart = segStart < startDate ? startDate : segStart
      const clipEnd = phaseEnd > endDate ? endDate : phaseEnd
      const segLeft = (daysBetween(startDate, clipStart) / duration) * 100
      const segWidth = Math.min(((daysBetween(clipStart, clipEnd) + 1) / duration) * 100, 100 - segLeft)
      return (
        <div
          key={`remainder-${role}`}
          className="phase-bar-forecast"
          style={{
            position: 'absolute',
            left: `${segLeft}%`,
            width: `${Math.max(segWidth, 1)}%`,
            height: '100%',
            '--phase-color': getPhaseColor(role),
            opacity: phase.noCapacity ? 0.4 : 0.7,
          } as React.CSSProperties}
        />
      )
    })
  }

  // Phases of a pure-forecast story (no retro data) — always striped. Stories with
  // retro data render worklog/status segments + the striped remainder instead.
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

    return (
      <div
        key={phaseType}
        className="phase-bar-forecast"
        style={{
          position: 'absolute',
          left: `${phaseLeftPercent}%`,
          width: `${phaseWidthPercent}%`,
          height: '100%',
          '--phase-color': getPhaseColor(phaseType),
          opacity: phase.noCapacity ? 0.4 : 0.7
        } as React.CSSProperties}
      />
    )
  }

  const handleMouseEnter = (e: React.MouseEvent) => {
    onHover(story, { x: e.clientX, y: e.clientY - 12 })
  }

  if (hideBar || endDate < startDate) return null

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
        border: story.flagged ? `2px solid ${TIMELINE_FLAGGED_BORDER}` : isBlocked ? `2px solid ${TIMELINE_BLOCKED_BORDER}` : '1px solid rgba(0,0,0,0.15)',
        overflow: 'hidden',
        background: TIMELINE_BAR_TRACK,
        boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
      }}
      onMouseEnter={handleMouseEnter}
      onMouseMove={e => onHover(story, { x: e.clientX, y: e.clientY - 12 })}
      onMouseLeave={() => onHover(null)}
    >
      {/* Past part: worklog days or story-status intervals depending on actualsMode.
          In Logged-time mode the striped autoplanner remainder is drawn on top. */}
      {(storySource === 'retro' || storySource === 'hybrid') && actualsMode === 'status'
        ? renderStatusSegments()
        : (storySource === 'retro' || storySource === 'hybrid')
          ? <>
              {renderWorklogSegments()}
              {renderForecastRemainder()}
            </>
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
        {story.flagged && <span style={{ marginLeft: 3, fontSize: 9, fontWeight: 700, padding: '0 3px', borderRadius: 3, color: DSR_RED, backgroundColor: ERROR_BG }}>FLG</span>}
        {hasWarning && <span style={{ marginLeft: 3, fontSize: 9, fontWeight: 700, padding: '0 3px', borderRadius: 3, color: WARNING_ORANGE, backgroundColor: WARNING_BG }}>!</span>}
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
  actualsMode: ActualsMode
}

function StoryBars({ stories, dateRange, jiraBaseUrl, globalWarnings, actualsMode }: StoryBarsProps) {
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()
  const [hoveredStory, setHoveredStory] = useState<PlannedStory | null>(null)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })

  // F92 — Status path tooltip (Story-statuses mode): lazily loads the hovered story's
  // status history (same shape/endpoint as the Board's StatusHistoryTooltip) and caches
  // it per storyKey so re-hovering a story already fetched this session doesn't refetch.
  const [statusHistory, setStatusHistory] = useState<StatusHistory | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState(false)
  const historyCacheRef = useRef<Map<string, StatusHistory>>(new Map())
  const historyAbortRef = useRef<AbortController | null>(null)
  const historyDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const historyFetchedKeyRef = useRef<string | null>(null)

  const fetchStatusHistory = useCallback((storyKey: string) => {
    historyAbortRef.current?.abort()
    const controller = new AbortController()
    historyAbortRef.current = controller
    setHistoryLoading(true)
    setHistoryError(false)
    getStatusHistory(storyKey, controller.signal)
      .then(data => {
        if (controller.signal.aborted) return
        historyCacheRef.current.set(storyKey, data)
        setStatusHistory(data)
      })
      .catch(() => {
        if (!controller.signal.aborted) setHistoryError(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
  }, [])

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

  const getStorySource = (s: PlannedStory | null): PhaseSource =>
    s ? ((s as PlannedStory & { _source?: PhaseSource })._source || 'forecast') : 'forecast'

  const handleHover = (story: PlannedStory | null, pos?: { x: number; y: number }) => {
    setHoveredStory(story)
    if (pos) setTooltipPos(pos)

    if (!story) {
      // Unhover: stop any pending/in-flight fetch so it doesn't resolve into a state
      // nobody is looking at, and clear loading so an aborted fetch never leaves a
      // re-hover stuck on "Loading…". Crucially also clear the per-hover dedup marker:
      // a quick hover (< 300ms) cancels the debounce before the fetch ever runs, so
      // without this reset a re-hover of the SAME story would short-circuit on the
      // "already handled" guard below and never retry — a permanently empty tooltip.
      if (historyDebounceRef.current) clearTimeout(historyDebounceRef.current)
      historyAbortRef.current?.abort()
      historyFetchedKeyRef.current = null
      setHistoryLoading(false)
      return
    }

    const showsStatusPath = actualsMode === 'status'
      && (getStorySource(story) === 'retro' || getStorySource(story) === 'hybrid')

    if (!showsStatusPath) {
      // Not in Story-statuses mode (or a pure-forecast story) — reset the dedup marker
      // so switching back to status mode on the same story re-checks the cache/fetch
      // instead of silently no-op'ing.
      historyFetchedKeyRef.current = null
      return
    }

    // onHover also fires on every mousemove within the same bar. Dedup so we run the
    // fetch/cache logic once per hover session (the marker is reset on unhover and on
    // mode/story changes above, so a later re-hover always re-evaluates from real state).
    if (historyFetchedKeyRef.current === story.storyKey) return
    historyFetchedKeyRef.current = story.storyKey

    const cached = historyCacheRef.current.get(story.storyKey)
    if (cached) {
      setStatusHistory(cached)
      setHistoryLoading(false)
      setHistoryError(false)
      return
    }

    setStatusHistory(null)
    setHistoryError(false)
    if (historyDebounceRef.current) clearTimeout(historyDebounceRef.current)
    historyDebounceRef.current = setTimeout(() => fetchStatusHistory(story.storyKey), 300)
  }

  const showsStatusPath = actualsMode === 'status'
    && hoveredStory != null
    && (getStorySource(hoveredStory) === 'retro' || getStorySource(hoveredStory) === 'hybrid')

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
            actualsMode={actualsMode}
          />
        ))}
      </div>

      {hoveredStory && (
        /* NOT interactive: the tooltip follows the cursor, and near the right viewport
           edge clampLeft pulls it under the pointer — with pointer-events:auto that
           triggers a mouseleave/mouseenter loop on the bar (rapid tooltip flicker).
           The tooltip can't be reached by the mouse anyway (it unmounts on bar leave);
           the clickable Jira link lives on the bar's story number. */
        <DarkTooltip top={tooltipPos.y + 12} left={tooltipPos.x + 12} minWidth={300} maxWidth={420}>
          {/* Header: Type icon + Key + AutoScore + Status */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <img
                src={getIssueIcon(hoveredStory.issueType || 'Story', getIssueTypeIconUrl(hoveredStory.issueType), getIssueTypeCategory(hoveredStory.issueType))}
                alt={hoveredStory.issueType || 'Story'}
                style={{ width: '16px', height: '16px' }}
              />
              <a
                href={`${jiraBaseUrl}${hoveredStory.storyKey}`}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: TOOLTIP_HIGHLIGHT, textDecoration: 'none', fontWeight: 600 }}
              >
                {hoveredStory.storyKey}
              </a>
              {hoveredStory.autoScore !== null && (
                <DarkTooltip.Label style={{ fontSize: '12px' }}>({hoveredStory.autoScore?.toFixed(0)})</DarkTooltip.Label>
              )}
              {hoveredStory.flagged && (
                <span style={{ fontSize: 9, fontWeight: 700, padding: '0 4px', borderRadius: 3, color: DSR_RED, backgroundColor: ERROR_BG, lineHeight: '16px' }} title="Flagged">FLG</span>
              )}
            </div>
            <StatusBadge status={hoveredStory.status} maxWidth={130} />
          </div>

          {showsStatusPath ? (
            /* F92 — Story-statuses mode on a retro/hybrid story: show the same status
               journey as the Board's StatusHistoryTooltip instead of the phases/progress
               body (which describes the autoplanner forecast, not what actually happened). */
            <div style={{ marginTop: '4px' }}>
              <div style={{ fontWeight: 600, color: TOOLTIP_TEXT, marginBottom: 8, fontSize: '12px' }}>Status path</div>
              {historyLoading && <div style={{ color: TOOLTIP_LABEL, fontSize: '12px' }}>Loading…</div>}
              {historyError && <div style={{ color: TOOLTIP_DANGER, fontSize: '12px' }}>Failed to load</div>}
              {statusHistory && !historyLoading && (
                <StatusPathContent history={statusHistory} variant="dark" />
              )}
            </div>
          ) : (
            <>
              {/* Summary */}
              <div style={{ color: TOOLTIP_TEXT, marginBottom: '10px', fontSize: '12px', lineHeight: 1.4 }}>
                {hoveredStory.summary || 'No summary'}
              </div>

              {/* Progress bar */}
              {hoveredStory.totalEstimateSeconds && hoveredStory.totalEstimateSeconds > 0 && (
                <div style={{ marginBottom: '10px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px', fontSize: '12px' }}>
                    <DarkTooltip.Label>Progress</DarkTooltip.Label>
                    <DarkTooltip.Value>
                      {formatHours(hoveredStory.totalLoggedSeconds)} / {formatHours(hoveredStory.totalEstimateSeconds)}
                      <DarkTooltip.Label style={{ marginLeft: '6px' }}>
                        ({hoveredStory.progressPercent ?? 0}%)
                      </DarkTooltip.Label>
                    </DarkTooltip.Value>
                  </div>
                  <DarkTooltip.Progress value={hoveredStory.progressPercent ?? 0} max={100} />
                </div>
              )}

              {/* Dates */}
              {hoveredStory.startDate && hoveredStory.endDate && (
                <div style={{ marginBottom: '10px', fontSize: '12px' }}>
                  <DarkTooltip.Label>📅 {formatDateShort(new Date(hoveredStory.startDate))} → {formatDateShort(new Date(hoveredStory.endDate))}</DarkTooltip.Label>
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
                              <span style={{ color: TOOLTIP_SUCCESS, marginLeft: '4px' }}>✓</span>
                            )}
                          </td>
                          <td style={{ padding: '3px 4px', color: TOOLTIP_TEXT }}>{phase?.assigneeDisplayName || '-'}</td>
                          <td style={{ padding: '3px 4px', textAlign: 'right', color: TOOLTIP_VALUE }}>
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
            </>
          )}

          {/* Blocked by */}
          {hoveredStory.blockedBy && hoveredStory.blockedBy.length > 0 && (
            <div style={{ color: TOOLTIP_DANGER, marginTop: '10px', fontSize: '12px', borderTop: `1px solid ${TOOLTIP_DIVIDER}`, paddingTop: '8px' }}>
              🚫 Blocked by: {hoveredStory.blockedBy.join(', ')}
            </div>
          )}

          {/* Planning warnings — the reason for the "!" badge on the bar. Story-level and
              global warnings can duplicate the same type ("No estimate" vs the verbose
              variant) — keep the most descriptive message per type. */}
          {(() => {
            const all = [
              ...(hoveredStory.warnings || []),
              ...globalWarnings.filter(w => w.issueKey === hoveredStory.storyKey),
            ]
            const byType = new Map<string, PlanningWarning>()
            for (const w of all) {
              const key = w.type || w.message
              const prev = byType.get(key)
              if (!prev || (w.message?.length || 0) > (prev.message?.length || 0)) byType.set(key, w)
            }
            const unique = Array.from(byType.values()).filter(w => w.message)
            return unique.length > 0 && (
              <div style={{ color: WARNING_ORANGE, marginTop: '10px', fontSize: '12px', borderTop: `1px solid ${TOOLTIP_DIVIDER}`, paddingTop: '8px' }}>
                {unique.map(w => (
                  <div key={w.type || w.message}>⚠️ {w.message}</div>
                ))}
              </div>
            )
          })()}
        </DarkTooltip>
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

  const getPhaseColorDimmed = (role: string) => lightenColor(getRoleColor(role), TIMELINE_PHASE_TINT_ROUGH)

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
        background: TIMELINE_ROUGH_BG,
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
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl, getTypeNameByCategory } = useWorkflowConfig()
  const epicTypeName = getTypeNameByCategory('EPIC') ?? 'Epic'
  const epicIconUrl = getIssueIcon(epicTypeName, getIssueTypeIconUrl(epicTypeName), 'EPIC')
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

      {hoveredEpic && (
        <DarkTooltip top={tooltipPos.y + 12} left={tooltipPos.x + 12} minWidth={280} maxWidth={380}>
          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <img src={epicIconUrl} alt="Epic" style={{ width: '16px', height: '16px' }} />
              <DarkTooltip.Title>{hoveredEpic.epicKey}</DarkTooltip.Title>
            </div>
            <span
              style={{
                fontSize: '10px',
                padding: '2px 6px',
                borderRadius: '4px',
                background: TIMELINE_ROUGH_BADGE_BG,
                color: TIMELINE_ROUGH_BADGE_TEXT,
                fontWeight: 500
              }}
            >
              Rough estimates
            </span>
          </div>

          {/* Summary */}
          <div style={{ color: TOOLTIP_TEXT, marginBottom: '10px', fontSize: '12px', lineHeight: 1.4 }}>
            {hoveredEpic.summary}
          </div>

          {/* Rough estimates breakdown */}
          {hoveredEpic.roughEstimates && Object.keys(hoveredEpic.roughEstimates).length > 0 && (
            <div style={{ marginBottom: '10px', borderTop: `1px solid ${TOOLTIP_DIVIDER}`, paddingTop: '10px' }}>
              <DarkTooltip.Label style={{ display: 'block', fontSize: '11px', marginBottom: '6px' }}>
                Estimates (days):
              </DarkTooltip.Label>
              <table style={{ width: '100%', fontSize: '12px' }}>
                <tbody>
                  {getRoleCodes()
                    .filter(role => (hoveredEpic.roughEstimates?.[role] || 0) > 0)
                    .map(role => (
                    <tr key={role}>
                      <td style={{ padding: '2px 4px' }}>
                        <span style={{ color: getRoleColor(role) }}>●</span> {role}
                      </td>
                      <td style={{ padding: '2px 4px', textAlign: 'right', color: TOOLTIP_VALUE }}>
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
            <div style={{ fontSize: '12px' }}>
              <DarkTooltip.Label>📅 {formatDateShort(new Date(hoveredEpic.startDate))} → {formatDateShort(new Date(hoveredEpic.endDate))}</DarkTooltip.Label>
            </div>
          )}

          {/* Note */}
          <div style={{ marginTop: '8px', color: TOOLTIP_LABEL, fontSize: '11px', fontStyle: 'italic' }}>
            Epic without stories, planned by rough estimates
          </div>
        </DarkTooltip>
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
        const tagged = { ...s, _source: 'forecast' as PhaseSource, _worklogDays: null, _statusIntervals: null }
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
        return { ...story, _source: 'forecast' as PhaseSource, _worklogDays: null, _statusIntervals: null }
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
        _worklogDays: retroStory.worklogDays || null,
        _statusIntervals: retroStory.statusIntervals || null
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
  const retroOnlyEpics: TimelinePlannedEpic[] = []
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
        summary: rs.summary,
        autoScore: rs.autoScore,
        status: rs.status,
        startDate: rs.startDate,
        endDate: rs.endDate,
        phases,
        blockedBy: [],
        warnings: [],
        issueType: rs.issueType,
        priority: null,
        flagged: null,
        totalEstimateSeconds: rs.totalEstimateSeconds,
        totalLoggedSeconds: rs.totalLoggedSeconds,
        progressPercent: rs.progressPercent,
        roleProgress: rs.roleProgress,
        _source: 'retro' as PhaseSource,
        _worklogDays: rs.worklogDays || null,
        _statusIntervals: rs.statusIntervals || null
      } as PlannedStory
    })

    retroOnlyEpics.push({
      _retroOnly: true,
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

  // Retro-only epics are tagged (_retroOnly) and reordered at render time by status
  // category: done ones above plan epics, unfinished ones below — see the `epics` memo.
  return [...retroOnlyEpics, ...mergedPlanEpics]
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
  actualsMode: ActualsMode
}

function GanttRow({ plannedEpic, stories, globalWarnings, dateRange, jiraBaseUrl, rowHeight, epicIndex, shouldAnimate, actualsMode }: GanttRowProps) {
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
            actualsMode={actualsMode}
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
  const [actualsMode, setActualsMode] = useState<ActualsMode>('worklog')
  // When true, render the full history instead of clamping to DEFAULT_PAST_DAYS.
  const [showEarlier, setShowEarlier] = useState(false)
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
    // Each team starts in the clamped (recent) view
    setShowEarlier(false)
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

  // Position the horizontal scroll: on the clamped (default) view, place the "today"
  // line at 30% of the visible width — 30% past on the left, 70% future on the right.
  // When the user expands history, scroll all the way left to reveal the older work.
  useEffect(() => {
    const el = chartRef.current
    if (!unifiedPlan || !el) return

    const raf = requestAnimationFrame(() => {
      if (showEarlier) {
        el.scrollLeft = 0
        return
      }
      const range = calculateDateRange(unifiedPlan, forecast, DEFAULT_PAST_DAYS)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const totalDays = daysBetween(range.start, range.end)
      if (totalDays <= 0) return
      const todayX = (daysBetween(range.start, today) / totalDays) * el.scrollWidth
      el.scrollLeft = Math.max(0, todayX - 0.30 * el.clientWidth)
    })
    return () => cancelAnimationFrame(raf)
  }, [unifiedPlan, forecast, showEarlier])

  // Compute the full and clamped ranges once per data change; derive the active range
  // and the "can expand" flag from them (avoids re-traversing epic/story dates 3x).
  const { fullRange, clampedRange } = useMemo(() => ({
    fullRange: calculateDateRange(unifiedPlan, forecast, null),
    clampedRange: calculateDateRange(unifiedPlan, forecast, DEFAULT_PAST_DAYS),
  }), [unifiedPlan, forecast])

  const dateRange = showEarlier ? fullRange : clampedRange

  // Whether there is history older than the default window (controls the toggle button).
  const canExpandHistory = fullRange.start.getTime() < clampedRange.start.getTime()

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

  // Get epics from unified plan, ordered consistently with the Board:
  // done retro-only epics on top (completed band), plan epics in backend manual_order,
  // unfinished retro-only epics (e.g. NEW epics whose stories already started) at the bottom.
  const epics = useMemo(() => {
    if (!unifiedPlan) return []
    const list = filteredEpicKeys
      ? unifiedPlan.epics.filter(epic => filteredEpicKeys.has(epic.epicKey))
      : unifiedPlan.epics
    const isRetroOnly = (e: PlannedEpic) => Boolean((e as TimelinePlannedEpic)._retroOnly)
    const isDoneEpic = (e: PlannedEpic) =>
      Boolean(e.status) && statusStyles[e.status as string]?.statusCategory === 'DONE'
    return [
      ...list.filter(e => isRetroOnly(e) && isDoneEpic(e)),
      ...list.filter(e => !isRetroOnly(e)),
      ...list.filter(e => isRetroOnly(e) && !isDoneEpic(e)),
    ]
  }, [unifiedPlan, filteredEpicKeys, statusStyles])

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

  // Only true filters get chips. Zoom and snapshot date are display parameters —
  // they're already visible in the dropdowns above, a removable chip would be noise.
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

    return result
  }, [selectedTeamId, showFilterBar, teams])

  const clearFilters = () => {
    if (teams.length > 1) {
      setSelectedTeamId(null)
    }
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
                      borderLeft: `3px solid ${lightenColor(getRoleColor(code), TIMELINE_ROLE_BORDER_TINT)}`,
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

            <SingleSelectDropdown
              label="Actuals"
              options={[
                { value: 'worklog', label: 'Logged time' },
                { value: 'status', label: 'Story statuses' },
              ]}
              selected={actualsMode}
              onChange={v => v && setActualsMode(v as ActualsMode)}
              allowClear={false}
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
                      borderLeft: `3px solid ${lightenColor(getRoleColor(code), TIMELINE_ROLE_BORDER_TINT)}`,
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

            <SingleSelectDropdown
              label="Actuals"
              options={[
                { value: 'worklog', label: 'Logged time' },
                { value: 'status', label: 'Story statuses' },
              ]}
              selected={actualsMode}
              onChange={v => v && setActualsMode(v as ActualsMode)}
              allowClear={false}
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
        <EmptyState message="Select exactly one team in filters to see the timeline" />
      )}

      {!loading && !error && !needsTeamSelection && epics.length === 0 && (
        <EmptyState message="No epics with planning data" />
      )}

      {!loading && !error && !needsTeamSelection && epics.length > 0 && canExpandHistory && (
        <div className="timeline-history-toggle">
          <button
            type="button"
            className="timeline-history-btn"
            aria-label={showEarlier ? 'Show recent history only' : 'Show earlier history'}
            onClick={() => setShowEarlier(v => !v)}
          >
            {showEarlier ? 'Show recent ▶' : '◀ Show earlier'}
          </button>
        </div>
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
                    actualsMode={actualsMode}
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
