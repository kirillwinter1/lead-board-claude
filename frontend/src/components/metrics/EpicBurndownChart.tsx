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
import { SingleSelectDropdown } from '../SingleSelectDropdown'
import { CHART_GRID, CHART_AXIS, CHART_TICK, CHART_TOOLTIP_BG } from '../../constants/colors'
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

    const actualMap = new Map(
      burndownData.actualLine.map(p => [p.date, p.remaining])
    )

    return burndownData.idealLine.map(point => ({
      date: new Date(point.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      fullDate: point.date,
      ideal: point.remaining,
      actual: actualMap.get(point.date) ?? null
    }))
  }, [burndownData])

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
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
        <div className="burndown-empty">No in-progress or completed epics found for this team.</div>
      </div>
    )
  }

  return (
    <div className="burndown-section">
      <h3>Epic Burndown</h3>
      <p className="burndown-description">
        Remaining work in person-days. Dashed line — plan, solid — actual.
      </p>

      {/* Epic Selector */}
      <div className="burndown-selector">
        <SingleSelectDropdown
          label="Epic"
          options={epics.map(epic => ({
            value: epic.key,
            label: `${epic.key} - ${epic.summary.length > 50 ? epic.summary.substring(0, 50) + '...' : epic.summary}${epic.completed ? ' (Done)' : ''}`,
          }))}
          selected={selectedEpicKey || null}
          onChange={v => v && setSelectedEpicKey(v)}
          placeholder="Select an epic..."
          allowClear={false}
        />
      </div>

      {loading && <div className="burndown-loading">Loading burndown data...</div>}

      {error && <div className="burndown-empty">Error: {error}</div>}

      {!loading && !error && burndownData && (
        <>
          {/* Epic Info */}
          <div className="burndown-epic-info">
            <div className="burndown-epic-info-item">
              <span className="burndown-epic-info-label">Stories:</span>
              <span className="burndown-epic-info-value">{burndownData.totalStories}</span>
            </div>
            <div className="burndown-epic-info-item">
              <span className="burndown-epic-info-label">
                {burndownData.planEstimateDays != null && burndownData.planEstimateDays !== burndownData.totalEstimateDays
                  ? 'Current:'
                  : 'Estimate:'}
              </span>
              <span className="burndown-epic-info-value">{burndownData.totalEstimateDays} p-days</span>
            </div>
            {burndownData.planEstimateDays != null && burndownData.planEstimateDays !== burndownData.totalEstimateDays && (
              <div className="burndown-epic-info-item">
                <span className="burndown-epic-info-label">Plan:</span>
                <span className="burndown-epic-info-value">{burndownData.planEstimateDays} p-days</span>
              </div>
            )}
            {burndownData.startDate && (
              <div className="burndown-epic-info-item">
                <span className="burndown-epic-info-label">Start:</span>
                <span className="burndown-epic-info-value">{formatDate(burndownData.startDate)}</span>
              </div>
            )}
            {burndownData.endDate && (
              <div className="burndown-epic-info-item">
                <span className="burndown-epic-info-label">{epics.find(e => e.key === selectedEpicKey)?.completed ? 'End:' : 'Today:'}</span>
                <span className="burndown-epic-info-value">{formatDate(burndownData.endDate)}</span>
              </div>
            )}
          </div>

          {chartData && chartData.length > 0 ? (
            <div className="burndown-chart-container">
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData} margin={{ top: 20, right: 20, left: 0, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11, fill: CHART_TICK }}
                    tickLine={false}
                    axisLine={{ stroke: CHART_AXIS }}
                    interval="preserveStartEnd"
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: CHART_TICK }}
                    tickLine={false}
                    axisLine={{ stroke: CHART_AXIS }}
                    allowDecimals={false}
                    label={{ value: 'p-days', angle: -90, position: 'insideLeft', style: { fontSize: 11, fill: CHART_TICK } }}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: CHART_TOOLTIP_BG,
                      border: 'none',
                      borderRadius: 4,
                      color: 'white',
                      fontSize: 12
                    }}
                    labelStyle={{ color: 'white', fontWeight: 600 }}
                    formatter={(value) => [`${value} p-days`]}
                  />
                  <Legend wrapperStyle={{ fontSize: 12, paddingTop: 10 }} />
                  <Line
                    type={burndownData.planEstimateDays != null ? 'stepAfter' : 'monotone'}
                    dataKey="ideal"
                    stroke="#97a0af"
                    strokeWidth={2}
                    strokeDasharray="5 5"
                    dot={false}
                    name="Plan"
                  />
                  <Line
                    type="monotone"
                    dataKey="actual"
                    stroke="#0065ff"
                    strokeWidth={3}
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
              <small style={{ color: CHART_TICK }}>
                Burndown requires stories with estimates assigned to this epic.
              </small>
            </div>
          )}
        </>
      )}
    </div>
  )
}
