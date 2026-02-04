import { useState, useEffect } from 'react'
import { getVelocity, VelocityResponse } from '../../api/metrics'
import './VelocityChart.css'

interface VelocityChartProps {
  teamId: number
  from: string
  to: string
}

export function VelocityChart({ teamId, from, to }: VelocityChartProps) {
  const [data, setData] = useState<VelocityResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getVelocity(teamId, from, to)
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [teamId, from, to])

  if (loading) {
    return (
      <div className="velocity-section">
        <h3>Team Velocity</h3>
        <div className="velocity-loading">Loading velocity data...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="velocity-section">
        <h3>Team Velocity</h3>
        <div className="velocity-empty">Failed to load: {error}</div>
      </div>
    )
  }

  if (!data || data.byWeek.length === 0) {
    return (
      <div className="velocity-section">
        <h3>Team Velocity</h3>
        <div className="velocity-empty">No velocity data for this period.</div>
      </div>
    )
  }

  const maxValue = Math.max(
    ...data.byWeek.map(w => Math.max(w.capacityHours, w.loggedHours)),
    1
  )
  const chartHeight = 200

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  const getUtilizationColor = (percent: number) => {
    if (percent >= 85 && percent <= 110) return '#36B37E'
    if (percent >= 70 && percent <= 130) return '#FFAB00'
    return '#FF5630'
  }

  return (
    <div className="velocity-section">
      <h3>Team Velocity</h3>
      <p className="velocity-description">
        Logged hours vs team capacity. Shows how much of available time was actually spent on tasks.
      </p>

      {/* Summary Cards */}
      <div className="velocity-summary">
        <div className="velocity-summary-card">
          <div className="velocity-summary-value">{data.totalCapacityHours.toFixed(0)}h</div>
          <div className="velocity-summary-label">Total Capacity</div>
        </div>
        <div className="velocity-summary-card">
          <div className="velocity-summary-value">{data.totalLoggedHours.toFixed(0)}h</div>
          <div className="velocity-summary-label">Total Logged</div>
        </div>
        <div className="velocity-summary-card">
          <div
            className="velocity-summary-value"
            style={{ color: getUtilizationColor(data.utilizationPercent) }}
          >
            {data.utilizationPercent.toFixed(0)}%
          </div>
          <div className="velocity-summary-label">Utilization</div>
        </div>
      </div>

      {/* Chart */}
      <div className="velocity-chart">
        <div className="velocity-y-axis">
          {[...Array(5)].map((_, i) => {
            const value = Math.round((maxValue / 4) * (4 - i))
            return <div key={i} className="velocity-y-label">{value}h</div>
          })}
        </div>
        <div className="velocity-chart-area">
          <div className="velocity-grid">
            {[...Array(5)].map((_, i) => (
              <div key={i} className="velocity-grid-line" />
            ))}
          </div>
          <div className="velocity-bars">
            {data.byWeek.map((week, i) => {
              const capacityHeight = (week.capacityHours / maxValue) * chartHeight
              const loggedHeight = (week.loggedHours / maxValue) * chartHeight

              return (
                <div
                  key={i}
                  className="velocity-bar-group"
                  title={`Week of ${formatDate(week.weekStart)}\nCapacity: ${week.capacityHours.toFixed(1)}h\nLogged: ${week.loggedHours.toFixed(1)}h\nUtilization: ${week.utilizationPercent.toFixed(0)}%`}
                >
                  <div className="velocity-bar-wrapper">
                    <div
                      className="velocity-bar velocity-bar-capacity"
                      style={{ height: `${capacityHeight}px` }}
                    />
                    <div
                      className="velocity-bar velocity-bar-logged"
                      style={{ height: `${loggedHeight}px` }}
                    />
                  </div>
                  <div className="velocity-bar-label">{formatDate(week.weekStart)}</div>
                </div>
              )
            })}
          </div>
        </div>
      </div>

      <div className="velocity-legend">
        <span className="velocity-legend-item velocity-legend-capacity">Capacity</span>
        <span className="velocity-legend-item velocity-legend-logged">Logged</span>
      </div>
    </div>
  )
}
