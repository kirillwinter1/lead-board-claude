import { useState, useEffect, useCallback, type ReactNode } from 'react'
import { myWorkApi, type MyWorkResponse, type MyTask, type QueueStory, type MyAnalytics, type MyCompletedTask, type DsrBreakdown } from '../api/myWork'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { StatusBadge } from '../components/board/StatusBadge'
import { TeamBadge } from '../components/TeamBadge'
import { getIssueIcon } from '../components/board/helpers'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { ABSENCE_TYPE_LABELS, ABSENCE_COLORS } from '../components/AbsenceModal'
import { MetricCard } from '../components/metrics/MetricCard'
import { TrendChart } from '../components/member/TrendChart'
import { getDsrClass, formatHours, formatDate } from '../components/member/dsrFormat'
import { MyWorklogCalendar } from '../components/member/MyWorklogCalendar'
import './TeamsPage.css'
import './MemberProfilePage.css'
import './MyWorkPage.css'

function defaultFrom(): string {
  const d = new Date()
  d.setDate(d.getDate() - 90)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function overHoursClass(estimateH: number | null, spentH: number | null): string {
  if (estimateH == null || spentH == null) return ''
  return spentH > estimateH ? 'over' : ''
}

// ======================== TASK TABLES ========================

interface MyTaskTableProps {
  tasks: MyTask[]
  showSpent: boolean
  emptyLabel: string
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}

function MyTaskTable({ tasks, showSpent, emptyLabel, getIssueTypeIconUrl, getIssueTypeCategory }: MyTaskTableProps) {
  if (tasks.length === 0) {
    return <div className="mywork-section-empty">{emptyLabel}</div>
  }
  return (
    <table className="profile-tasks-table">
      <thead>
        <tr>
          <th>Type</th>
          <th>Key</th>
          <th>Summary</th>
          <th>Story/Epic</th>
          <th>Status</th>
          <th>Team</th>
          <th style={{ textAlign: 'right' }}>Est</th>
          {showSpent && <th style={{ textAlign: 'right' }}>Spent</th>}
        </tr>
      </thead>
      <tbody>
        {tasks.map(t => (
          <tr key={t.key}>
            <td>
              <img
                src={getIssueIcon(t.issueType, getIssueTypeIconUrl(t.issueType), getIssueTypeCategory(t.issueType))}
                className="issue-type-icon"
                alt={t.issueType}
              />
            </td>
            <td>
              <a href={t.jiraUrl} target="_blank" rel="noopener noreferrer" className="task-key">{t.key}</a>
            </td>
            <td className="task-summary-cell">{t.summary}</td>
            <td className="task-summary-cell">
              {t.parentSummary}
              {t.epicSummary && <span className="task-epic-label">{t.epicSummary}</span>}
            </td>
            <td><StatusBadge status={t.status} /></td>
            <td><TeamBadge name={t.teamName} color={t.teamColor} /></td>
            <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(t.estimateH)}</td>
            {showSpent && (
              <td style={{ textAlign: 'right' }}>
                <span className={`task-hours ${overHoursClass(t.estimateH, t.spentH)}`}>{formatHours(t.spentH)}</span>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

interface TeamQueueTableProps {
  stories: QueueStory[]
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  getIssueTypeCategory: (typeName: string | null | undefined) => string | null
}

function TeamQueueTable({ stories, getIssueTypeIconUrl, getIssueTypeCategory }: TeamQueueTableProps) {
  if (stories.length === 0) {
    return <div className="mywork-section-empty">No stories in the queue</div>
  }
  return (
    <table className="profile-tasks-table">
      <thead>
        <tr>
          <th>Type</th>
          <th>Key</th>
          <th>Summary</th>
          <th>Epic</th>
          <th>Status</th>
          <th>Team</th>
          <th>My Phase</th>
        </tr>
      </thead>
      <tbody>
        {stories.map(s => (
          <tr key={s.key}>
            <td>
              <img
                src={getIssueIcon(s.issueType, getIssueTypeIconUrl(s.issueType), getIssueTypeCategory(s.issueType))}
                className="issue-type-icon"
                alt={s.issueType}
              />
            </td>
            <td>
              <a href={s.jiraUrl} target="_blank" rel="noopener noreferrer" className="task-key">{s.key}</a>
            </td>
            <td className="task-summary-cell">{s.summary}</td>
            <td className="task-summary-cell">
              {s.epicSummary ? <span className="task-epic-label">{s.epicSummary}</span> : '—'}
            </td>
            <td><StatusBadge status={s.status} /></td>
            <td><TeamBadge name={s.teamName} color={s.teamColor} /></td>
            <td>{s.myPhaseSubtasks} subtasks &middot; {formatHours(s.myPhaseEstimateH)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function TaskSection({ title, count, children }: { title: string; count: number; children: ReactNode }) {
  return (
    <div className="profile-section full-width">
      <div className="profile-section-header">
        <h3>{title}</h3>
        <span className="section-badge">{count}</span>
      </div>
      {children}
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
        <div className="mywork-section-empty">No data for this period</div>
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

function CompletedTasksTable({ tasks }: { tasks: MyCompletedTask[] }) {
  if (tasks.length === 0) {
    return <div className="mywork-section-empty">No completed tasks in this period</div>
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
}

function MyPerformanceSection({ analytics, from, to, onFromChange, onToChange }: MyPerformanceSectionProps) {
  const s = analytics.summary
  return (
    <div className="mywork-analytics">
      <div className="mywork-analytics-header">
        <h3>My Performance</h3>
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
          <div className="mywork-section-empty">No trend data for this period</div>
        )}
      </div>

      <div className="profile-section full-width" style={{ marginBottom: 16 }}>
        <div className="profile-section-header">
          <h3>Completed</h3>
          <span className="section-badge">{analytics.completedTasks.length}</span>
        </div>
        <CompletedTasksTable tasks={analytics.completedTasks} />
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
  const { getIssueTypeIconUrl, getIssueTypeCategory, getRoleColor, getRoleDisplayName } = useWorkflowConfig()

  const [data, setData] = useState<MyWorkResponse | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [teamFilter, setTeamFilter] = useState<number | undefined>(undefined)
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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
      <div className="mywork-empty">You are not a member of any team yet. Ask your team lead to add you.</div>
    )
  } else {
    const { member, upcomingAbsences, activeTasks, upcomingAssigned, teamQueue, worklogCalendar } = data
    const initials = member.displayName ? member.displayName.split(' ').map(w => w[0]).join('') : '?'

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
              <span
                className="role-badge"
                style={{ backgroundColor: getRoleColor(member.role) + '20', color: getRoleColor(member.role) }}
              >
                {getRoleDisplayName(member.role)}
              </span>
              <span className={`grade-badge ${member.grade.toLowerCase()}`}>{member.grade}</span>
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
                      background: ABSENCE_COLORS[a.absenceType] + '15',
                      border: `1px solid ${ABSENCE_COLORS[a.absenceType]}30`,
                    }}
                  >
                    <span className="mywork-absence-dot" style={{ backgroundColor: ABSENCE_COLORS[a.absenceType] }} />
                    <span style={{ fontWeight: 600, color: ABSENCE_COLORS[a.absenceType] }}>
                      {ABSENCE_TYPE_LABELS[a.absenceType]}
                    </span>
                    <span style={{ color: '#6b778c' }}>
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

        <div className="mywork-sections">
          <TaskSection title="In Progress" count={activeTasks.length}>
            <MyTaskTable
              tasks={activeTasks}
              showSpent
              emptyLabel="No active tasks"
              getIssueTypeIconUrl={getIssueTypeIconUrl}
              getIssueTypeCategory={getIssueTypeCategory}
            />
          </TaskSection>

          <TaskSection title="Up Next" count={upcomingAssigned.length}>
            <MyTaskTable
              tasks={upcomingAssigned}
              showSpent={false}
              emptyLabel="No upcoming tasks"
              getIssueTypeIconUrl={getIssueTypeIconUrl}
              getIssueTypeCategory={getIssueTypeCategory}
            />
          </TaskSection>

          <TaskSection title="Team Queue" count={teamQueue.length}>
            <TeamQueueTable
              stories={teamQueue}
              getIssueTypeIconUrl={getIssueTypeIconUrl}
              getIssueTypeCategory={getIssueTypeCategory}
            />
          </TaskSection>
        </div>

        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Time Logged &mdash; Last 4 Weeks</h3>
          </div>
          <MyWorklogCalendar days={worklogCalendar} />
        </div>

        {data.analytics && (
          <MyPerformanceSection
            analytics={data.analytics}
            from={from}
            to={to}
            onFromChange={setFrom}
            onToChange={setTo}
          />
        )}
      </>
    )
  }

  return (
    <main className="main-content">
      <StatusStylesProvider value={statusStyles}>
        {content}
      </StatusStylesProvider>
    </main>
  )
}
