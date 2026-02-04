import { useState, useEffect, useMemo } from 'react'
import {
  getEpicBurndown,
  getEpicsForBurndown,
  EpicBurndownResponse,
  EpicInfo,
  BurndownPoint
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
        // Select first non-completed epic, or first epic
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

    const maxHours = burndownData.totalEstimateHours ||
      Math.max(
        ...burndownData.idealLine.map(p => p.remainingHours),
        ...burndownData.actualLine.map(p => p.remainingHours)
      )

    return {
      maxHours: Math.ceil(maxHours / 10) * 10 || 10,
      gridLines: 5
    }
  }, [burndownData])

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  const generatePath = (points: BurndownPoint[], maxHours: number): string => {
    if (points.length === 0) return ''

    const width = 100
    const height = 100

    return points.map((point, i) => {
      const x = (i / (points.length - 1)) * width
      const y = height - (point.remainingHours / maxHours) * height
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)},${y.toFixed(2)}`
    }).join(' ')
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

          {chartData && burndownData.idealLine.length > 0 ? (
            <>
              {/* Chart */}
              <div className="burndown-chart-container">
                <div className="burndown-chart">
                  {/* Y-axis */}
                  <div className="burndown-y-axis">
                    {[...Array(chartData.gridLines + 1)].map((_, i) => {
                      const value = Math.round((chartData.maxHours / chartData.gridLines) * (chartData.gridLines - i))
                      return <div key={i} className="burndown-y-label">{value}h</div>
                    })}
                  </div>

                  {/* Chart Area */}
                  <div className="burndown-chart-area">
                    {/* Grid lines */}
                    <div className="burndown-grid">
                      {[...Array(chartData.gridLines + 1)].map((_, i) => (
                        <div
                          key={i}
                          className="burndown-grid-line-h"
                          style={{ top: `${(i / chartData.gridLines) * 100}%` }}
                        />
                      ))}
                    </div>

                    {/* SVG Lines */}
                    <svg className="burndown-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
                      {/* Ideal line */}
                      <path
                        className="burndown-line-ideal"
                        d={generatePath(burndownData.idealLine, chartData.maxHours)}
                        vectorEffect="non-scaling-stroke"
                      />
                      {/* Actual line */}
                      <path
                        className="burndown-line-actual"
                        d={generatePath(burndownData.actualLine, chartData.maxHours)}
                        vectorEffect="non-scaling-stroke"
                      />
                    </svg>
                  </div>
                </div>

                {/* X-axis labels */}
                <div className="burndown-x-axis">
                  {burndownData.idealLine.length > 0 && (
                    <>
                      <span className="burndown-x-label">
                        {formatDate(burndownData.idealLine[0].date)}
                      </span>
                      <span className="burndown-x-label">
                        {formatDate(burndownData.idealLine[burndownData.idealLine.length - 1].date)}
                      </span>
                    </>
                  )}
                </div>
              </div>

              {/* Legend */}
              <div className="burndown-legend">
                <div className="burndown-legend-item">
                  <span className="burndown-legend-line burndown-legend-line-ideal" />
                  Ideal (linear)
                </div>
                <div className="burndown-legend-item">
                  <span className="burndown-legend-line burndown-legend-line-actual" />
                  Actual
                </div>
              </div>
            </>
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
