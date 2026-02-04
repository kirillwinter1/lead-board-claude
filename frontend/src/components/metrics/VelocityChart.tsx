import { useState, useEffect } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Cell
} from 'recharts'
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

  const getUtilizationColor = (percent: number) => {
    if (percent >= 85 && percent <= 110) return '#36B37E'
    if (percent >= 70 && percent <= 130) return '#FFAB00'
    return '#FF5630'
  }

  const chartData = data.byWeek.map(week => ({
    name: new Date(week.weekStart).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    capacity: week.capacityHours,
    logged: week.loggedHours,
    utilization: week.utilizationPercent
  }))

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
      <div className="velocity-chart-container">
        <ResponsiveContainer width="100%" height={250}>
          <BarChart data={chartData} margin={{ top: 20, right: 20, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#ebecf0" />
            <XAxis
              dataKey="name"
              tick={{ fontSize: 11, fill: '#6b778c' }}
              tickLine={false}
              axisLine={{ stroke: '#dfe1e6' }}
            />
            <YAxis
              tick={{ fontSize: 11, fill: '#6b778c' }}
              tickLine={false}
              axisLine={{ stroke: '#dfe1e6' }}
              tickFormatter={(value) => `${value}h`}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#172b4d',
                border: 'none',
                borderRadius: 4,
                color: 'white',
                fontSize: 12
              }}
              labelStyle={{ color: 'white', fontWeight: 600 }}
              formatter={(value, name) => [
                `${Number(value).toFixed(1)}h`,
                name === 'capacity' ? 'Capacity' : 'Logged'
              ]}
            />
            <Legend wrapperStyle={{ fontSize: 12, paddingTop: 10 }} />
            <Bar dataKey="capacity" fill="#dfe1e6" name="Capacity" radius={[4, 4, 0, 0]} />
            <Bar dataKey="logged" name="Logged" radius={[4, 4, 0, 0]}>
              {chartData.map((entry, index) => (
                <Cell
                  key={`cell-${index}`}
                  fill={getUtilizationColor(entry.utilization)}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
