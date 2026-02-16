import { useState, useRef } from 'react'
import { TimeInStatusResponse } from '../../api/metrics'
import './TimeInStatusChart.css'

interface TimeInStatusChartProps {
  data: TimeInStatusResponse[]
}

interface TooltipData {
  item: TimeInStatusResponse
  x: number
  y: number
}

// Safe number: treat null/undefined/NaN as 0
function num(v: number | null | undefined): number {
  return typeof v === 'number' && !isNaN(v) ? v : 0
}

export function TimeInStatusChart({ data }: TimeInStatusChartProps) {
  const [tooltip, setTooltip] = useState<TooltipData | null>(null)
  const chartRef = useRef<HTMLDivElement>(null)

  if (!data || data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Time in Status</h3>
        <div className="chart-empty">No status transition data available</div>
      </div>
    )
  }

  const sorted = [...data].sort((a, b) => num(a.sortOrder) - num(b.sortOrder))

  // Scales — safe against null/NaN
  const maxCount = Math.max(...sorted.map(d => num(d.transitionsCount)), 1)
  const allHourValues = sorted.flatMap(d => [num(d.medianHours), num(d.p85Hours), num(d.p99Hours)])
  const maxHours = Math.max(...allHourValues, 1)

  // Chart dimensions
  const chartHeight = 240
  const colCount = sorted.length
  const colWidth = 100 / colCount // % per column

  // Y position in pixels for hours axis
  function yPx(hours: number): number {
    return chartHeight - (hours / maxHours) * chartHeight
  }

  // X center of column in % (for SVG viewBox 0..100)
  function xPct(index: number): number {
    return colWidth * index + colWidth / 2
  }

  // Generate Y-axis ticks
  function makeTicks(max: number, steps: number): number[] {
    if (max <= 0) return [0]
    const step = Math.ceil(max / steps)
    const result: number[] = []
    for (let i = 0; i <= steps; i++) result.push(step * i)
    return result
  }

  const leftTicks = makeTicks(maxCount, 4)
  const rightTicks = makeTicks(maxHours, 4)
  const leftMax = leftTicks[leftTicks.length - 1] || 1

  // SVG paths
  function linePath(getter: (d: TimeInStatusResponse) => number): string {
    return sorted
      .map((d, i) => `${i === 0 ? 'M' : 'L'}${xPct(i)},${yPx(getter(d))}`)
      .join(' ')
  }

  const medianPath = linePath(d => num(d.medianHours))
  const p85Path = linePath(d => num(d.p85Hours))
  const p99Path = linePath(d => num(d.p99Hours))

  const handleMouse = (e: React.MouseEvent, item: TimeInStatusResponse) => {
    const rect = chartRef.current?.getBoundingClientRect()
    if (!rect) return
    setTooltip({
      item,
      x: e.clientX - rect.left,
      y: e.clientY - rect.top - 10,
    })
  }

  return (
    <div className="chart-section">
      <h3>Time in Status</h3>
      <div className="tis-wrapper">
        {/* Y-axis left (tasks) */}
        <div className="tis-y-axis tis-y-left">
          {[...leftTicks].reverse().map((t, i) => (
            <span key={i} className="tis-y-tick">{t}</span>
          ))}
          <span className="tis-y-label">tasks</span>
        </div>

        {/* Main chart */}
        <div className="tis-chart-area" ref={chartRef} style={{ height: chartHeight }}>
          {/* Grid */}
          {leftTicks.map((t, i) => (
            <div
              key={i}
              className="tis-grid-line"
              style={{ bottom: `${(t / leftMax) * 100}%` }}
            />
          ))}

          {/* Gray bars */}
          <div className="tis-bars">
            {sorted.map((item, i) => (
              <div
                key={i}
                className="tis-bar-col"
                style={{ width: `${colWidth}%` }}
                onMouseMove={e => handleMouse(e, item)}
                onMouseLeave={() => setTooltip(null)}
              >
                <div
                  className="tis-bar"
                  style={{ height: `${(num(item.transitionsCount) / maxCount) * 100}%` }}
                />
              </div>
            ))}
          </div>

          {/* SVG lines */}
          <svg
            className="tis-lines-svg"
            viewBox={`0 0 100 ${chartHeight}`}
            preserveAspectRatio="none"
          >
            <path d={p99Path} className="tis-line tis-line-p99" vectorEffect="non-scaling-stroke" />
            <path d={p85Path} className="tis-line tis-line-p85" vectorEffect="non-scaling-stroke" />
            <path d={medianPath} className="tis-line tis-line-median" vectorEffect="non-scaling-stroke" />
          </svg>

          {/* Dots */}
          <div className="tis-dots-overlay">
            {sorted.map((item, i) => (
              <div
                key={i}
                className="tis-dot-col"
                style={{ width: `${colWidth}%` }}
              >
                <div className="tis-dot tis-dot-p99"
                  style={{ bottom: `${(num(item.p99Hours) / maxHours) * 100}%` }} />
                <div className="tis-dot tis-dot-p85"
                  style={{ bottom: `${(num(item.p85Hours) / maxHours) * 100}%` }} />
                <div className="tis-dot tis-dot-median"
                  style={{ bottom: `${(num(item.medianHours) / maxHours) * 100}%` }} />
              </div>
            ))}
          </div>

          {/* Tooltip */}
          {tooltip && (
            <div className="tis-tooltip" style={{ left: tooltip.x, top: tooltip.y }}>
              <div className="tis-tooltip-title">{tooltip.item.status}</div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#0052CC' }} />
                Median: {num(tooltip.item.medianHours).toFixed(1)}h
              </div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#FF991F' }} />
                P85: {num(tooltip.item.p85Hours).toFixed(1)}h
              </div>
              <div className="tis-tooltip-row">
                <span className="tis-tooltip-dot" style={{ background: '#DE350B' }} />
                P99: {num(tooltip.item.p99Hours).toFixed(1)}h
              </div>
              <div className="tis-tooltip-row tis-tooltip-muted">
                Avg: {num(tooltip.item.avgHours).toFixed(1)}h
              </div>
              <div className="tis-tooltip-row tis-tooltip-muted">
                Transitions: {num(tooltip.item.transitionsCount)}
              </div>
            </div>
          )}
        </div>

        {/* Y-axis right (hours) */}
        <div className="tis-y-axis tis-y-right">
          {[...rightTicks].reverse().map((t, i) => (
            <span key={i} className="tis-y-tick">{t}</span>
          ))}
          <span className="tis-y-label">hours</span>
        </div>
      </div>

      {/* X-axis */}
      <div className="tis-x-axis">
        <div className="tis-x-spacer" />
        <div className="tis-x-labels">
          {sorted.map((item, i) => (
            <div key={i} className="tis-x-label" style={{ width: `${colWidth}%` }} title={item.status}>
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
