import { Link, useParams } from 'react-router-dom'
import { MetricCard } from '../components/metrics/MetricCard'
import './TeamsPage.css'
import './MemberProfilePage.css'

// ======================== MOCK DATA ========================

const MOCK_MEMBER = {
  id: 1,
  displayName: 'Алексей Петров',
  role: 'DEV' as const,
  grade: 'SENIOR' as const,
  hoursPerDay: 6,
  teamName: 'Platform Team',
  teamId: 1,
}

const MOCK_COMPLETED_TASKS = [
  { key: 'PROJ-142', summary: 'Реализовать кэширование ответов API', epic: 'PROJ-50 Оптимизация производительности', estimateH: 16, spentH: 14, dsr: 0.88, doneDate: '2026-02-07' },
  { key: 'PROJ-138', summary: 'Исправить N+1 запросы в сервисе отчётов', epic: 'PROJ-50 Оптимизация производительности', estimateH: 8, spentH: 6, dsr: 0.75, doneDate: '2026-02-05' },
  { key: 'PROJ-131', summary: 'Добавить индексы на таблицу orders', epic: 'PROJ-50 Оптимизация производительности', estimateH: 4, spentH: 5, dsr: 1.25, doneDate: '2026-02-04' },
  { key: 'PROJ-125', summary: 'Миграция на Spring Boot 3.2', epic: 'PROJ-45 Техдолг Q1', estimateH: 24, spentH: 20, dsr: 0.83, doneDate: '2026-02-03' },
  { key: 'PROJ-119', summary: 'Рефакторинг модуля аутентификации', epic: 'PROJ-45 Техдолг Q1', estimateH: 16, spentH: 18, dsr: 1.13, doneDate: '2026-01-31' },
  { key: 'PROJ-112', summary: 'Написать интеграционные тесты для Payments API', epic: 'PROJ-40 Платежи v2', estimateH: 12, spentH: 10, dsr: 0.83, doneDate: '2026-01-29' },
  { key: 'PROJ-108', summary: 'Реализовать webhook обработку платежей', epic: 'PROJ-40 Платежи v2', estimateH: 20, spentH: 22, dsr: 1.10, doneDate: '2026-01-27' },
]

const MOCK_INPROGRESS_TASKS = [
  { key: 'PROJ-155', summary: 'Внедрить rate limiting для API', epic: 'PROJ-55 Безопасность', estimateH: 12, spentH: 4, status: 'In Progress' as const, forecastDate: '2026-02-14' },
  { key: 'PROJ-158', summary: 'Настроить мониторинг Prometheus + Grafana', epic: 'PROJ-55 Безопасность', estimateH: 8, spentH: 0, status: 'To Do' as const, forecastDate: '2026-02-18' },
]

const MOCK_UPCOMING_TASKS = [
  { key: 'PROJ-165', summary: 'Реализовать graceful shutdown для воркеров', epic: 'PROJ-60 Инфраструктура', estimateH: 6, status: 'To Do' as const, forecastDate: '2026-02-20' },
  { key: 'PROJ-170', summary: 'Добавить circuit breaker для внешних API', epic: 'PROJ-60 Инфраструктура', estimateH: 10, status: 'To Do' as const, forecastDate: '2026-02-25' },
  { key: 'PROJ-175', summary: 'Оптимизация Docker образов (multi-stage)', epic: 'PROJ-60 Инфраструктура', estimateH: 8, status: 'To Do' as const, forecastDate: '2026-02-28' },
]

// Weekly trend data (last 8 weeks)
const MOCK_WEEKLY_TREND = [
  { week: '30 дек', dsr: 1.15, tasks: 3, hours: 28 },
  { week: '6 янв', dsr: 0.95, tasks: 4, hours: 30 },
  { week: '13 янв', dsr: 1.05, tasks: 3, hours: 26 },
  { week: '20 янв', dsr: 0.88, tasks: 5, hours: 32 },
  { week: '27 янв', dsr: 0.92, tasks: 4, hours: 29 },
  { week: '3 фев', dsr: 0.83, tasks: 4, hours: 31 },
  { week: '10 фев', dsr: 0.87, tasks: 3, hours: 24 },
  { week: '17 фев', dsr: null, tasks: 1, hours: 10 },
]

// Workload for next 5 working days
const MOCK_WORKLOAD = [
  { date: 'Пн 10 фев', hours: 5.5, capacity: 4.8 },
  { date: 'Вт 11 фев', hours: 4.8, capacity: 4.8 },
  { date: 'Ср 12 фев', hours: 3.2, capacity: 4.8 },
  { date: 'Чт 13 фев', hours: 4.0, capacity: 4.8 },
  { date: 'Пт 14 фев', hours: 2.5, capacity: 4.8 },
]

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

