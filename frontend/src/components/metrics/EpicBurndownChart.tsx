import { useState, useEffect, useMemo } from 'react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts'
import {
  getEpicBurndown,
  getEpicsForBurndown,
  EpicBurndownResponse,
  EpicInfo
} from '../../api/metrics'
import './EpicBurndownChart.css'

interface EpicBurndownChartProps {
  teamId: number
}

export function EpicBurndownChart({ teamId }: EpicBurndownChartProps) {
  const [epics, setEpics] = useState<EpicInfo[]>([])
  const [selectedEpicKey, setSelectedEpicKey] = useState<string>('')
  const [burndownData, setBurndownData] = useState<EpicBurndownResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [epicsLoading, setEpicsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Load epics list
  useEffect(() => {
    setEpicsLoading(true)
    getEpicsForBurndown(teamId)
      .then(data => {
        setEpics(data)
        const activeEpic = data.find(e => !e.completed) || data[0]
        if (activeEpic) {
          setSelectedEpicKey(activeEpic.key)
        }
      })
      .catch(err => setError(err.message))
      .finally(() => setEpicsLoading(false))
  }, [teamId])

  // Load burndown data when epic selected
  useEffect(() => {
    if (!selectedEpicKey) {
      setBurndownData(null)
      return
    }

    setLoading(true)
    setError(null)
    getEpicBurndown(selectedEpicKey)
      .then(setBurndownData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [selectedEpicKey])

  const chartData = useMemo(() => {
    if (!burndownData || burndownData.idealLine.length === 0) return null

    // Create a map of actual values by date
    const actualMap = new Map(
      burndownData.actualLine.map(p => [p.date, p.remainingHours])
    )

    // Merge ideal and actual into single dataset
    return burndownData.idealLine.map(point => ({
      date: new Date(point.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      fullDate: point.date,
      ideal: point.remainingHours,
      actual: actualMap.get(point.date) ?? null
    }))
  }, [burndownData])

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  if (epicsLoading) {
    return (
      <div className="burndown-section">
        <h3>Epic Burndown</h3>
        <div className="burndown-loading">Loading epics...</div>
      </div>
    )
  }

  if (epics.length === 0) {
    return (
      <div className="burndown-section">
        <h3>Epic Burndown</h3>
        <div className="burndown-empty">No epics found for this team.</div>
      </div>
    )
  }

  return (
    <div className="burndown-section">
      <h3>Epic Burndown</h3>
      <p className="burndown-description">
        Track progress of an epic. Ideal line shows linear completion, actual line shows real progress.
      </p>

      {/* Epic Selector */}
      <div className="burndown-selector">
        <select
          value={selectedEpicKey}
          onChange={e => setSelectedEpicKey(e.target.value)}
        >
          <option value="" disabled>Select an epic...</option>
          {epics.map(epic => (
            <option key={epic.key} value={epic.key}>
              {epic.key} - {epic.summary.length > 50 ? epic.summary.substring(0, 50) + '...' : epic.summary}
              {epic.completed ? ' (Done)' : ''}
            </option>
          ))}
        </select>
      </div>

      {loading && <div className="burndown-loading">Loading burndown data...</div>}

      {error && <div className="burndown-empty">Error: {error}</div>}

      {!loading && !error && burndownData && (
        <>
          {/* Epic Info */}
          <div className="burndown-epic-info">
            <div className="burndown-epic-info-item">
              <span className="burndown-epic-info-label">Total Estimate:</span>
              <span className="burndown-epic-info-value">{burndownData.totalEstimateHours}h</span>
            </div>
            {burndownData.startDate && (
              <div className="burndown-epic-info-item">
                <span className="burndown-epic-info-label">Start:</span>
                <span className="burndown-epic-info-value">{formatDate(burndownData.startDate)}</span>
              </div>
            )}
            {burndownData.endDate && (
              <div className="burndown-epic-info-item">
                <span className="burndown-epic-info-label">End:</span>
                <span className="burndown-epic-info-value">{formatDate(burndownData.endDate)}</span>
              </div>
            )}
          </div>

          {chartData && chartData.length > 0 ? (
            <div className="burndown-chart-container">
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData} margin={{ top: 20, right: 20, left: 0, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#ebecf0" />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11, fill: '#6b778c' }}
                    tickLine={false}
                    axisLine={{ stroke: '#dfe1e6' }}
                    interval="preserveStartEnd"
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
                    formatter={(value) => [`${value}h`]}
                  />
                  <Legend wrapperStyle={{ fontSize: 12, paddingTop: 10 }} />
                  <Line
                    type="monotone"
                    dataKey="ideal"
                    stroke="#97a0af"
                    strokeWidth={2}
                    strokeDasharray="5 5"
                    dot={false}
                    name="Ideal"
                  />
                  <Line
                    type="monotone"
                    dataKey="actual"
                    stroke="#0065ff"
                    strokeWidth={2}
                    dot={false}
                    name="Actual"
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="burndown-empty">
              No burndown data available for this epic.
              <br />
              <small style={{ color: '#6b778c' }}>
                Burndown requires subtasks with estimates and time logged.
              </small>
            </div>
          )}
        </>
      )}
    </div>
  )
}
