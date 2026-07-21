import { useState, useEffect, useCallback, type ReactNode } from 'react'
import { myWorkApi, type MyWorkResponse, type MyTask, type QueueStory, type MyAnalytics, type MyCompletedTask, type DsrBreakdown, type CalendarDay } from '../api/myWork'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { EmptyState } from '../components/EmptyState'
import { StatusBadge } from '../components/board/StatusBadge'
import { TeamBadge } from '../components/TeamBadge'
import { RoleBadge } from '../components/RoleBadge'
import { GradeBadge } from '../components/GradeBadge'
import { getIssueIcon } from '../components/board/helpers'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { ABSENCE_TYPE_LABELS } from '../components/AbsenceModal'
import { ABSENCE_COLORS, hexToRgba, TEXT_MUTED } from '../constants/colors'
import { MetricCard } from '../components/metrics/MetricCard'
import { TrendChart } from '../components/member/TrendChart'
import { getDsrClass, formatHours, formatDate } from '../components/member/dsrFormat'
import { MyWorklogCalendar } from '../components/member/MyWorklogCalendar'
import { LogTimeModal, type LogTimeTarget } from '../components/member/LogTimeModal'
import { Modal } from '../components/Modal'
import './TeamsPage.css'
import './MemberProfilePage.css'
import './MyWorkPage.css'

export function defaultFrom(): string {
  const d = new Date()
  d.setDate(d.getDate() - 90)
  return localDateKey(d)
}

export function defaultTo(): string {
  return localDateKey(new Date())
}

function overHoursClass(estimateH: number | null, spentH: number | null): string {
  if (estimateH == null || spentH == null) return ''
  return spentH > estimateH ? 'over' : ''
}

// ======================== TASK TABLES ========================
//
// The three task lists (In Progress / Up Next / Team Queue) share one column
// layout — Type | Key | Summary | Story/Epic | Status | Team | a right-aligned
// numeric column whose content differs per section. Callers adapt their data
// into TaskRow[] (see taskRows/queueRows below) so the table markup — and
// therefore the <colgroup> widths — stays identical across all three tables.

interface TaskRow {
  key: string
  jiraUrl: string
  issueType: string
  summary: string
  status: string
  teamName: string | null
  teamColor: string | null
  parentLabel: string | null
  epicLabel: string | null
  right: ReactNode
  onLog?: () => void
}

function taskRows(tasks: MyTask[], showSpent: boolean, onLog?: (target: LogTimeTarget) => void): TaskRow[] {
  return tasks.map(t => ({
    key: t.key,
    jiraUrl: t.jiraUrl,
    issueType: t.issueType,
    summary: t.summary,
    status: t.status,
    teamName: t.teamName,
    teamColor: t.teamColor,
    parentLabel: t.parentSummary,
    epicLabel: t.epicSummary,
    right: showSpent ? (
      <>
        <span className="task-hours">{formatHours(t.estimateH)}</span>
        <span className="mywork-hours-sep"> / </span>
        <span className={`task-hours ${overHoursClass(t.estimateH, t.spentH)}`}>{formatHours(t.spentH)}</span>
      </>
    ) : (
      <span className="task-hours">{formatHours(t.estimateH)}</span>
    ),
    onLog: onLog
      ? () => onLog({ key: t.key, summary: t.summary, originalEstimateH: t.estimateH, spentH: t.spentH, remainingH: t.remainingH })
      : undefined,
  }))
}

// Team Queue rows never get a log button — those stories aren't assigned to the
// viewer, so there's nothing of theirs to log time against.
function queueRows(stories: QueueStory[]): TaskRow[] {
  return stories.map(s => ({
    key: s.key,
    jiraUrl: s.jiraUrl,
    issueType: s.issueType,
    summary: s.summary,
    status: s.status,
    teamName: s.teamName,
    teamColor: s.teamColor,
    parentLabel: null,
    epicLabel: s.epicSummary,
    right: (
      <>{s.myPhaseSubtasks} subtask{s.myPhaseSubtasks === 1 ? '' : 's'} &middot; {formatHours(s.myPhaseEstimateH)}</>
    ),
  }))
}

interface TaskTableProps {
  rows: TaskRow[]
  rightHeader: string
  emptyLabel: string
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}

