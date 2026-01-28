import { TimeInStatusResponse } from '../../api/metrics'
import './TimeInStatusChart.css'

interface TimeInStatusChartProps {
  data: TimeInStatusResponse[]
}

export function TimeInStatusChart({ data }: TimeInStatusChartProps) {
  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Time in Status</h3>
        <div className="chart-empty">No status transition data available</div>
      </div>
    )
  }

  const maxHours = Math.max(...data.map(d => d.avgHours), 1)

  return (
    <div className="chart-section">
      <h3>Time in Status (Average Hours)</h3>
      <div className="time-status-chart">
        {data.map((item, i) => {
          const barWidth = (item.avgHours / maxHours) * 100
          return (
            <div key={i} className="time-status-row">
              <div className="time-status-label" title={item.status}>
                {item.status}
              </div>
              <div className="time-status-bar-container">
                <div
                  className="time-status-bar"
                  style={{ width: `${barWidth}%` }}
                >
                  <span className="time-status-value">
                    {item.avgHours.toFixed(1)}h
                  </span>
                </div>
              </div>
              <div className="time-status-count" title="Transitions count">
                {item.transitionsCount}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
