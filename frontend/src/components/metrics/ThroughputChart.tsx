import { PeriodThroughput } from '../../api/metrics'
import './ThroughputChart.css'

interface ThroughputChartProps {
  data: PeriodThroughput[]
  movingAverage?: number[]
}

export function ThroughputChart({ data, movingAverage }: ThroughputChartProps) {
  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Throughput by Period</h3>
        <div className="chart-empty">No data available for this period</div>
      </div>
    )
  }

  const maxValue = Math.max(
    ...data.map(d => d.total),
    ...(movingAverage || []),
    1
  )
  const chartHeight = 200 // pixels for bar area

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  // Generate SVG path for moving average line
  const generateMaPath = () => {
    if (!movingAverage || movingAverage.length === 0) return null

    const barWidth = 100 / data.length // percentage width per bar
    const points = movingAverage.map((value, i) => {
      const x = barWidth * i + barWidth / 2 // center of each bar
      const y = 100 - (value / maxValue) * 100
      return `${x},${y}`
    })

    return `M ${points.join(' L ')}`
  }

  const maPath = generateMaPath()

  return (
    <div className="chart-section">
      <h3>Throughput by Period</h3>
      <div className="throughput-chart">
        <div className="chart-y-axis">
          {[...Array(5)].map((_, i) => {
            const value = Math.round((maxValue / 4) * (4 - i))
            return <div key={i} className="chart-y-label">{value}</div>
          })}
        </div>
        <div className="chart-area">
          <div className="chart-grid">
            {[...Array(5)].map((_, i) => (
              <div key={i} className="chart-grid-line" />
            ))}
          </div>
          <div className="chart-bars">
            {data.map((period, i) => {
              const epicHeight = (period.epics / maxValue) * chartHeight
              const storyHeight = (period.stories / maxValue) * chartHeight
              const subtaskHeight = (period.subtasks / maxValue) * chartHeight
              const totalHeight = epicHeight + storyHeight + subtaskHeight

              return (
                <div
                  key={i}
                  className="bar-group"
                  title={`${formatDate(period.periodStart)}\nEpics: ${period.epics}\nStories: ${period.stories}\nSubtasks: ${period.subtasks}\nTotal: ${period.total}${movingAverage ? `\nMA: ${movingAverage[i]?.toFixed(1)}` : ''}`}
                >
                  <div className="stacked-bar" style={{ height: `${totalHeight}px` }}>
                    {epicHeight > 0 && (
                      <div
                        className="bar bar-epic"
                        style={{ height: `${epicHeight}px` }}
                      />
                    )}
                    {storyHeight > 0 && (
                      <div
                        className="bar bar-story"
                        style={{ height: `${storyHeight}px` }}
                      />
                    )}
                    {subtaskHeight > 0 && (
                      <div
                        className="bar bar-subtask"
                        style={{ height: `${subtaskHeight}px` }}
                      />
                    )}
                  </div>
                  <div className="bar-label">{formatDate(period.periodStart)}</div>
                </div>
              )
            })}
          </div>

          {/* Moving Average Line */}
          {maPath && (
            <svg className="ma-line-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
              <path
                d={maPath}
                fill="none"
                stroke="#ff5630"
                strokeWidth="2"
                vectorEffect="non-scaling-stroke"
              />
            </svg>
          )}
        </div>
      </div>
      <div className="chart-legend">
        <span className="legend-item legend-epic">Epics</span>
        <span className="legend-item legend-story">Stories</span>
        <span className="legend-item legend-subtask">Subtasks</span>
        {movingAverage && movingAverage.length > 0 && (
          <span className="legend-item legend-ma">4-Week MA</span>
        )}
      </div>
    </div>
  )
}