function TaskTable({ rows, rightHeader, emptyLabel, getIssueTypeIconUrl, getIssueTypeCategory }: TaskTableProps) {
  if (rows.length === 0) {
    return <EmptyState variant="inline" message={emptyLabel} />
  }
  return (
    <div className="mywork-task-scroll">
      <table className="profile-tasks-table mywork-task-table">
      <colgroup>
        <col className="mywork-col-type" />
        <col className="mywork-col-key" />
        <col className="mywork-col-summary" />
        <col className="mywork-col-parent" />
        <col className="mywork-col-status" />
        <col className="mywork-col-team" />
        <col className="mywork-col-right" />
        <col className="mywork-col-log" />
      </colgroup>
      <thead>
        <tr>
          <th>Type</th>
          <th>Key</th>
          <th>Summary</th>
          <th>Story/Epic</th>
          <th>Status</th>
          <th>Team</th>
          <th className="mywork-col-right-cell">{rightHeader}</th>
          <th aria-label="Log time" />
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.key}>
            <td>
              <img
                src={getIssueIcon(r.issueType, getIssueTypeIconUrl(r.issueType), getIssueTypeCategory(r.issueType))}
                className="issue-type-icon"
                alt={r.issueType}
              />
            </td>
            <td>
              <a href={r.jiraUrl} target="_blank" rel="noopener noreferrer" className="task-key">{r.key}</a>
            </td>
            <td className="task-summary-cell">{r.summary}</td>
            <td className="task-summary-cell">
              {r.parentLabel}
              {r.epicLabel && <span className="task-epic-label">{r.epicLabel}</span>}
              {!r.parentLabel && !r.epicLabel && '—'}
            </td>
            <td><StatusBadge status={r.status} /></td>
            <td><TeamBadge name={r.teamName} color={r.teamColor} /></td>
            <td className="mywork-col-right-cell">{r.right}</td>
            <td>{r.onLog && <button type="button" className="mywork-log-btn" title="Log time" onClick={r.onLog}>+</button>}</td>
          </tr>
        ))}
      </tbody>
      </table>
    </div>
  )
}

function FocusTaskRows({ rows, emptyLabel, getIssueTypeIconUrl, getIssueTypeCategory }: {
  rows: TaskRow[]
  emptyLabel: string
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}) {
  if (rows.length === 0) return <EmptyState variant="inline" message={emptyLabel} />

  return (
    <div className="mywork-focus-tasks">
      {rows.map(row => (
        <div className={`mywork-focus-task ${row.onLog ? '' : 'mywork-focus-task--no-log'}`.trim()} key={row.key}>
          <img
            src={getIssueIcon(row.issueType, getIssueTypeIconUrl(row.issueType), getIssueTypeCategory(row.issueType))}
            className="issue-type-icon"
            alt={row.issueType}
          />
          <div className="mywork-focus-task-main">
            <div className="mywork-focus-task-heading">
              <a href={row.jiraUrl} target="_blank" rel="noopener noreferrer" className="task-key">{row.key}</a>
              <span className="mywork-focus-task-summary">{row.summary}</span>
            </div>
            <div className="mywork-focus-task-context">
              {row.parentLabel || row.epicLabel || 'No parent'}
              {row.parentLabel && row.epicLabel && <span> &middot; {row.epicLabel}</span>}
            </div>
          </div>
          <div className="mywork-focus-task-badges">
            <StatusBadge status={row.status} />
            <TeamBadge name={row.teamName} color={row.teamColor} />
          </div>
          <div className="mywork-focus-task-hours">{row.right}</div>
          {row.onLog && (
            <button type="button" className="mywork-log-btn" title="Log time" onClick={row.onLog}>+</button>
          )}
        </div>
      ))}
    </div>
  )
}

function FocusTaskList({ tasks, showSpent, emptyLabel, onLog, getIssueTypeIconUrl, getIssueTypeCategory }: {
  tasks: MyTask[]
  showSpent: boolean
  emptyLabel: string
  onLog: (target: LogTimeTarget) => void
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}) {
  return (
    <FocusTaskRows
      rows={taskRows(tasks, showSpent, onLog)}
      emptyLabel={emptyLabel}
      getIssueTypeIconUrl={getIssueTypeIconUrl}
      getIssueTypeCategory={getIssueTypeCategory}
    />
  )
}

function TaskSection({ title, count, children, defaultExpanded = true, className = '' }: { title: string; count: number; children: ReactNode; defaultExpanded?: boolean; className?: string }) {
  const [expanded, setExpanded] = useState(defaultExpanded)
  return (
    <div className={`profile-section full-width ${className}`.trim()}>
      <button
        type="button"
        className="profile-section-header mywork-section-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded(v => !v)}
      >
        <span className="mywork-section-title">
          <span className={`mywork-section-chevron ${expanded ? 'expanded' : ''}`} aria-hidden="true">&#9656;</span>
          <h3>{title}</h3>
        </span>
        <span className="section-badge">{count}</span>
      </button>
      {expanded && children}
    </div>
  )
}

