import { Link, useParams } from 'react-router-dom'
import { useState, useEffect, useCallback } from 'react'
import { MetricCard } from '../components/metrics/MetricCard'
import { teamsApi, MemberProfileResponse, WeeklyTrend } from '../api/teams'
import './TeamsPage.css'
import './MemberProfilePage.css'

// ======================== HELPERS ========================

function getDsrClass(dsr: number): string {
  if (dsr <= 1.0) return 'good'
  if (dsr <= 1.2) return 'warning'
  return 'bad'
}

function getDsrStatClass(dsr: number): string {
  if (dsr <= 1.0) return 'good'
  if (dsr <= 1.15) return 'warning'
  return 'bad'
}

function formatHours(h: number): string {
  if (h == null) return '—'
  return h % 1 === 0 ? `${h}h` : `${h.toFixed(1)}h`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function getDefaultFrom(): string {
  const d = new Date()
  d.setDate(d.getDate() - 30)
  return d.toISOString().slice(0, 10)
}

function getDefaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

// ======================== TREND CHART ========================

function TrendChart({ data }: { data: WeeklyTrend[] }) {
  const W = 500, H = 160, PX = 40, PY = 20
  const chartW = W - PX * 2
  const chartH = H - PY * 2

  const dsrValues = data.map(d => d.dsr).filter((v): v is number => v !== null)
  const maxDsr = Math.max(...dsrValues, 1.3)
  const minDsr = Math.min(...dsrValues, 0.6)
  const range = maxDsr - minDsr || 0.5

  const taskValues = data.map(d => d.tasksCompleted)
  const maxTasks = Math.max(...taskValues, 5)

  const dsrPoints = data.map((d, i) => {
    if (d.dsr === null) return null
    const x = PX + (i / (data.length - 1)) * chartW
    const y = PY + (1 - (d.dsr - minDsr) / range) * chartH
    return `${x},${y}`
  }).filter(Boolean)

  const barW = chartW / data.length * 0.5

  // Y axis reference line at DSR = 1.0
  const refY = PY + (1 - (1.0 - minDsr) / range) * chartH

  return (
    <div className="trend-chart-container">
      <div className="trend-chart-legend">
        <span className="trend-chart-legend-item">
          <span className="trend-chart-legend-dot" style={{ background: '#0052cc' }} />
          DSR (оценка / факт)
        </span>
        <span className="trend-chart-legend-item">
          <span className="trend-chart-legend-dot" style={{ background: '#c1c7d0', borderRadius: 2 }} />
          Задач закрыто
        </span>
      </div>
      <svg className="trend-chart-svg" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet">
        {/* Reference line DSR=1.0 */}
        <line x1={PX} y1={refY} x2={W - PX} y2={refY} stroke="#ebecf0" strokeWidth="1" strokeDasharray="4 3" />
        <text x={PX - 4} y={refY + 4} textAnchor="end" fontSize="10" fill="#97a0af">1.0</text>

        {/* Task bars */}
        {data.map((d, i) => {
          const x = PX + (i / (data.length - 1)) * chartW - barW / 2
          const barH = (d.tasksCompleted / maxTasks) * chartH * 0.6
          const y = PY + chartH - barH
          return (
            <rect key={`bar-${i}`} x={x} y={y} width={barW} height={barH} rx="3" fill="#ebecf0" />
          )
        })}

        {/* DSR line */}
        <polyline
          points={dsrPoints.join(' ')}
          fill="none"
          stroke="#0052cc"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {/* DSR dots */}
        {data.map((d, i) => {
          if (d.dsr === null) return null
          const x = PX + (i / (data.length - 1)) * chartW
          const y = PY + (1 - (d.dsr - minDsr) / range) * chartH
          return (
            <circle key={`dot-${i}`} cx={x} cy={y} r="4" fill="#0052cc" stroke="white" strokeWidth="2" />
          )
        })}

        {/* X labels */}
        {data.map((d, i) => {
          const x = PX + (i / (data.length - 1)) * chartW
          return (
            <text key={`label-${i}`} x={x} y={H - 2} textAnchor="middle" fontSize="10" fill="#97a0af">
              {d.week}
            </text>
          )
        })}
      </svg>
    </div>
  )
}

// ======================== MAIN PAGE ========================

export function MemberProfilePage() {
  const { teamId, memberId } = useParams<{ teamId: string; memberId: string }>()
  const [data, setData] = useState<MemberProfileResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [from, setFrom] = useState(getDefaultFrom)
  const [to, setTo] = useState(getDefaultTo)

  const loadProfile = useCallback(async () => {
    if (!teamId || !memberId) return
    setLoading(true)
    setError(null)
    try {
      const result = await teamsApi.getMemberProfile(Number(teamId), Number(memberId), from, to)
      setData(result)
    } catch (e: any) {
      setError(e.response?.data?.error || e.message || 'Ошибка загрузки профиля')
    } finally {
      setLoading(false)
    }
  }, [teamId, memberId, from, to])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  if (loading) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: '#6b778c' }}>Загрузка профиля...</div>
      </main>
    )
  }

  if (error || !data) {
    return (
      <main className="main-content">
        <div className="page-header">
          <div className="page-header-left">
            <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
          </div>
        </div>
        <div style={{ padding: 40, textAlign: 'center', color: '#de350b' }}>{error || 'Нет данных'}</div>
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
          <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
        </div>
        <div className="member-profile-period">
          <label>Период:</label>
          <input type="date" value={from} onChange={e => setFrom(e.target.value)} />
          <span>—</span>
          <input type="date" value={to} onChange={e => setTo(e.target.value)} />
        </div>
      </div>

      {/* Profile Card */}
      <div className="member-profile-header">
        <div className="member-avatar">{initials}</div>
        <div className="member-info">
          <h2>{member.displayName}</h2>
          <div className="member-info-badges">
            <span className={`role-badge ${member.role.toLowerCase()}`}>{member.role}</span>
            <span className={`grade-badge ${member.grade.toLowerCase()}`}>{member.grade}</span>
            <span className="member-info-team">{member.teamName} &middot; {member.hoursPerDay}h/день</span>
          </div>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="member-summary-cards">
        <MetricCard
          title="Закрыто задач"
          value={summary.completedCount}
          subtitle="за период"
          trend={summary.completedCount > 0 ? 'up' : 'neutral'}
          tooltip="Количество задач со статусом Done за выбранный период"
        />
        <MetricCard
          title="Средний DSR"
          value={summary.avgDsr.toFixed(2)}
          subtitle={summary.avgDsr <= 1.0 ? 'Быстрее оценки' : 'Медленнее оценки'}
          trend={summary.avgDsr <= 1.0 ? 'up' : 'down'}
          tooltip="Delivery Speed Ratio = потрачено / оценено. Меньше 1.0 = делает быстрее, чем оценивает"
        />
        <MetricCard
          title="Cycle Time"
          value={`${summary.avgCycleTimeDays}d`}
          subtitle="среднее"
          trend="neutral"
          tooltip="Среднее время от начала работы (In Progress) до завершения (Done)"
        />
        <MetricCard
          title="Загрузка"
          value={`${summary.utilization}%`}
          subtitle="capacity"
          trend="neutral"
          tooltip="Процент использования эффективного рабочего времени за период"
        />
        <MetricCard
          title="Часы"
          value={`${summary.totalSpentH}/${summary.totalEstimateH}`}
          subtitle="факт / оценка"
          tooltip="Суммарные затраченные часы vs суммарная оценка за период"
        />
      </div>

      {/* Main Grid */}
      <div className="member-profile-grid">
        {/* ===== Completed Tasks ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Выполненные задачи</h3>
            <span className="section-badge">{completedTasks.length}</span>
          </div>
          {completedTasks.length === 0 ? (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              Нет выполненных задач за выбранный период
            </div>
          ) : (
            <table className="profile-tasks-table">
              <thead>
                <tr>
                  <th>Задача</th>
                  <th>Описание</th>
                  <th style={{ textAlign: 'right' }}>Оценка</th>
                  <th style={{ textAlign: 'right' }}>Факт</th>
                  <th style={{ textAlign: 'center' }}>DSR</th>
                  <th>Дата</th>
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
                  <td colSpan={2}>Итого</td>
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
            <h3>Тренд по неделям</h3>
          </div>
          {weeklyTrend.length > 0 ? (
            <>
              <TrendChart data={weeklyTrend} />
              <div className="profile-stats-row">
                <div className="profile-stat">
                  <div className="profile-stat-label">DSR тренд</div>
                  <div className={`profile-stat-value ${getDsrStatClass(summary.avgDsr)}`}>
                    {summary.avgDsr < 1 ? '\u2191' : '\u2193'} {summary.avgDsr.toFixed(2)}
                  </div>
                  <div className="profile-stat-hint">за период</div>
                </div>
                <div className="profile-stat">
                  <div className="profile-stat-label">Задач / неделю</div>
                  <div className="profile-stat-value">{avgTasksPerWeek}</div>
                  <div className="profile-stat-hint">среднее</div>
                </div>
                <div className="profile-stat">
                  <div className="profile-stat-label">Часов / неделю</div>
                  <div className="profile-stat-value">{avgHoursPerWeek}</div>
                  <div className="profile-stat-hint">залогировано</div>
                </div>
              </div>
            </>
          ) : (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              Нет данных для отображения тренда
            </div>
          )}
        </div>

        {/* ===== In Progress + Upcoming ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Текущие и предстоящие задачи</h3>
            <span className="section-badge">{activeTasks.length + upcomingTasks.length}</span>
          </div>
          {activeTasks.length === 0 && upcomingTasks.length === 0 ? (
            <div style={{ padding: 20, textAlign: 'center', color: '#6b778c', fontSize: 13 }}>
              Нет текущих и предстоящих задач
            </div>
          ) : (
            <table className="profile-tasks-table">
              <thead>
                <tr>
                  <th>Задача</th>
                  <th>Описание</th>
                  <th style={{ textAlign: 'center' }}>Статус</th>
                  <th style={{ textAlign: 'right' }}>Оценка</th>
                  <th style={{ textAlign: 'right' }}>Потрачено</th>
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
                      <span className="status-badge in-progress">{task.status}</span>
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
                      <span className="status-badge todo">{task.status}</span>
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
