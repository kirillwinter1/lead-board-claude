import { WeeklyTrend } from '../../api/teams'

// Extracted from MemberProfilePage — CSS classes (trend-chart-*) live in MemberProfilePage.css.
// Consuming pages must import that stylesheet (or a copy of the relevant rules).
export function TrendChart({ data }: { data: WeeklyTrend[] }) {
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