function TeamQueueSection({ stories, getIssueTypeIconUrl, getIssueTypeCategory }: {
  stories: QueueStory[]
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}) {
  const [expanded, setExpanded] = useState(false)
  const rows = queueRows(stories)
  const previewRows = rows.slice(0, 3)

  return (
    <div className="profile-section full-width mywork-team-queue">
      <button
        type="button"
        className="profile-section-header mywork-section-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded(value => !value)}
      >
        <span className="mywork-section-title">
          <span className={`mywork-section-chevron ${expanded ? 'expanded' : ''}`} aria-hidden="true">&#9656;</span>
          <h3>Team Queue</h3>
          {!expanded && rows.length > 0 && (
            <span className="mywork-queue-preview-label">Top {Math.min(3, rows.length)}</span>
          )}
        </span>
        <span className="section-badge">{rows.length}</span>
      </button>

      {expanded ? (
        <TaskTable
          rows={rows}
          rightHeader="My Phase"
          emptyLabel="No stories in the queue"
          getIssueTypeIconUrl={getIssueTypeIconUrl}
          getIssueTypeCategory={getIssueTypeCategory}
        />
      ) : (
        <>
          <FocusTaskRows
            rows={previewRows}
            emptyLabel="No stories in the queue"
            getIssueTypeIconUrl={getIssueTypeIconUrl}
            getIssueTypeCategory={getIssueTypeCategory}
          />
          {rows.length > previewRows.length && (
            <button type="button" className="mywork-queue-more" onClick={() => setExpanded(true)}>
              View all {rows.length} tasks
            </button>
          )}
        </>
      )}
    </div>
  )
}

// ======================== DAILY FOCUS SIDEBAR ========================

const WEEKDAY_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function currentWeek(days: CalendarDay[]): CalendarDay[] {
  const now = new Date()
  const monday = new Date(now)
  monday.setHours(0, 0, 0, 0)
  monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  const from = localDateKey(monday)
  const to = localDateKey(sunday)
  return days.filter(day => day.date >= from && day.date <= to)
}

function WorklogFocusPanel({ days, onOpen }: { days: CalendarDay[]; onOpen: () => void }) {
  const week = currentWeek(days)
  const today = localDateKey(new Date())
  const loggedH = week.reduce((sum, day) => sum + day.loggedH, 0)
  const normH = week.reduce((sum, day) => sum + day.normH, 0)

  return (
    <div className="profile-section mywork-focus-panel mywork-focus-card mywork-focus-card--time">
      <div className="profile-section-header">
        <div>
          <h3>Time Logged</h3>
          <span className="mywork-focus-subtitle">This week</span>
        </div>
        <strong className="mywork-focus-total">{formatHours(loggedH)} / {formatHours(normH)}</strong>
      </div>
      <div className="mywork-week-preview">
        {week.length > 0 ? week.map((day, index) => {
          const isFuture = day.date > today
          const isLow = day.dayType === 'WORKDAY' && !day.absenceType && !isFuture && day.loggedH < day.normH * 0.5
          const isComplete = day.dayType === 'WORKDAY' && !day.absenceType && day.loggedH >= day.normH && day.normH > 0
          return (
            <div
              key={day.date}
              className={[
                'mywork-week-day',
                day.date === today ? 'today' : '',
                isLow ? 'low' : '',
                isComplete ? 'complete' : '',
                day.dayType !== 'WORKDAY' || day.absenceType ? 'off' : '',
                isFuture ? 'future' : '',
              ].filter(Boolean).join(' ')}
              title={`${day.date}: ${formatHours(day.loggedH)} / ${formatHours(day.normH)}`}
            >
              <span>{WEEKDAY_SHORT[index]}</span>
              <strong>{day.loggedH}h</strong>
            </div>
          )
        }) : (
          <EmptyState variant="inline" message="No worklog data for this week" />
        )}
      </div>
      <button type="button" className="mywork-focus-action" aria-haspopup="dialog" onClick={onOpen}>
        Open monthly calendar
      </button>
    </div>
  )
}

