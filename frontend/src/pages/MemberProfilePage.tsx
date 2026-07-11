import { Link, useParams } from 'react-router-dom'
import { useState, useEffect, useCallback } from 'react'
import { MetricCard } from '../components/metrics/MetricCard'
import { CompetencyRating } from '../components/competency/CompetencyRating'
import { teamsApi, MemberProfileResponse, Absence } from '../api/teams'
import { competencyApi, CompetencyLevel } from '../api/competency'
import { ABSENCE_TYPE_LABELS, ABSENCE_COLORS } from '../components/AbsenceModal'
import { ERROR_TEXT } from '../constants/colors'
import { TrendChart } from '../components/member/TrendChart'
import { StatusBadge } from '../components/board/StatusBadge'
import { RoleBadge } from '../components/RoleBadge'
import { GradeBadge } from '../components/GradeBadge'
import { getDsrClass, formatHours, formatDate } from '../components/member/dsrFormat'
import './TeamsPage.css'
import './MemberProfilePage.css'

// ======================== HELPERS ========================

function getDsrStatClass(dsr: number): string {
  if (dsr <= 1.0) return 'good'
  if (dsr <= 1.15) return 'warning'
  return 'bad'
}

function getDefaultFrom(): string {
  const d = new Date()
  d.setDate(d.getDate() - 30)
  return d.toISOString().slice(0, 10)
}

function getDefaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

// ======================== MAIN PAGE ========================

