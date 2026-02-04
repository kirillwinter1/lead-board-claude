import { useState, useMemo } from 'react'
import { ForecastAccuracyResponse, EpicAccuracy } from '../../api/metrics'
import './ForecastScatterPlot.css'

interface ForecastScatterPlotProps {
  data: ForecastAccuracyResponse
  jiraBaseUrl?: string
}

interface TooltipState {
  visible: boolean
  x: number
  y: number
  epic: EpicAccuracy | null
}

export function ForecastScatterPlot({ data, jiraBaseUrl = '' }: ForecastScatterPlotProps) {
  const [tooltip, setTooltip] = useState<TooltipState>({
    visible: false,
    x: 0,
    y: 0,
    epic: null
  })

  const chartData = useMemo(() => {
    if (!data.epics || data.epics.length === 0) return null

    const epics = data.epics.filter(e => e.plannedDays > 0 && e.actualDays > 0)
    if (epics.length === 0) return null

    const maxPlan = Math.max(...epics.map(e => e.plannedDays))
    const maxActual = Math.max(...epics.map(e => e.actualDays))
    const maxValue = Math.max(maxPlan, maxActual)

    // Round up to nice number
    const niceMax = Math.ceil(maxValue / 5) * 5

    return {
      epics,
      maxValue: niceMax,
      gridLines: 5
    }
  }, [data])

  if (!chartData) {
    return (
      <div className="scatter-plot-section">
        <h3>Plan vs Fact (Scatter)</h3>
        <div className="scatter-empty">
          No data for scatter plot. Need completed epics with both planned and actual days.
        </div>
      </div>
    )
  }

  const getPointColor = (epic: EpicAccuracy): string => {
    const diff = Math.abs(epic.actualDays - epic.plannedDays)
    if (diff <= 2) return 'scatter-point-green'
    if (diff <= 5) return 'scatter-point-yellow'
    return 'scatter-point-red'
  }

  const handlePointHover = (
    e: React.MouseEvent,
    epic: EpicAccuracy
  ) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setTooltip({
      visible: true,
      x: rect.left + rect.width / 2,
      y: rect.top - 10,
      epic
    })
  }

  const handlePointLeave = () => {
    setTooltip({ visible: false, x: 0, y: 0, epic: null })
  }

  const handlePointClick = (epic: EpicAccuracy) => {
    if (jiraBaseUrl) {
      window.open(`${jiraBaseUrl}${epic.epicKey}`, '_blank')
    }
  }

  const { maxValue, gridLines, epics } = chartData

  return (
    <div className="scatter-plot-section">
      <h3>Plan vs Fact (Scatter)</h3>
      <p className="scatter-plot-description">
        Each point is an epic. X = planned days, Y = actual days.
        Points on diagonal = perfect estimation. Above = late, below = early.
      </p>

      <div className="scatter-plot-container">
        <div className="scatter-plot-chart">
          {/* Y-axis labels */}
          <div className="scatter-y-axis">
            {[...Array(gridLines + 1)].map((_, i) => {
              const value = Math.round((maxValue / gridLines) * (gridLines - i))
              return <div key={i} className="scatter-y-label">{value}</div>
            })}
          </div>

          {/* Chart area */}
          <div className="scatter-area">
            {/* Grid lines */}
            <div className="scatter-grid">
              {/* Horizontal grid lines */}
              {[...Array(gridLines + 1)].map((_, i) => (
                <div
                  key={`h-${i}`}
                  className="scatter-grid-line-h"
                  style={{ top: `${(i / gridLines) * 100}%` }}
                />
              ))}
              {/* Vertical grid lines */}
              {[...Array(gridLines + 1)].map((_, i) => (
                <div
                  key={`v-${i}`}
                  className="scatter-grid-line-v"
                  style={{ left: `${(i / gridLines) * 100}%` }}
                />
              ))}
            </div>

            {/* Diagonal line (y = x) */}
            <svg
              className="scatter-diagonal"
              viewBox="0 0 100 100"
              preserveAspectRatio="none"
            >
              <line
                x1="0"
                y1="100"
                x2="100"
                y2="0"
                stroke="#97a0af"
                strokeWidth="0.5"
                strokeDasharray="2,2"
              />
            </svg>

            {/* Data points */}
            <div className="scatter-points">
              {epics.map((epic) => {
                const x = (epic.plannedDays / maxValue) * 100
                const y = 100 - (epic.actualDays / maxValue) * 100

                return (
                  <div
                    key={epic.epicKey}
                    className={`scatter-point ${getPointColor(epic)}`}
                    style={{
                      left: `${x}%`,
                      bottom: `${100 - y}%`
                    }}
                    onMouseEnter={(e) => handlePointHover(e, epic)}
                    onMouseLeave={handlePointLeave}
                    onClick={() => handlePointClick(epic)}
                    title={epic.epicKey}
                  />
                )
              })}
            </div>
          </div>
        </div>

        {/* X-axis labels */}
        <div className="scatter-x-axis">
          {[...Array(gridLines + 1)].map((_, i) => {
            const value = Math.round((maxValue / gridLines) * i)
            return <div key={i} className="scatter-x-label">{value}</div>
          })}
        </div>
        <div className="scatter-x-title">Planned Days</div>
      </div>

      {/* Legend */}
      <div className="scatter-legend">
        <div className="scatter-legend-item">
          <span className="scatter-legend-dot scatter-legend-dot-green" />
          Diff ≤ 2 days (accurate)
        </div>
        <div className="scatter-legend-item">
          <span className="scatter-legend-dot scatter-legend-dot-yellow" />
          Diff 3-5 days (acceptable)
        </div>
        <div className="scatter-legend-item">
          <span className="scatter-legend-dot scatter-legend-dot-red" />
          Diff &gt; 5 days (needs review)
        </div>
        <div className="scatter-legend-item">
          <span className="scatter-legend-line" />
          y = x (ideal)
        </div>
      </div>

      {/* Tooltip */}
      {tooltip.visible && tooltip.epic && (
        <div
          className="scatter-tooltip"
          style={{
            left: tooltip.x,
            top: tooltip.y,
            transform: 'translate(-50%, -100%)'
          }}
        >
          <div className="scatter-tooltip-title">{tooltip.epic.epicKey}</div>
          <div className="scatter-tooltip-row">
            <span className="scatter-tooltip-label">Summary:</span>
          </div>
          <div style={{ marginBottom: 4, fontSize: 11 }}>
            {tooltip.epic.summary.length > 50
              ? tooltip.epic.summary.substring(0, 50) + '...'
              : tooltip.epic.summary}
          </div>
          <div className="scatter-tooltip-row">
            <span className="scatter-tooltip-label">Planned:</span>
            <span className="scatter-tooltip-value">{tooltip.epic.plannedDays} days</span>
          </div>
          <div className="scatter-tooltip-row">
            <span className="scatter-tooltip-label">Actual:</span>
            <span className="scatter-tooltip-value">{tooltip.epic.actualDays} days</span>
          </div>
          <div className="scatter-tooltip-row">
            <span className="scatter-tooltip-label">Diff:</span>
            <span className="scatter-tooltip-value">
              {tooltip.epic.actualDays - tooltip.epic.plannedDays > 0 ? '+' : ''}
              {tooltip.epic.actualDays - tooltip.epic.plannedDays} days
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