function getWorkloadClass(hours: number, capacity: number): string {
  const pct = hours / capacity
  if (pct <= 0.85) return 'light'
  if (pct <= 1.0) return 'medium'
  return 'heavy'
}

function formatHours(h: number): string {
  return h % 1 === 0 ? `${h}h` : `${h.toFixed(1)}h`
}

// ======================== TREND CHART ========================

function TrendChart({ data }: { data: typeof MOCK_WEEKLY_TREND }) {
  const W = 500, H = 160, PX = 40, PY = 20
  const chartW = W - PX * 2
  const chartH = H - PY * 2

  const dsrValues = data.map(d => d.dsr).filter((v): v is number => v !== null)
  const maxDsr = Math.max(...dsrValues, 1.3)
  const minDsr = Math.min(...dsrValues, 0.6)
  const range = maxDsr - minDsr || 0.5

  const taskValues = data.map(d => d.tasks)
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
          const barH = (d.tasks / maxTasks) * chartH * 0.6
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
  const { teamId } = useParams<{ teamId: string; memberId: string }>()
  const member = MOCK_MEMBER

  // Computed totals
  const totalEstimate = MOCK_COMPLETED_TASKS.reduce((s, t) => s + t.estimateH, 0)
  const totalSpent = MOCK_COMPLETED_TASKS.reduce((s, t) => s + t.spentH, 0)
  const avgDsr = totalSpent / totalEstimate
  const avgCycleTime = 3.2 // mock
  const utilization = 87 // mock %
  const initials = member.displayName.split(' ').map(w => w[0]).join('')

  return (
    <main className="main-content">
      {/* Header */}
      <div className="page-header" style={{ marginBottom: 12 }}>
        <div className="page-header-left">
          <Link to={`/board/teams/${teamId}`} className="back-link">&larr; Назад к команде</Link>
        </div>
        <div className="member-profile-period">
          <label>Период:</label>
          <input type="date" defaultValue="2026-01-15" />
          <span>—</span>
          <input type="date" defaultValue="2026-02-11" />
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
            <span className="member-info-team">{member.teamName} &middot; {member.hoursPerDay}h/день &middot; eff. 4.8h/день</span>
          </div>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="member-summary-cards">
        <MetricCard
          title="Закрыто задач"
          value={MOCK_COMPLETED_TASKS.length}
          subtitle="за период"
          trend="up"
          tooltip="Количество задач со статусом Done за выбранный период"
        />
        <MetricCard
          title="Средний DSR"
          value={avgDsr.toFixed(2)}
          subtitle={avgDsr <= 1.0 ? 'Быстрее оценки' : 'Медленнее оценки'}
          trend={avgDsr <= 1.0 ? 'up' : 'down'}
          tooltip="Delivery Speed Ratio = потрачено / оценено. Меньше 1.0 = делает быстрее, чем оценивает"
        />
        <MetricCard
          title="Cycle Time"
          value={`${avgCycleTime}d`}
          subtitle="среднее"
          trend="up"
          tooltip="Среднее время от начала работы (In Progress) до завершения (Done)"
        />
        <MetricCard
          title="Загрузка"
          value={`${utilization}%`}
          subtitle="capacity"
          trend="neutral"
          tooltip="Процент использования эффективного рабочего времени за период"
        />
        <MetricCard
          title="Часы"
          value={`${totalSpent}/${totalEstimate}`}
          subtitle="факт / оценка"
          tooltip="Суммарные затраченные часы vs суммарная оценка за период"
        />
      </div>

      {/* Main Grid */}
      <div className="member-profile-grid">
        {/* ===== LEFT: Completed Tasks ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Выполненные задачи</h3>
            <span className="section-badge">{MOCK_COMPLETED_TASKS.length}</span>
          </div>
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
              {MOCK_COMPLETED_TASKS.map(task => (
                <tr key={task.key}>
                  <td><span className="task-key">{task.key}</span></td>
                  <td className="task-summary-cell">
                    {task.summary}
                    <span className="task-epic-label">{task.epic}</span>
                  </td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                  <td style={{ textAlign: 'right' }}>
                    <span className={`task-hours ${task.spentH > task.estimateH ? 'over' : 'under'}`}>
                      {formatHours(task.spentH)}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span className={`dsr-badge ${getDsrClass(task.dsr)}`}>
                      {task.dsr.toFixed(2)}
                    </span>
                  </td>
                  <td style={{ color: '#6b778c', fontSize: 13 }}>{task.doneDate}</td>
                </tr>
              ))}
              {/* Totals row */}
              <tr className="tasks-totals-row">
                <td colSpan={2}>Итого</td>
                <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(totalEstimate)}</td>
                <td style={{ textAlign: 'right' }}>
                  <span className={`task-hours ${totalSpent > totalEstimate ? 'over' : 'under'}`}>
                    {formatHours(totalSpent)}
                  </span>
                </td>
                <td style={{ textAlign: 'center' }}>
                  <span className={`dsr-badge ${getDsrClass(avgDsr)}`}>
                    {avgDsr.toFixed(2)}
                  </span>
                </td>
                <td />
              </tr>
            </tbody>
          </table>
        </div>

        {/* ===== LEFT: Trend Chart ===== */}
        <div className="profile-section">
          <div className="profile-section-header">
            <h3>Тренд по неделям</h3>
          </div>
          <TrendChart data={MOCK_WEEKLY_TREND} />
          <div className="profile-stats-row">
            <div className="profile-stat">
              <div className="profile-stat-label">DSR тренд</div>
              <div className={`profile-stat-value ${getDsrStatClass(avgDsr)}`}>
                {avgDsr < 1 ? '↑' : '↓'} {avgDsr.toFixed(2)}
              </div>
              <div className="profile-stat-hint">за 4 недели</div>
            </div>
            <div className="profile-stat">
              <div className="profile-stat-label">Задач / неделю</div>
              <div className="profile-stat-value">3.7</div>
              <div className="profile-stat-hint">среднее</div>
            </div>
            <div className="profile-stat">
              <div className="profile-stat-label">Часов / неделю</div>
              <div className="profile-stat-value">28.6</div>
              <div className="profile-stat-hint">залогировано</div>
            </div>
          </div>
        </div>

        {/* ===== RIGHT: Workload ===== */}
        <div className="profile-section">
          <div className="profile-section-header">
            <h3>Загрузка на неделю</h3>
            <span className="section-badge">4.8h/день eff.</span>
          </div>
          <div className="workload-bar-container">
            {MOCK_WORKLOAD.map((day, i) => {
              const pct = Math.min((day.hours / day.capacity) * 100, 120)
              return (
                <div className="workload-day" key={i}>
                  <span className="workload-day-label">{day.date}</span>
                  <div className="workload-bar-bg">
                    <div
                      className={`workload-bar-fill ${getWorkloadClass(day.hours, day.capacity)}`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                  <span className="workload-day-hours">
                    {day.hours.toFixed(1)}/{day.capacity}
                  </span>
                </div>
              )
            })}
          </div>
          <div className="profile-stats-row">
            <div className="profile-stat">
              <div className="profile-stat-label">Запланировано</div>
              <div className="profile-stat-value">20h</div>
              <div className="profile-stat-hint">на 5 дней</div>
            </div>
            <div className="profile-stat">
              <div className="profile-stat-label">Capacity</div>
              <div className="profile-stat-value">24h</div>
              <div className="profile-stat-hint">eff. capacity</div>
            </div>
            <div className="profile-stat">
              <div className="profile-stat-label">Свободно</div>
              <div className="profile-stat-value good">4h</div>
              <div className="profile-stat-hint">можно назначить</div>
            </div>
          </div>
        </div>

        {/* ===== In Progress + Upcoming ===== */}
        <div className="profile-section full-width">
          <div className="profile-section-header">
            <h3>Текущие и предстоящие задачи</h3>
            <span className="section-badge">{MOCK_INPROGRESS_TASKS.length + MOCK_UPCOMING_TASKS.length}</span>
          </div>
          <table className="profile-tasks-table">
            <thead>
              <tr>
                <th>Задача</th>
                <th>Описание</th>
                <th style={{ textAlign: 'center' }}>Статус</th>
                <th style={{ textAlign: 'right' }}>Оценка</th>
                <th style={{ textAlign: 'right' }}>Потрачено</th>
                <th>Прогноз</th>
              </tr>
            </thead>
            <tbody>
              {MOCK_INPROGRESS_TASKS.map(task => (
                <tr key={task.key}>
                  <td><span className="task-key">{task.key}</span></td>
                  <td className="task-summary-cell">
                    {task.summary}
                    <span className="task-epic-label">{task.epic}</span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span className={`status-badge ${task.status === 'In Progress' ? 'in-progress' : 'todo'}`}>
                      {task.status}
                    </span>
                  </td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.spentH)}</td>
                  <td style={{ color: '#6b778c', fontSize: 13 }}>{task.forecastDate}</td>
                </tr>
              ))}
              {MOCK_UPCOMING_TASKS.map(task => (
                <tr key={task.key} style={{ opacity: 0.7 }}>
                  <td><span className="task-key">{task.key}</span></td>
                  <td className="task-summary-cell">
                    {task.summary}
                    <span className="task-epic-label">{task.epic}</span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span className="status-badge todo">{task.status}</span>
                  </td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>{formatHours(task.estimateH)}</td>
                  <td className="task-hours" style={{ textAlign: 'right' }}>—</td>
                  <td style={{ color: '#6b778c', fontSize: 13 }}>{task.forecastDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </main>
  )
}