export function MemberProfilePage() {
  const { teamId, memberId } = useParams<{ teamId: string; memberId: string }>()
  const [data, setData] = useState<MemberProfileResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [from, setFrom] = useState(getDefaultFrom)
  const [to, setTo] = useState(getDefaultTo)

  const [competencies, setCompetencies] = useState<CompetencyLevel[]>([])
  const [availableComponents, setAvailableComponents] = useState<string[]>([])
  const [upcomingAbsences, setUpcomingAbsences] = useState<Absence[]>([])

  const loadProfile = useCallback(async () => {
    if (!teamId || !memberId) return
    setLoading(true)
    setError(null)
    try {
      const result = await teamsApi.getMemberProfile(Number(teamId), Number(memberId), from, to)
      setData(result)
    } catch (e: unknown) {
      const axiosErr = e as { response?: { data?: { error?: string } }; message?: string }
      setError(axiosErr.response?.data?.error || axiosErr.message || 'Failed to load profile')
    } finally {
      setLoading(false)
    }
  }, [teamId, memberId, from, to])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  useEffect(() => {
    if (!memberId || !teamId) return
    competencyApi.getMember(Number(memberId)).then(setCompetencies).catch(() => {})
    competencyApi.getComponents().then(setAvailableComponents).catch(() => {})
    teamsApi.getUpcomingAbsences(Number(teamId), Number(memberId)).then(setUpcomingAbsences).catch(() => {})
  }, [memberId, teamId])

  const handleCompetencyChange = async (componentName: string, level: number) => {
    if (!memberId) return
    const updated = competencies.some(c => c.componentName === componentName)
      ? competencies.map(c => c.componentName === componentName ? { ...c, level } : c)
      : [...competencies, { componentName, level }]
    setCompetencies(updated)
    try {
      const result = await competencyApi.updateMember(Number(memberId), [{ componentName, level }])
      setCompetencies(result)
    } catch { /* silent */ }
  }

  if (loading) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/teams/${teamId}`} className="back-link">&larr; Back to team</Link>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: '#6b778c' }}>Loading profile...</div>
      </main>
    )
  }

  if (error || !data) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/teams/${teamId}`} className="back-link">&larr; Back to team</Link>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: ERROR_TEXT }}>{error || 'No data'}</div>
      </main>
    )
  }

  const { member, completedTasks, activeTasks, upcomingTasks, weeklyTrend, summary } = data
  const initials = member.displayName ? member.displayName.split(' ').map(w => w[0]).join('') : '?'

  // Trend stats from weeklyTrend
  const trendWeeksWithTasks = weeklyTrend.filter(w => w.tasksCompleted > 0)
  const avgTasksPerWeek = trendWeeksWithTasks.length > 0
    ? (trendWeeksWithTasks.reduce((s, w) => s + w.tasksCompleted, 0) / trendWeeksWithTasks.length).toFixed(1)
    : '0'
  const avgHoursPerWeek = trendWeeksWithTasks.length > 0
    ? (trendWeeksWithTasks.reduce((s, w) => s + w.hoursLogged, 0) / trendWeeksWithTasks.length).toFixed(1)
    : '0'

  return (
    <main className="main-content">
      {/* Header */}
      <div className="page-header" style={{ marginBottom: 12 }}>
        <div className="page-header-left">
          <Link to={`/teams/${teamId}`} className="back-link">&larr; Back to team</Link>
        </div>
        <div className="member-profile-period">
          <label>Period:</label>
          <input type="date" value={from} onChange={e => setFrom(e.target.value)} />
          <span>—</span>
          <input type="date" value={to} onChange={e => setTo(e.target.value)} />
        </div>
      </div>

      {/* Profile Card */}
      <div className="member-profile-header">
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
            <span className="member-info-team">{member.teamName} &middot; {member.hoursPerDay}h/day</span>
          </div>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="member-summary-cards">
        <MetricCard
          title="Tasks closed"
          value={summary.completedCount}
          subtitle="for period"
          trend={summary.completedCount > 0 ? 'up' : 'neutral'}
          tooltip="Number of tasks in Done status for the selected period"
        />
        <MetricCard
          title="Average DSR"
          value={summary.avgDsr.toFixed(2)}
          subtitle={summary.avgDsr <= 1.0 ? 'Faster than estimate' : 'Slower than estimate'}
          trend={summary.avgDsr <= 1.0 ? 'up' : 'down'}
          tooltip="Delivery Speed Ratio = spent / estimated. Below 1.0 = works faster than estimated"
        />
        <MetricCard
          title="Cycle Time"
          value={`${summary.avgCycleTimeDays}d`}
          subtitle="average"
          trend="neutral"
          tooltip="Average time from start (In Progress) to completion (Done)"
        />
        <MetricCard
          title="Utilization"
          value={`${summary.utilization}%`}
          subtitle="capacity"
          trend="neutral"
          tooltip="Percentage of effective working time used for the period"
        />
        <MetricCard
          title="Hours"
          value={`${summary.totalSpentH}/${summary.totalEstimateH}`}
          subtitle="actual / estimate"
          tooltip="Total spent hours vs total estimate for the period"
        />
      </div>

      {/* Competency Section */}
      {availableComponents.length > 0 && (
        <div className="profile-section" style={{ marginBottom: 16 }}>
          <div className="profile-section-header">
            <h3>Competencies</h3>
            <span className="section-badge">{competencies.length}/{availableComponents.length}</span>
          </div>
          <div className="competency-grid">
            {availableComponents.map(comp => {
              const existing = competencies.find(c => c.componentName === comp)
              return (
                <div key={comp} className="competency-row">
                  <span className="competency-component-name">{comp}</span>
                  <CompetencyRating
                    level={existing?.level ?? 0}
                    onChange={level => handleCompetencyChange(comp, level)}
                    showLabel
                  />
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Upcoming Absences */}
      {upcomingAbsences.length > 0 && (
        <div className="profile-section" style={{ marginBottom: 16 }}>
          <div className="profile-section-header">
            <h3>Upcoming absences</h3>
            <span className="section-badge">{upcomingAbsences.length}</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, padding: '8px 0' }}>
            {upcomingAbsences.map(a => (
              <div key={a.id} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '6px 12px',
                borderRadius: 6,
                background: ABSENCE_COLORS[a.absenceType] + '15',
                border: `1px solid ${ABSENCE_COLORS[a.absenceType]}30`,
                fontSize: 13,
              }}>
                <span style={{
                  display: 'inline-block',
                  width: 8,
                  height: 8,
                  borderRadius: 2,
                  backgroundColor: ABSENCE_COLORS[a.absenceType],
                  flexShrink: 0,
                }} />
                <span style={{ fontWeight: 600, color: ABSENCE_COLORS[a.absenceType] }}>
                  {ABSENCE_TYPE_LABELS[a.absenceType]}
                </span>
                <span style={{ color: '#6b778c' }}>
                  {formatDate(a.startDate)} — {formatDate(a.endDate)}
                </span>
                {a.comment && (
                  <span style={{ color: '#97a0af', fontSize: 12 }}>{a.comment}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Main Grid */}
      <div className="member-profile-grid">
        {/* ===== Completed Tasks ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Completed tasks</h3>
            <span className="section-badge">{completedTasks.length}</span>
          </div>
          {completedTasks.length === 0 ? (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              No completed tasks for the selected period
            </div>
          ) : (
            <table className="profile-tasks-table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Description</th>
                  <th style={{ textAlign: 'right' }}>Estimate</th>
                  <th style={{ textAlign: 'right' }}>Actual</th>
                  <th style={{ textAlign: 'center' }}>DSR</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {completedTasks.map(task => (
                  <tr key={task.key}>
                    <td><span className="task-key">{task.key}</span></td>
                    <td className="task-summary-cell">
                      {task.summary}
                      {task.epicKey && (
                        <span className="task-epic-label">{task.epicKey} {task.epicSummary}</span>
                      )}
                    </td>
                    <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <span className={`task-hours ${task.spentH > task.estimateH ? 'over' : 'under'}`}>
                        {formatHours(task.spentH)}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      {task.dsr != null ? (
                        <span className={`dsr-badge ${getDsrClass(task.dsr)}`}>
                          {task.dsr.toFixed(2)}
                        </span>
                      ) : '—'}
                    </td>
                    <td style={{ color: '#6b778c', fontSize: 13 }}>{formatDate(task.doneDate)}</td>
                  </tr>
                ))}
                {/* Totals row */}
                <tr className="tasks-totals-row">
                  <td colSpan={2}>Total</td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(summary.totalEstimateH)}</td>
                  <td style={{ textAlign: 'right' }}>
                    <span className={`task-hours ${summary.totalSpentH > summary.totalEstimateH ? 'over' : 'under'}`}>
                      {formatHours(summary.totalSpentH)}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span className={`dsr-badge ${getDsrClass(summary.avgDsr)}`}>
                      {summary.avgDsr.toFixed(2)}
                    </span>
                  </td>
                  <td />
                </tr>
              </tbody>
            </table>
          )}
        </div>

        {/* ===== Trend Chart ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Weekly trend</h3>
          </div>
          {weeklyTrend.length > 0 ? (
            <>
              <TrendChart data={weeklyTrend} />
              <div className="profile-stats-row">
                <div className="profile-stat">
                  <div className="profile-stat-label">DSR trend</div>
                  <div className={`profile-stat-value ${getDsrStatClass(summary.avgDsr)}`}>
                    {summary.avgDsr < 1 ? '\u2191' : '\u2193'} {summary.avgDsr.toFixed(2)}
                  </div>
                  <div className="profile-stat-hint">for period</div>
                </div>
                <div className="profile-stat">
                  <div className="profile-stat-label">Tasks / week</div>
                  <div className="profile-stat-value">{avgTasksPerWeek}</div>
                  <div className="profile-stat-hint">average</div>
                </div>
                <div className="profile-stat">
                  <div className="profile-stat-label">Hours / week</div>
                  <div className="profile-stat-value">{avgHoursPerWeek}</div>
                  <div className="profile-stat-hint">logged</div>
                </div>
              </div>
            </>
          ) : (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              No data to show the trend
            </div>
          )}
        </div>

        {/* ===== In Progress + Upcoming ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Current and upcoming tasks</h3>
            <span className="section-badge">{activeTasks.length + upcomingTasks.length}</span>
          </div>
          {activeTasks.length === 0 && upcomingTasks.length === 0 ? (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              No current or upcoming tasks
            </div>
          ) : (
            <table className="profile-tasks-table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Description</th>
                  <th style={{ textAlign: 'center' }}>Status</th>
                  <th style={{ textAlign: 'right' }}>Estimate</th>
                  <th style={{ textAlign: 'right' }}>Spent</th>
                </tr>
              </thead>
              <tbody>
                {activeTasks.map(task => (
                  <tr key={task.key}>
                    <td><span className="task-key">{task.key}</span></td>
                    <td className="task-summary-cell">
                      {task.summary}
                      {task.epicKey && (
                        <span className="task-epic-label">{task.epicKey} {task.epicSummary}</span>
                      )}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <StatusBadge status={task.status} />
                    </td>
                    <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                    <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.spentH)}</td>
                  </tr>
                ))}
                {upcomingTasks.map(task => (
                  <tr key={task.key} style={{ opacity: 0.7 }}>
                    <td><span className="task-key">{task.key}</span></td>
                    <td className="task-summary-cell">
                      {task.summary}
                      {task.epicKey && (
                        <span className="task-epic-label">{task.epicKey} {task.epicSummary}</span>
                      )}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <StatusBadge status={task.status} />
                    </td>
                    <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                    <td className="task-hours" style={{ textAlign: 'right' }}>—</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </main>
  )
}
