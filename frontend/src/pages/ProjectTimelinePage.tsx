import { useState, useEffect, useMemo, useRef } from 'react'
import { projectsApi, ProjectTimelineDto, EpicTimelineDto } from '../api/projects'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { getConfig } from '../api/config'
import './ProjectTimelinePage.css'

type ZoomLevel = 'week' | 'month'

const ZOOM_UNIT_WIDTH: Record<ZoomLevel, number> = {
  week: 120,
  month: 100,
}

interface DateRange {
  start: Date
  end: Date
}

// --- Utility functions ---

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
  return date.toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' })
}

function lightenColor(hex: string, factor: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lr = Math.round(r + (255 - r) * factor)
  const lg = Math.round(g + (255 - g) * factor)
  const lb = Math.round(b + (255 - b) * factor)
  return `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
}

// --- Status badge colors ---
const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  'new': { bg: '#dfe1e6', text: '#42526e' },
  'backlog': { bg: '#dfe1e6', text: '#42526e' },
  'to do': { bg: '#dfe1e6', text: '#42526e' },
  'planned': { bg: '#deebff', text: '#0747a6' },
  'in progress': { bg: '#e3fcef', text: '#006644' },
  'done': { bg: '#dfe1e6', text: '#42526e' },
  'новый': { bg: '#dfe1e6', text: '#42526e' },
  'бэклог': { bg: '#dfe1e6', text: '#42526e' },
  'запланировано': { bg: '#deebff', text: '#0747a6' },
  'в разработке': { bg: '#e3fcef', text: '#006644' },
  'готово': { bg: '#dfe1e6', text: '#42526e' },
}

function getStatusColor(status: string | null): { bg: string; text: string } {
  if (!status) return { bg: '#dfe1e6', text: '#42526e' }
  return STATUS_COLORS[status.toLowerCase()] || { bg: '#dfe1e6', text: '#42526e' }
}

// --- Date range calculation ---

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

// --- Timeline headers ---

interface TimelineHeader {
  date: Date
  label: string
}

interface GroupHeader {
  label: string
  span: number
}

function generateTimelineHeaders(range: DateRange, zoom: ZoomLevel): TimelineHeader[] {
  const headers: TimelineHeader[] = []
  let current = new Date(range.start)

  if (zoom === 'week') {
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

function generateGroupHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (headers.length === 0) return []
  const groups: GroupHeader[] = []

  if (zoom === 'week') {
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
    if (currentSpan > 0) {
      groups.push({
        label: new Date(currentYear, currentMonth, 1).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' }),
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

// --- Remaining days text for bar ---

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

// --- Main component ---

export function ProjectTimelinePage() {
  const { getRoleColor, getRoleCodes } = useWorkflowConfig()
  const [projects, setProjects] = useState<ProjectTimelineDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [zoom, setZoom] = useState<ZoomLevel>('week')
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  const chartRef = useRef<HTMLDivElement>(null)
  const labelsRef = useRef<HTMLDivElement>(null)

  // Load data
  useEffect(() => {
    setLoading(true)
    setError(null)

    Promise.all([
      projectsApi.getTimeline(),
      getConfig().then(c => c.jiraBaseUrl).catch(() => ''),
    ])
      .then(([data, baseUrl]) => {
        setProjects(data)
        setJiraBaseUrl(baseUrl)
        // Expand all projects by default
        const exp: Record<string, boolean> = {}
        data.forEach(p => { exp[p.issueKey] = true })
        setExpanded(exp)
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load timeline: ' + err.message)
        setLoading(false)
      })
  }, [])

  // Sync scroll between labels and chart
  useEffect(() => {
    const chart = chartRef.current
    const labels = labelsRef.current
    if (!chart || !labels) return

    const handleScroll = () => {
      labels.scrollTop = chart.scrollTop
    }
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

  const toggleProject = (key: string) => {
    setExpanded(prev => ({ ...prev, [key]: !prev[key] }))
  }

  const toggleAll = () => {
    const allExpanded = projects.every(p => expanded[p.issueKey])
    const next: Record<string, boolean> = {}
    projects.forEach(p => { next[p.issueKey] = !allExpanded })
    setExpanded(next)
  }

  const dateRange = useMemo(() => calculateDateRange(projects), [projects])
  const headers = useMemo(() => generateTimelineHeaders(dateRange, zoom), [dateRange, zoom])
  const groupHeaders = useMemo(() => generateGroupHeaders(headers, zoom), [headers, zoom])
  const chartWidth = useMemo(() => headers.length * ZOOM_UNIT_WIDTH[zoom], [headers.length, zoom])

  const todayPercent = useMemo(() => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const totalDays = daysBetween(dateRange.start, dateRange.end)
    const todayOffset = daysBetween(dateRange.start, today)
    return (todayOffset / totalDays) * 100
  }, [dateRange])

  const roleCodes = getRoleCodes()

  // Compute project-level aggregated date range from epics
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

  // Build list of rows for rendering (project headers + expanded epics)
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
        }}
      >
        {/* Phase segments */}
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

        {/* Bar text */}
        {barText && (
          <span className="pt-bar-text">{barText}</span>
        )}
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

  return (
    <main className="main-content" style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 56px)' }}>
      <div className="page-header" style={{ padding: '12px 16px 0' }}>
        <h2>Project Timeline</h2>
      </div>

      {/* Controls */}
      <div className="pt-controls">
        <div className="filter-group">
          <label className="filter-label">Zoom</label>
          <select
            className="filter-input"
            value={zoom}
            onChange={e => setZoom(e.target.value as ZoomLevel)}
          >
            <option value="week">Week</option>
            <option value="month">Month</option>
          </select>
        </div>

        <button
          className="filter-input"
          onClick={toggleAll}
          style={{ cursor: 'pointer', padding: '4px 10px' }}
        >
          {projects.every(p => expanded[p.issueKey]) ? 'Collapse All' : 'Expand All'}
        </button>

        <div className="pt-legend">
          {roleCodes.map(code => (
            <span
              key={code}
              className="pt-legend-item"
              style={{ borderLeft: `3px solid ${lightenColor(getRoleColor(code), 0.5)}` }}
            >
              {code}
            </span>
          ))}
          <span className="pt-legend-item pt-legend-today">Today</span>
          <span className="pt-legend-item pt-legend-rough">Rough est.</span>
        </div>
      </div>

      {/* Loading / Error / Empty */}
      {loading && <div className="pt-loading">Loading...</div>}
      {error && <div className="pt-error">{error}</div>}
      {!loading && !error && projects.length === 0 && (
        <div className="pt-empty">No projects with timeline data</div>
      )}

      {/* Gantt */}
      {!loading && !error && projects.length > 0 && (
        <div className="pt-gantt">
          {/* Labels panel */}
          <div className="pt-labels" ref={labelsRef}>
            <div className="pt-labels-header">Project / Epic</div>
            {rows.map((row) => {
              if (row.type === 'project') {
                const p = row.project
                const isExp = expanded[p.issueKey]
                return (
                  <div key={p.issueKey} className="pt-project-label" onClick={() => toggleProject(p.issueKey)}>
                    <span className={`pt-project-chevron ${isExp ? 'expanded' : ''}`}>&#9654;</span>
                    <span className="pt-project-key">{p.issueKey}</span>
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
                const statusColor = getStatusColor(e.status)
                return (
                  <div key={`${row.projectKey}-${e.epicKey}`} className="pt-epic-label">
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
                    {e.teamName && <span className="pt-epic-team">{e.teamName}</span>}
                    <span className="pt-epic-status" style={{ background: statusColor.bg, color: statusColor.text }}>
                      {(e.status || '').length > 14 ? (e.status || '').substring(0, 12) + '...' : (e.status || '')}
                    </span>
                  </div>
                )
              }
            })}
          </div>

          {/* Chart panel */}
          <div className="pt-chart" ref={chartRef}>
            {/* Headers */}
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
              <div className="pt-header-unit">
                {headers.map((h, i) => (
                  <div
                    key={i}
                    className="pt-header-cell"
                    style={{ width: `${ZOOM_UNIT_WIDTH[zoom]}px`, flex: 'none' }}
                  >
                    {h.label}
                  </div>
                ))}
              </div>
            </div>

            {/* Body */}
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
              {/* Today line */}
              {todayPercent >= 0 && todayPercent <= 100 && (
                <div className="pt-today-line" style={{ left: `${todayPercent}%` }} />
              )}

              {rows.map((row) => {
                if (row.type === 'project') {
                  return (
                    <div key={row.project.issueKey} className="pt-project-row">
                      {renderProjectBar(row.project)}
                    </div>
                  )
                } else {
                  return (
                    <div key={`${row.projectKey}-${row.epic.epicKey}`} className="pt-epic-row">
                      {renderEpicBar(row.epic)}
                    </div>
                  )
                }
              })}
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