function PerformanceSnapshot({ analytics, onOpen }: { analytics: MyAnalytics; onOpen: () => void }) {
  const summary = analytics.summary
  return (
    <div className="profile-section mywork-focus-panel mywork-focus-card mywork-focus-card--performance">
      <div className="profile-section-header">
        <div>
          <h3>Performance Snapshot</h3>
          <span className="mywork-focus-subtitle">Selected period</span>
        </div>
      </div>
      <div className="mywork-snapshot-grid">
        <div><span>Closed</span><strong>{summary.completedCount}</strong></div>
        <div><span>Avg DSR</span><strong>{summary.avgDsr?.toFixed(2) ?? '—'}</strong></div>
        <div><span>Cycle</span><strong>{summary.avgCycleTimeDays != null ? `${summary.avgCycleTimeDays}d` : '—'}</strong></div>
      </div>
      <button type="button" className="mywork-focus-action" aria-haspopup="dialog" onClick={onOpen}>
        View performance details
      </button>
    </div>
  )
}

// ======================== PERFORMANCE ANALYTICS (Task 11) ========================

function DsrBreakdownTable({ title, rows }: { title: string; rows: DsrBreakdown[] }) {
  return (
    <div className="profile-section">
      <div className="profile-section-header">
        <h3>{title}</h3>
        <span className="section-badge">{rows.length}</span>
      </div>
      {rows.length === 0 ? (
        <EmptyState variant="inline" message="No data for this period" />
      ) : (
        <table className="profile-tasks-table">
          <thead>
            <tr>
              <th>Label</th>
              <th style={{ textAlign: 'right' }}>Tasks</th>
              <th style={{ textAlign: 'right' }}>Est</th>
              <th style={{ textAlign: 'right' }}>Spent</th>
              <th style={{ textAlign: 'center' }}>DSR</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.key}>
                <td className="task-summary-cell">{r.label}</td>
                <td style={{ textAlign: 'right' }}>{r.taskCount}</td>
                <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(r.estimateH)}</td>
                <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(r.spentH)}</td>
                <td style={{ textAlign: 'center' }}>
                  <span className={`dsr-badge ${getDsrClass(r.dsr)}`}>{r.dsr?.toFixed(2) ?? '—'}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function CompletedTasksTable({ tasks, onLog }: { tasks: MyCompletedTask[]; onLog: (target: LogTimeTarget) => void }) {
  if (tasks.length === 0) {
    return <EmptyState variant="inline" message="No completed tasks in this period" />
  }
  return (
    <table className="profile-tasks-table">
      <thead>
        <tr>
          <th>Key</th>
          <th>Summary</th>
          <th>Epic</th>
          <th>Team</th>
          <th style={{ textAlign: 'right' }}>Est</th>
          <th style={{ textAlign: 'right' }}>Spent</th>
          <th style={{ textAlign: 'center' }}>DSR</th>
          <th>Done</th>
          <th aria-label="Log time" />
        </tr>
      </thead>
      <tbody>
        {tasks.map(t => (
          <tr key={t.key}>
            <td>
              <a href={t.jiraUrl} target="_blank" rel="noopener noreferrer" className="task-key">{t.key}</a>
            </td>
            <td className="task-summary-cell">{t.summary}</td>
            <td className="task-summary-cell">
              {t.epicKey ? <span className="task-epic-label">{`${t.epicKey} ${t.epicSummary ?? ''}`.trim()}</span> : '—'}
            </td>
            <td><TeamBadge name={t.teamName} color={t.teamColor} /></td>
            <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(t.estimateH)}</td>
            <td style={{ textAlign: 'right' }}>
              <span className={`task-hours ${overHoursClass(t.estimateH, t.spentH)}`}>{formatHours(t.spentH)}</span>
            </td>
            <td style={{ textAlign: 'center' }}>
              <span className={`dsr-badge ${getDsrClass(t.dsr)}`}>{t.dsr?.toFixed(2) ?? '—'}</span>
            </td>
            <td>{formatDate(t.doneDate)}</td>
            <td>
              <button
                type="button"
                className="mywork-log-btn"
                title="Log time"
                onClick={() => onLog({ key: t.key, summary: t.summary, originalEstimateH: t.estimateH, spentH: t.spentH, remainingH: t.remainingH })}
              >
                +
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

interface MyPerformanceSectionProps {
  analytics: MyAnalytics
  from: string
  to: string
  onFromChange: (value: string) => void
  onToChange: (value: string) => void
  onLog: (target: LogTimeTarget) => void
}

function MyPerformanceSection({ analytics, from, to, onFromChange, onToChange, onLog }: MyPerformanceSectionProps) {
  const s = analytics.summary
  return (
    <div className="mywork-analytics-detail">
      <div className="mywork-analytics-header">
        <span className="mywork-analytics-description">Detailed personal analytics across all teams</span>
        <div className="member-profile-period">
          <input type="date" aria-label="From" value={from} onChange={e => onFromChange(e.target.value)} />
          <span>—</span>
          <input type="date" aria-label="To" value={to} onChange={e => onToChange(e.target.value)} />
        </div>
      </div>

      <div className="member-summary-cards mywork-performance-cards">
        <MetricCard title="Closed tasks" value={s.completedCount} />
        <MetricCard
          title="Avg DSR"
          value={s.avgDsr != null ? s.avgDsr.toFixed(2) : '—'}
          tooltip="Time spent / original estimate"
        />
        <MetricCard
          title="Cycle Time"
          value={s.avgCycleTimeDays != null ? `${s.avgCycleTimeDays} d` : '—'}
        />
        <MetricCard
          title="Hours"
          value={`${s.totalSpentH} / ${s.totalEstimateH}`}
          subtitle="spent / estimate"
        />
      </div>

      <div className="profile-section full-width" style={{ marginBottom: 16 }}>
        <div className="profile-section-header">
          <h3>Weekly Trend</h3>
        </div>
        {analytics.weeklyTrend.length > 0 ? (
          <TrendChart data={analytics.weeklyTrend} />
        ) : (
          <EmptyState variant="inline" message="No trend data for this period" />
        )}
      </div>

      <div className="profile-section full-width" style={{ marginBottom: 16 }}>
        <div className="profile-section-header">
          <h3>Completed</h3>
          <span className="section-badge">{analytics.completedTasks.length}</span>
        </div>
        <CompletedTasksTable tasks={analytics.completedTasks} onLog={onLog} />
      </div>

      <div className="member-profile-grid">
        <DsrBreakdownTable title="DSR by Task Type" rows={analytics.dsrByParentType} />
        <DsrBreakdownTable title="DSR by Epic" rows={analytics.dsrByEpic} />
      </div>
    </div>
  )
}

// ======================== MAIN PAGE ========================

export function MyWorkPage() {
  const { getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()

  const [data, setData] = useState<MyWorkResponse | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [teamFilter, setTeamFilter] = useState<number | undefined>(undefined)
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [logTarget, setLogTarget] = useState<LogTimeTarget | null>(null)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [performanceOpen, setPerformanceOpen] = useState(false)

  // Status colors are shared across the tenant and don't depend on the
  // selected period/team — load once.
  useEffect(() => {
    getStatusStyles().then(setStatusStyles).catch(() => {})
  }, [])

  const loadMyWork = useCallback(() => {
    setLoading(true)
    setError(null)
    myWorkApi.getMyWork(from, to, teamFilter)
      .then(setData)
      .catch((e: unknown) => {
        const axiosErr = e as { response?: { data?: { error?: string } }; message?: string }
        setError(axiosErr.response?.data?.error || axiosErr.message || 'Failed to load your work')
      })
      .finally(() => setLoading(false))
  }, [from, to, teamFilter])

  useEffect(() => {
    loadMyWork()
  }, [loadMyWork])

  let content: ReactNode
  if (error) {
    content = <div className="error">{error}</div>
  } else if (loading && !data) {
    content = <div className="loading">Loading&hellip;</div>
  } else if (!data) {
    content = null
  } else if (!data.hasMembership || !data.member) {
    content = (
      <EmptyState message="You are not a member of any team yet. Ask your team lead to add you." />
    )
  } else {
    const { member, upcomingAbsences, activeTasks, upcomingAssigned, teamQueue, worklogCalendar } = data
    const initials = member.displayName ? member.displayName.split(' ').map(w => w[0]).join('') : '?'
    // Current calendar month ('YYYY-MM') — the calendar's starting page; /api/me/work
    // returns worklogCalendar for exactly this month.
    const now = new Date()
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

    content = (
      <>
        <div className="mywork-header">
          {member.avatarUrl ? (
            <img src={member.avatarUrl} alt={member.displayName} className="member-avatar member-avatar-img" />
          ) : (
            <div className="member-avatar">{initials}</div>
          )}
          <div className="member-info">
            <h2>{member.displayName}</h2>
            <div className="member-info-badges">
              <RoleBadge role={member.role} />
              <GradeBadge grade={member.grade} />
              {member.teams.map(t => (
                <TeamBadge key={t.teamId} name={t.teamName} color={t.teamColor} />
              ))}
              <span className="member-info-team">{member.hoursPerDay}h/day</span>
            </div>
            {upcomingAbsences.length > 0 && (
              <div className="mywork-absence-chips">
                {upcomingAbsences.map(a => (
                  <div
                    key={a.id}
                    className="mywork-absence-chip"
                    style={{
                      background: hexToRgba(ABSENCE_COLORS[a.absenceType], 0.08),
                      border: `1px solid ${hexToRgba(ABSENCE_COLORS[a.absenceType], 0.19)}`,
                    }}
                  >
                    <span className="mywork-absence-dot" style={{ backgroundColor: ABSENCE_COLORS[a.absenceType] }} />
                    <span style={{ fontWeight: 600, color: ABSENCE_COLORS[a.absenceType] }}>
                      {ABSENCE_TYPE_LABELS[a.absenceType]}
                    </span>
                    <span style={{ color: TEXT_MUTED }}>
                      {formatDate(a.startDate)} &mdash; {formatDate(a.endDate)}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {member.teams.length > 1 && (
          <div className="mywork-team-chips">
            <button
              type="button"
              className={`chip ${teamFilter === undefined ? 'active' : ''}`}
              onClick={() => setTeamFilter(undefined)}
            >
              All teams
            </button>
            {member.teams.map(t => (
              <button
                key={t.teamId}
                type="button"
                className={`chip ${teamFilter === t.teamId ? 'active' : ''}`}
                onClick={() => setTeamFilter(t.teamId)}
              >
                {t.teamName}
              </button>
            ))}
          </div>
        )}

        <div className={`mywork-focus-layout ${upcomingAssigned.length === 0 ? 'mywork-focus-layout--up-next-empty' : ''}`.trim()}>
          <TaskSection
            title="In Progress"
            count={activeTasks.length}
            className={`mywork-focus-card mywork-focus-card--active ${activeTasks.length === 0 ? 'mywork-focus-card--empty' : ''}`.trim()}
          >
            <FocusTaskList
              tasks={activeTasks}
              showSpent
              emptyLabel="No active tasks"
              onLog={setLogTarget}
              getIssueTypeIconUrl={getIssueTypeIconUrl}
              getIssueTypeCategory={getIssueTypeCategory}
            />
          </TaskSection>

          <WorklogFocusPanel days={worklogCalendar} onOpen={() => setCalendarOpen(true)} />

          <TaskSection
            title="Up Next"
            count={upcomingAssigned.length}
            className={`mywork-focus-card mywork-focus-card--next ${upcomingAssigned.length === 0 ? 'mywork-focus-card--empty' : ''}`.trim()}
          >
            <FocusTaskList
              tasks={upcomingAssigned}
              showSpent={false}
              emptyLabel="No upcoming tasks"
              onLog={setLogTarget}
              getIssueTypeIconUrl={getIssueTypeIconUrl}
              getIssueTypeCategory={getIssueTypeCategory}
            />
          </TaskSection>

          {data.analytics && (
            <PerformanceSnapshot analytics={data.analytics} onOpen={() => setPerformanceOpen(true)} />
          )}
        </div>

        <div className="mywork-secondary-sections">
          <TeamQueueSection
            stories={teamQueue}
            getIssueTypeIconUrl={getIssueTypeIconUrl}
            getIssueTypeCategory={getIssueTypeCategory}
          />
        </div>

        <Modal isOpen={calendarOpen} onClose={() => setCalendarOpen(false)} title="Monthly Worklog" maxWidth={980}>
          <div className="mywork-calendar-dialog">
            <MyWorklogCalendar initialDays={worklogCalendar} initialMonth={currentMonth} />
          </div>
        </Modal>

        {data.analytics && (
          <Modal isOpen={performanceOpen} onClose={() => setPerformanceOpen(false)} title="My Performance" maxWidth={1200}>
            <div className="mywork-performance-dialog">
              <MyPerformanceSection
                analytics={data.analytics}
                from={from}
                to={to}
                onFromChange={setFrom}
                onToChange={setTo}
                onLog={setLogTarget}
              />
            </div>
          </Modal>
        )}
      </>
    )
  }

  return (
    <main className="main-content">
      <StatusStylesProvider value={statusStyles}>
        {content}
      </StatusStylesProvider>
      <LogTimeModal target={logTarget} onClose={() => setLogTarget(null)} onLogged={loadMyWork} />
    </main>
  )
}
