import { useState, useEffect, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import { ProjectTimelineDto, EpicTimelineDto } from '../api/projects'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { StatusBadge } from './board/StatusBadge'
import { getIssueIcon } from './board/helpers'
import '../pages/ProjectTimelinePage.css'

export type ZoomLevel = 'day' | 'week' | 'month'

const ZOOM_UNIT_WIDTH: Record<ZoomLevel, number> = {
  day: 40,
  week: 120,
  month: 100,
}

interface DateRange {
  start: Date
  end: Date
}

interface TimelineHeader {
  date: Date
  label: string
}

interface GroupHeader {
  label: string
  span: number
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

function formatDateShort(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function lightenColor(hex: string, factor: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lr = Math.round(r + (255 - r) * factor)
  const lg = Math.round(g + (255 - g) * factor)
  const lb = Math.round(b + (255 - b) * factor)
  return `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
}

function calculateDateRange(projects: ProjectTimelineDto[]): DateRange {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  let minDate: Date = today
  let maxDate: Date = addDays(today, 30)
  for (const project of projects) {
    for (const epic of project.epics) {
      if (epic.startDate) {
        const d = new Date(epic.startDate)
        if (d < minDate) minDate = d
      }
      if (epic.endDate) {
        const d = new Date(epic.endDate)
        if (d > maxDate) maxDate = d
      }
    }
  }
  minDate = startOfWeek(addDays(minDate, -3))
  const paddedMax = addDays(maxDate, 7)
  const numWeeks = Math.ceil(daysBetween(minDate, paddedMax) / 7)
  maxDate = addDays(minDate, numWeeks * 7)
  return { start: minDate, end: maxDate }
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

function generateGroupHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (headers.length === 0) return []
  const groups: GroupHeader[] = []
  if (zoom === 'day' || zoom === 'week') {
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
    if (currentSpan > 0) {
      groups.push({
        label: new Date(currentYear, currentMonth, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
        span: currentSpan
      })
    }
  } else {
    let currentQuarter = -1
    let currentYear = -1
    let currentSpan = 0
    for (const header of headers) {
      const quarter = Math.floor(header.date.getMonth() / 3) + 1
      const year = header.date.getFullYear()
      if (quarter !== currentQuarter || year !== currentYear) {
        if (currentSpan > 0) {
          groups.push({ label: `Q${currentQuarter} ${currentYear}`, span: currentSpan })
        }
        currentQuarter = quarter
        currentYear = year
        currentSpan = 1
      } else {
        currentSpan++
      }
    }
    if (currentSpan > 0) {
      groups.push({ label: `Q${currentQuarter} ${currentYear}`, span: currentSpan })
    }
  }
  return groups
}

function generateWeekHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (zoom !== 'day' || headers.length === 0) return []
  const weeks: GroupHeader[] = []
  let currentWeekStart: Date | null = null
  let currentSpan = 0
  for (const header of headers) {
    const weekStart = startOfWeek(header.date)
    if (!currentWeekStart || weekStart.getTime() !== currentWeekStart.getTime()) {
      if (currentSpan > 0 && currentWeekStart) {
        weeks.push({ label: `W${getWeekNumber(currentWeekStart)}`, span: currentSpan })
      }
      currentWeekStart = weekStart
      currentSpan = 1
    } else {
      currentSpan++
    }
  }
  if (currentSpan > 0 && currentWeekStart) {
    weeks.push({ label: `W${getWeekNumber(currentWeekStart)}`, span: currentSpan })
  }
  return weeks
}

function getWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)
}

function isWeekend(date: Date): boolean {
  const day = date.getDay()
  return day === 0 || day === 6
}

function formatHours(seconds: number | null): string {
  if (seconds === null || seconds === 0) return '0h'
  return `${Math.round(seconds / 3600)}h`
}

function buildRemainingText(epic: EpicTimelineDto, roleCodes: string[]): string {
  if (epic.isRoughEstimate && epic.roughEstimates) {
    const parts = roleCodes
      .filter(r => (epic.roughEstimates?.[r] ?? 0) > 0)
      .map(r => `${r}:${epic.roughEstimates![r]}`)
    return parts.length > 0 ? `~${parts.join('/')}d` : ''
  }
  if (epic.roleProgress) {
    const parts = roleCodes
      .filter(r => epic.roleProgress?.[r] && !epic.roleProgress[r].completed)
      .map(r => {
        const p = epic.roleProgress![r]
        const remainSec = Math.max(0, (p.estimateSeconds ?? 0) - (p.loggedSeconds ?? 0))
        const remainDays = Math.ceil(remainSec / 3600 / 8)
        return remainDays > 0 ? `${r}:${remainDays}` : null
      })
      .filter(Boolean)
    return parts.length > 0 ? `${parts.join('/')}d` : ''
  }
  return ''
}

// --- Component ---

export interface ProjectGanttViewProps {
  projects: ProjectTimelineDto[]
  jiraBaseUrl: string
  zoom: ZoomLevel
  expanded: Record<string, boolean>
  onToggleProject: (key: string) => void
}

export function ProjectGanttView({ projects, jiraBaseUrl, zoom, expanded, onToggleProject }: ProjectGanttViewProps) {
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl } = useWorkflowConfig()
  const [hoveredEpic, setHoveredEpic] = useState<EpicTimelineDto | null>(null)
  const [hoveredProject, setHoveredProject] = useState<ProjectTimelineDto | null>(null)
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 })

  const chartRef = useRef<HTMLDivElement>(null)
  const labelsRef = useRef<HTMLDivElement>(null)

  // Sync scroll between labels and chart
  useEffect(() => {
    const chart = chartRef.current
    const labels = labelsRef.current
    if (!chart || !labels) return
    const handleScroll = () => { labels.scrollTop = chart.scrollTop }
    chart.addEventListener('scroll', handleScroll)
    return () => chart.removeEventListener('scroll', handleScroll)
  }, [projects])

  // Auto-scroll to today
  useEffect(() => {
    if (!chartRef.current || projects.length === 0) return
    const range = calculateDateRange(projects)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const totalDays = daysBetween(range.start, range.end)
    const todayOffset = daysBetween(range.start, today)
    const todayPercent = todayOffset / totalDays
    const scrollTarget = Math.max(0, (todayPercent - 0.2) * chartRef.current.scrollWidth)
    chartRef.current.scrollLeft = scrollTarget
  }, [projects, zoom])

  const roleCodes = getRoleCodes()

  const dateRange = useMemo(() => calculateDateRange(projects), [projects])
  const headers = useMemo(() => generateTimelineHeaders(dateRange, zoom), [dateRange, zoom])
  const groupHeaders = useMemo(() => generateGroupHeaders(headers, zoom), [headers, zoom])
  const weekHeaders = useMemo(() => generateWeekHeaders(headers, zoom), [headers, zoom])
  const chartWidth = useMemo(() => headers.length * ZOOM_UNIT_WIDTH[zoom], [headers.length, zoom])

  const todayPercent = useMemo(() => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const todayOffset = daysBetween(dateRange.start, today)
    return (todayOffset / totalDays) * 100
  }, [dateRange])

  const projectDateRanges = useMemo(() => {
    const map: Record<string, { start: Date | null; end: Date | null }> = {}
    for (const p of projects) {
      let minD: Date | null = null
      let maxD: Date | null = null
      for (const e of p.epics) {
        if (e.startDate) {
          const d = new Date(e.startDate)
          if (!minD || d < minD) minD = d
        }
        if (e.endDate) {
          const d = new Date(e.endDate)
          if (!maxD || d > maxD) maxD = d
        }
      }
      map[p.issueKey] = { start: minD, end: maxD }
    }
    return map
  }, [projects])

  const rows = useMemo(() => {
    const result: Array<
      | { type: 'project'; project: ProjectTimelineDto }
      | { type: 'epic'; epic: EpicTimelineDto; projectKey: string }
    > = []
    for (const p of projects) {
      result.push({ type: 'project', project: p })
      if (expanded[p.issueKey]) {
        for (const e of p.epics) {
          result.push({ type: 'epic', epic: e, projectKey: p.issueKey })
        }
      }
    }
    return result
  }, [projects, expanded])

  const renderEpicBar = (epic: EpicTimelineDto) => {
    if (!epic.startDate || !epic.endDate) return null
    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const startDate = new Date(epic.startDate)
    const endDate = new Date(epic.endDate)
    const daysFromStart = daysBetween(dateRange.start, startDate)
    const duration = daysBetween(startDate, endDate) + 1
    const leftPercent = (daysFromStart / totalDays) * 100
    const widthPercent = (duration / totalDays) * 100
    const isRough = epic.isRoughEstimate
    const barText = buildRemainingText(epic, roleCodes)

    return (
      <div
        className={`pt-epic-bar ${isRough ? 'rough' : ''}`}
        style={{
          left: `${leftPercent}%`,
          width: `${Math.max(widthPercent, 0.5)}%`,
          ...(epic.teamColor ? { borderLeft: `3px solid ${epic.teamColor}` } : {}),
        }}
      >
        {epic.phaseAggregation && Object.entries(epic.phaseAggregation).map(([role, phase]) => {
          if (!phase.startDate || !phase.endDate) return null
          const phaseStart = new Date(phase.startDate)
          const phaseEnd = new Date(phase.endDate)
          const phaseStartOffset = daysBetween(startDate, phaseStart)
          const phaseDuration = daysBetween(phaseStart, phaseEnd) + 1
          const phaseLeftPct = Math.max(0, (phaseStartOffset / duration) * 100)
          const phaseWidthPct = Math.min(100 - phaseLeftPct, (phaseDuration / duration) * 100)
          const color = lightenColor(getRoleColor(role), isRough ? 0.7 : 0.5)

          return (
            <div
              key={role}
              className={`pt-phase-segment ${isRough ? 'rough' : ''}`}
              style={{
                left: `${phaseLeftPct}%`,
                width: `${phaseWidthPct}%`,
                height: '100%',
                top: 0,
                transform: 'none',
                backgroundColor: color,
                borderRadius: 0,
                ...(isRough ? {
                  backgroundImage: `repeating-linear-gradient(135deg, transparent, transparent 3px, rgba(255,255,255,0.4) 3px, rgba(255,255,255,0.4) 6px)`,
                } : {}),
              }}
            />
          )
        })}
        {barText && <span className="pt-bar-text">{barText}</span>}
      </div>
    )
  }

  const renderProjectBar = (project: ProjectTimelineDto) => {
    const range = projectDateRanges[project.issueKey]
    if (!range?.start || !range?.end) return null
    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const daysFromStart = daysBetween(dateRange.start, range.start)
    const duration = daysBetween(range.start, range.end) + 1
    const leftPercent = (daysFromStart / totalDays) * 100
    const widthPercent = (duration / totalDays) * 100

    return (
      <div
        className="pt-project-bar"
        style={{
          left: `${leftPercent}%`,
          width: `${Math.max(widthPercent, 0.5)}%`,
        }}
      >
        <div
          className="pt-project-bar-progress"
          style={{
            width: `${project.progressPercent}%`,
            background: project.progressPercent >= 100 ? '#36B37E' : '#0065FF',
          }}
        />
      </div>
    )
  }

  if (projects.length === 0) {
    return <div className="pt-empty">No projects with timeline data</div>
  }

  return (
    <>
      <div className="pt-gantt">
        {/* Labels panel */}
        <div className="pt-labels" ref={labelsRef}>
          <div className="pt-labels-header" style={{ height: zoom === 'day' ? 80 : 60 }}>Project / Epic</div>
          {rows.map((row) => {
            if (row.type === 'project') {
              const p = row.project
              const isExp = expanded[p.issueKey]
              return (
                <div key={p.issueKey} className="pt-project-label" onClick={() => onToggleProject(p.issueKey)}>
                  <span className={`pt-project-chevron ${isExp ? 'expanded' : ''}`}>&#9654;</span>
                  <img
                    src={getIssueIcon(p.issueType || 'Project', getIssueTypeIconUrl(p.issueType))}
                    alt={p.issueType || 'Project'}
                    style={{ width: 16, height: 16, flexShrink: 0 }}
                  />
                  <a
                    href={jiraBaseUrl ? `${jiraBaseUrl}${p.issueKey}` : '#'}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="pt-project-key"
                    onClick={ev => ev.stopPropagation()}
                  >
                    {p.issueKey}
                  </a>
                  <span className="pt-project-summary" title={p.summary}>{p.summary}</span>
                  <div className="pt-project-progress">
                    <div className="pt-progress-bar-bg">
                      <div
                        className="pt-progress-bar-fill"
                        style={{
                          width: `${p.progressPercent}%`,
                          background: p.progressPercent >= 100 ? '#36B37E' : '#0065FF',
                        }}
                      />
                    </div>
                    <span className="pt-progress-text">{p.progressPercent}%</span>
                  </div>
                </div>
              )
            } else {
              const e = row.epic
              return (
                <div key={`${row.projectKey}-${e.epicKey}`} className="pt-epic-label">
                  <img
                    src={getIssueIcon(e.issueType || 'Epic', getIssueTypeIconUrl(e.issueType))}
                    alt={e.issueType || 'Epic'}
                    style={{ width: 16, height: 16, flexShrink: 0 }}
                  />
                  <a
                    href={jiraBaseUrl ? `${jiraBaseUrl}${e.epicKey}` : '#'}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="pt-epic-key"
                    onClick={ev => ev.stopPropagation()}
                  >
                    {e.epicKey}
                  </a>
                  <span className="pt-epic-summary" title={e.summary}>{e.summary}</span>
                  {e.teamName && e.teamColor && (
                    <span
                      className="pt-epic-team-dot"
                      style={{ backgroundColor: e.teamColor }}
                      title={e.teamName}
                    />
                  )}
                </div>
              )
            }
          })}
        </div>

        {/* Chart panel */}
        <div className="pt-chart" ref={chartRef}>
          <div className="pt-chart-headers" style={{ width: `${chartWidth}px`, minWidth: `${chartWidth}px` }}>
            <div className="pt-header-group">
              {groupHeaders.map((g, i) => (
                <div
                  key={i}
                  className="pt-header-group-cell"
                  style={{ width: `${g.span * ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                >
                  {g.label}
                </div>
              ))}
            </div>
            {zoom === 'day' && weekHeaders.length > 0 && (
              <div className="pt-header-week">
                {weekHeaders.map((w, i) => (
                  <div
                    key={i}
                    className="pt-header-week-cell"
                    style={{ width: `${w.span * ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                  >
                    {w.label}
                  </div>
                ))}
              </div>
            )}
            <div className="pt-header-unit">
              {headers.map((h, i) => (
                <div
                  key={i}
                  className={`pt-header-cell${zoom === 'day' && isWeekend(h.date) ? ' pt-header-cell-weekend' : ''}`}
                  style={{ width: `${ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                >
                  {h.label}
                </div>
              ))}
            </div>
          </div>

          <div
            className="pt-chart-body"
            style={{
              width: `${chartWidth}px`,
              minWidth: `${chartWidth}px`,
              backgroundImage: `repeating-linear-gradient(to right, transparent, transparent ${ZOOM_UNIT_WIDTH[zoom] - 1}px, #ebecf0 ${ZOOM_UNIT_WIDTH[zoom] - 1}px, #ebecf0 ${ZOOM_UNIT_WIDTH[zoom]}px)`,
              backgroundSize: `${ZOOM_UNIT_WIDTH[zoom]}px 100%`,
              position: 'relative',
            }}
          >
            {zoom === 'day' && headers.map((h, i) => {
              if (!isWeekend(h.date)) return null
              return (
                <div
                  key={`wknd-${i}`}
                  className="pt-weekend-column"
                  style={{
                    left: `${i * ZOOM_UNIT_WIDTH[zoom]}px`,
                    width: `${ZOOM_UNIT_WIDTH[zoom]}px`,
                  }}
                />
              )
            })}

            {todayPercent >= 0 && todayPercent <= 100 && (
              <div className="pt-today-line" style={{ left: `${todayPercent}%` }} />
            )}

            {rows.map((row, rowIndex) => {
              if (row.type === 'project') {
                return (
                  <div
                    key={row.project.issueKey}
                    className="pt-project-row pt-row-animate"
                    style={{ animationDelay: `${Math.min(rowIndex * 0.05, 0.5)}s` }}
                    onMouseEnter={() => { setHoveredProject(row.project); setHoveredEpic(null) }}
                    onMouseMove={(e) => setTooltipPos({ x: e.clientX + 12, y: e.clientY - 12 })}
                    onMouseLeave={() => setHoveredProject(null)}
                  >
                    {renderProjectBar(row.project)}
                  </div>
                )
              } else {
                return (
                  <div
                    key={`${row.projectKey}-${row.epic.epicKey}`}
                    className="pt-epic-row pt-row-animate"
                    style={{ animationDelay: `${Math.min(rowIndex * 0.05, 0.5)}s` }}
                    onMouseEnter={() => { setHoveredEpic(row.epic); setHoveredProject(null) }}
                    onMouseMove={(e) => setTooltipPos({ x: e.clientX + 12, y: e.clientY - 12 })}
                    onMouseLeave={() => setHoveredEpic(null)}
                  >
                    {renderEpicBar(row.epic)}
                  </div>
                )
              }
            })}
          </div>
        </div>
      </div>

      {/* Epic Tooltip */}
      {hoveredEpic && createPortal(
        <div
          className="pt-tooltip"
          style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            transform: 'translateY(-100%)',
            zIndex: 10000,
            pointerEvents: 'none',
            background: 'rgba(23, 43, 77, 0.98)',
            borderRadius: 8,
            padding: 14,
            minWidth: 300,
            maxWidth: 400,
            boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
            color: 'white',
            fontSize: 13,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ fontWeight: 600, color: '#B3D4FF' }}>{hoveredEpic.epicKey}</span>
            <StatusBadge status={hoveredEpic.status || 'Unknown'} />
          </div>
          <div style={{ color: '#B3BAC5', marginBottom: 12, fontSize: 12, lineHeight: 1.4 }}>{hoveredEpic.summary}</div>
          {hoveredEpic.progressPercent != null && (
            <div style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <span style={{ color: '#8993A4', fontSize: 11 }}>Progress</span>
                <span style={{ color: '#B3BAC5', fontSize: 11 }}>
                  {hoveredEpic.roleProgress ? (() => {
                    let totalLogged = 0, totalEst = 0
                    Object.values(hoveredEpic.roleProgress!).forEach(rp => {
                      totalLogged += rp.loggedSeconds ?? 0
                      totalEst += rp.estimateSeconds ?? 0
                    })
                    return `${formatHours(totalLogged)} / ${formatHours(totalEst)}`
                  })() : ''}
                </span>
              </div>
              <div style={{ width: '100%', height: 8, backgroundColor: '#42526e', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{
                  width: `${hoveredEpic.progressPercent}%`,
                  height: '100%',
                  backgroundColor: (hoveredEpic.progressPercent ?? 0) >= 100 ? '#36B37E' : '#0065FF',
                  borderRadius: 4,
                }} />
              </div>
              <div style={{ textAlign: 'right', color: '#B3BAC5', fontSize: 12, marginTop: 2 }}>
                {hoveredEpic.progressPercent}%
              </div>
            </div>
          )}
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, fontSize: 12 }}>
            <div>
              <span style={{ color: '#8993A4' }}>Dates: </span>
              <span style={{ color: '#B3BAC5' }}>
                {hoveredEpic.startDate ? formatDateShort(new Date(hoveredEpic.startDate)) : '\u2014'}
                {' \u2192 '}
                {hoveredEpic.endDate ? formatDateShort(new Date(hoveredEpic.endDate)) : '\u2014'}
              </span>
            </div>
          </div>
          {hoveredEpic.teamName && (
            <div style={{ fontSize: 11, color: '#8993A4', marginBottom: 8 }}>
              Team: <span style={{ color: hoveredEpic.teamColor || '#B3BAC5' }}>{hoveredEpic.teamName}</span>
            </div>
          )}
          {hoveredEpic.roleProgress && (
            <div style={{ borderTop: '1px solid #42526e', paddingTop: 10 }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <tbody>
                  {roleCodes.filter(r => hoveredEpic.roleProgress?.[r]).map(role => {
                    const rp = hoveredEpic.roleProgress![role]
                    return (
                      <tr key={role}>
                        <td style={{ padding: '3px 4px' }}>
                          <span style={{ color: getRoleColor(role) }}>{'\u25CF'}</span> {role}
                          {rp.completed && <span style={{ color: '#22c55e', marginLeft: 4 }}>{'\u2713'}</span>}
                        </td>
                        <td style={{ padding: '3px 4px', textAlign: 'right', color: '#e5e7eb' }}>
                          {formatHours(rp.loggedSeconds)}/{formatHours(rp.estimateSeconds)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
          {hoveredEpic.isRoughEstimate && hoveredEpic.roughEstimates && (
            <div style={{ borderTop: '1px solid #42526e', paddingTop: 10 }}>
              <div style={{ color: '#8993A4', fontSize: 11, marginBottom: 4 }}>Rough Estimate</div>
              {roleCodes.filter(r => (hoveredEpic.roughEstimates?.[r] ?? 0) > 0).map(role => (
                <div key={role} style={{ fontSize: 12, padding: '2px 4px' }}>
                  <span style={{ color: getRoleColor(role) }}>{'\u25CF'}</span> {role}: {hoveredEpic.roughEstimates![role]}d
                </div>
              ))}
            </div>
          )}
        </div>,
        document.body
      )}

      {/* Project Tooltip */}
      {hoveredProject && createPortal(
        <div
          className="pt-tooltip"
          style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            transform: 'translateY(-100%)',
            zIndex: 10000,
            pointerEvents: 'none',
            background: 'rgba(23, 43, 77, 0.98)',
            borderRadius: 8,
            padding: 14,
            minWidth: 260,
            maxWidth: 360,
            boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
            color: 'white',
            fontSize: 13,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ fontWeight: 600, color: '#B3D4FF' }}>{hoveredProject.issueKey}</span>
            <StatusBadge status={hoveredProject.status} />
          </div>
          <div style={{ color: '#B3BAC5', marginBottom: 12, fontSize: 12, lineHeight: 1.4 }}>{hoveredProject.summary}</div>
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, fontSize: 12 }}>
            <div>
              <span style={{ color: '#8993A4' }}>Epics: </span>
              <span style={{ color: '#B3BAC5' }}>{hoveredProject.epics.length}</span>
            </div>
            {hoveredProject.riceNormalizedScore != null && (
              <div>
                <span style={{ color: '#8993A4' }}>RICE: </span>
                <span style={{ color: '#FFD700', fontWeight: 600 }}>{hoveredProject.riceNormalizedScore}</span>
              </div>
            )}
            {hoveredProject.quarterLabel && (
              <div>
                <span style={{ color: '#8993A4' }}>Quarter: </span>
                <span style={{ color: '#B3BAC5' }}>{hoveredProject.quarterLabel}</span>
              </div>
            )}
          </div>
          <div style={{ marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <span style={{ color: '#8993A4', fontSize: 11 }}>Progress</span>
            </div>
            <div style={{ width: '100%', height: 8, backgroundColor: '#42526e', borderRadius: 4, overflow: 'hidden' }}>
              <div style={{
                width: `${hoveredProject.progressPercent}%`,
                height: '100%',
                backgroundColor: hoveredProject.progressPercent >= 100 ? '#36B37E' : '#0065FF',
                borderRadius: 4,
              }} />
            </div>
            <div style={{ textAlign: 'right', color: '#B3BAC5', fontSize: 12, marginTop: 2 }}>
              {hoveredProject.progressPercent}%
            </div>
          </div>
          {(() => {
            const range = projectDateRanges[hoveredProject.issueKey]
            if (!range?.start || !range?.end) return null
            return (
              <div style={{ fontSize: 12, color: '#B3BAC5' }}>
                <span style={{ color: '#8993A4' }}>Dates: </span>
                {formatDateShort(range.start)} {'\u2192'} {formatDateShort(range.end)}
              </div>
            )
          })()}
        </div>,
        document.body
      )}
    </>
  )
}
