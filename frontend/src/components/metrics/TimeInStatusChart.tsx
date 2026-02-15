import { useState } from 'react'
import { TimeInStatusResponse } from '../../api/metrics'
import './TimeInStatusChart.css'

interface TimeInStatusChartProps {
  data: TimeInStatusResponse[]
}

interface TooltipState {
  visible: boolean
  x: number
  y: number
  item: TimeInStatusResponse | null
}

export function TimeInStatusChart({ data }: TimeInStatusChartProps) {
  const [tooltip, setTooltip] = useState<TooltipState>({
    visible: false, x: 0, y: 0, item: null
  })

  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Time in Status</h3>
        <div className="chart-empty">No status transition data available</div>
      </div>
    )
  }

  const sorted = [...data].sort((a, b) => a.sortOrder - b.sortOrder)

  // Scales
  const maxCount = Math.max(...sorted.map(d => d.transitionsCount), 1)
  const maxHours = Math.max(...sorted.map(d => d.p99Hours), ...sorted.map(d => d.p85Hours), 1)

  // Chart dimensions
  const chartHeight = 240
  const barAreaWidth = 100 / sorted.length // percentage per column

  function yRightPx(hours: number) {
    return chartHeight - (hours / maxHours) * chartHeight
  }

  // Generate nice Y-axis ticks
  function generateTicks(max: number, count: number): number[] {
    if (max === 0) return [0]
    const step = Math.ceil(max / count)
    const ticks: number[] = []
    for (let i = 0; i <= count; i++) {
      ticks.push(step * i)
    }
    return ticks
  }

  const leftTicks = generateTicks(maxCount, 4)
  const rightTicks = generateTicks(maxHours, 4)

  const handleMouseEnter = (e: React.MouseEvent, item: TimeInStatusResponse) => {
    const rect = (e.currentTarget as HTMLElement).closest('.tis-chart-area')?.getBoundingClientRect()
    if (!rect) return
    setTooltip({
      visible: true,
      x: e.clientX - rect.left,
      y: e.clientY - rect.top - 10,
      item
    })
  }

  const handleMouseLeave = () => {
    setTooltip({ visible: false, x: 0, y: 0, item: null })
  }

  // Build SVG line paths
  const colWidth = barAreaWidth
  const svgWidth = 100 // percentage-based

  function xCenter(index: number): number {
    return colWidth * index + colWidth / 2
  }

  function buildLinePath(values: number[]): string {
    return values
      .map((v, i) => `${i === 0 ? 'M' : 'L'} ${xCenter(i)} ${yRightPx(v)}`)
      .join(' ')
  }

  const medianPath = buildLinePath(sorted.map(d => d.medianHours))
  const p85Path = buildLinePath(sorted.map(d => d.p85Hours))
  const p99Path = buildLinePath(sorted.map(d => d.p99Hours))

  return (
    <div className="chart-section">
      <h3>Time in Status</h3>
      <div className="tis-wrapper">
        {/* Y-axis left (count) */}
        <div className="tis-y-axis tis-y-left">
          {leftTicks.slice().reverse().map((tick, i) => (
            <span key={i} className="tis-y-tick">{tick}</span>
          ))}
          <span className="tis-y-label">tasks</span>
        </div>

        {/* Chart area */}
        <div className="tis-chart-area" style={{ height: chartHeight }}>
          {/* Grid lines */}
          <div className="tis-grid">
            {leftTicks.map((tick, i) => (
              <div
                key={i}
                className="tis-grid-line"
                style={{ bottom: `${(tick / Math.max(...leftTicks)) * 100}%` }}
              />
            ))}
          </div>

          {/* Bars */}
          <div className="tis-bars">
            {sorted.map((item, i) => {
              const barHeight = (item.transitionsCount / maxCount) * 100
              return (
                <div
                  key={i}
                  className="tis-bar-col"
                  style={{ width: `${colWidth}%` }}
                  onMouseEnter={(e) => handleMouseEnter(e, item)}
                  onMouseLeave={handleMouseLeave}
                >
                  <div
                    className="tis-bar"
                    style={{ height: `${barHeight}%` }}
                  />
                </div>
              )
            })}
          </div>

          {/* SVG overlay for lines + dots */}
          <svg
            className="tis-lines-svg"
            viewBox={`0 0 ${svgWidth} ${chartHeight}`}
            preserveAspectRatio="none"
          >
            {/* P99 line — dashed red */}
            <path d={p99Path} className="tis-line tis-line-p99" vectorEffect="non-scaling-stroke" />
            {/* P85 line — orange */}
            <path d={p85Path} className="tis-line tis-line-p85" vectorEffect="non-scaling-stroke" />
            {/* Median line — blue */}
            <path d={medianPath} className="tis-line tis-line-median" vectorEffect="non-scaling-stroke" />
          </svg>

          {/* Dots overlay (HTML for better hover) */}
          <div className="tis-dots-overlay">
            {sorted.map((item, i) => (
              <div
                key={i}
                className="tis-dot-col"
                style={{ width: `${colWidth}%` }}
                onMouseEnter={(e) => handleMouseEnter(e, item)}
                onMouseLeave={handleMouseLeave}
              >
                {/* P99 dot */}
                <div
                  className="tis-dot tis-dot-p99"
                  style={{ bottom: `${(item.p99Hours / maxHours) * 100}%` }}
                />
                {/* P85 dot */}
                <div
                  className="tis-dot tis-dot-p85"
                  style={{ bottom: `${(item.p85Hours / maxHours) * 100}%` }}
                />
                {/* Median dot */}
                <div
                  className="tis-dot tis-dot-median"
                  style={{ bottom: `${(item.medianHours / maxHours) * 100}%` }}
                />
              </div>
            ))}
          </div>

          {/* Tooltip */}
          {tooltip.visible && tooltip.item && (
            <div
              className="tis-tooltip"
              style={{ left: tooltip.x, top: tooltip.y }}
            >
              <div className="tis-tooltip-title">{tooltip.item.status}</div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#0052CC' }} />
                Median: {tooltip.item.medianHours.toFixed(1)}h
              </div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#FF991F' }} />
                P85: {tooltip.item.p85Hours.toFixed(1)}h
              </div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#DE350B' }} />
                P99: {tooltip.item.p99Hours.toFixed(1)}h
              </div>
              <div className="tis-tooltip-row tis-tooltip-muted">
                Avg: {tooltip.item.avgHours.toFixed(1)}h
              </div>
              <div className="tis-tooltip-row tis-tooltip-muted">
                Transitions: {tooltip.item.transitionsCount}
              </div>
            </div>
          )}
        </div>

        {/* Y-axis right (hours) */}
        <div className="tis-y-axis tis-y-right">
          {rightTicks.slice().reverse().map((tick, i) => (
            <span key={i} className="tis-y-tick">{tick}</span>
          ))}
          <span className="tis-y-label">hours</span>
        </div>
      </div>

      {/* X-axis labels */}
      <div className="tis-x-axis">
        <div className="tis-x-spacer" />
        <div className="tis-x-labels">
          {sorted.map((item, i) => (
            <div
              key={i}
              className="tis-x-label"
              style={{ width: `${colWidth}%` }}
              title={item.status}
            >
              {item.status}
            </div>
          ))}
        </div>
        <div className="tis-x-spacer" />
      </div>

      {/* Legend */}
      <div className="tis-legend">
        <div className="tis-legend-item">
          <div className="tis-legend-bar" />
          <span>Tasks (n)</span>
        </div>
        <div className="tis-legend-item">
          <div className="tis-legend-line tis-legend-median" />
          <span>Median</span>
        </div>
        <div className="tis-legend-item">
          <div className="tis-legend-line tis-legend-p85" />
          <span>P85</span>
        </div>
        <div className="tis-legend-item">
          <div className="tis-legend-line tis-legend-p99" />
          <span>P99</span>
        </div>
      </div>
    </div>
  )
}
