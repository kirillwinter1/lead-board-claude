import { useState, useEffect } from 'react'
import { getExecutiveSummary, getSparklines, ExecutiveSummary, KpiCard as KpiCardData, SparklinePoint } from '../../api/metrics'
import { DSR_GREEN, DSR_RED, TEXT_MUTED, TEXT_PRIMARY, TEXT_SECONDARY } from '../../constants/colors'
import './ExecutiveSummaryRow.css'

interface ExecutiveSummaryRowProps {
  teamId: number
  from: string
  to: string
}

// For these metrics, UP is bad (higher = slower)
const INVERSE_METRICS = new Set(['Cycle Time', 'Lead Time', 'Blocked/Aging'])

const PERCENTILE_LABELS: Record<string, string> = {
  median: 'Med',
  p85: 'P85',
  p90: 'P90',
}

const SPARKLINE_HINTS: Record<string, string> = {
  'Throughput': 'Stories closed per week',
  'Cycle Time': 'Median days, start → done',
  'Lead Time': 'Median days, created → done',
  'Predictability': '% epics on time (DSR ≤ 1.1)',
  'Utilization': 'Logged hours / capacity',
}

const SPARKLINE_KEYS: Record<string, string> = {
  'Throughput': 'throughput',
  'Cycle Time': 'cycleTimeMedian',
  'Lead Time': 'leadTimeMedian',
  'Predictability': 'predictability',
  'Utilization': 'utilization',
}

// Bar sparkline for counts/percentages
const BAR_METRICS = new Set(['Throughput', 'Utilization'])

type SparklineMap = Record<string, SparklinePoint[]>

export function ExecutiveSummaryRow({ teamId, from, to }: ExecutiveSummaryRowProps) {
  const [data, setData] = useState<ExecutiveSummary | null>(null)
  const [sparklines, setSparklines] = useState<SparklineMap>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      getExecutiveSummary(teamId, from, to),
      getSparklines(teamId, from, to).catch(() => null),
    ])
      .then(([summary, sparks]) => {
        setData(summary)
        if (sparks) {
          setSparklines(sparks as unknown as SparklineMap)
        }
      })
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [teamId, from, to])

  if (loading) return <div className="kpi-loading">Loading KPIs...</div>
  if (!data) return null

  const cards: KpiCardData[] = [
    data.throughput,
    data.cycleTimeMedian,
    data.leadTimeMedian,
    data.predictability,
    data.capacityUtilization,
    data.blockedRisk,
  ]

  return (
    <div className="kpi-grid">
      {cards.map(card => {
        const key = SPARKLINE_KEYS[card.label]
        const points = key ? (sparklines[key] || []) : []
        return (
          <KpiCardComponent
            key={card.label}
            card={card}
            sparklinePoints={points}
          />
        )
      })}
    </div>
  )
}

function KpiCardComponent({ card, sparklinePoints }: { card: KpiCardData; sparklinePoints: SparklinePoint[] }) {
  const hasPercentiles = card.percentiles && Object.keys(card.percentiles).length > 0
  const [selectedPct, setSelectedPct] = useState<string>('median')

  const isInverse = INVERSE_METRICS.has(card.label)
  const hasSparkline = sparklinePoints.length > 1
  const hint = SPARKLINE_HINTS[card.label]
  const isBarType = BAR_METRICS.has(card.label)

  // Get current value and prev value based on selected percentile
  let currentValue = card.rawValue
  let prevValue = card.prevValue
  if (hasPercentiles && card.percentiles) {
    currentValue = card.percentiles[selectedPct] ?? card.rawValue
    prevValue = card.prevPercentiles?.[selectedPct] ?? null
  }

  // Calculate delta for the selected percentile
  let delta: number | null = null
  if (prevValue !== null && prevValue > 0) {
    delta = ((currentValue - prevValue) / prevValue) * 100
  } else if (!hasPercentiles) {
    delta = card.deltaPercent
  }
  const hasDelta = delta !== null

  let deltaColor = TEXT_MUTED
  if (hasDelta && delta !== null) {
    const isPositive = delta > 0
    if (isInverse) {
      deltaColor = isPositive ? DSR_RED : DSR_GREEN
    } else {
      deltaColor = isPositive ? DSR_GREEN : DSR_RED
    }
    if (Math.abs(delta) < 3) deltaColor = TEXT_MUTED
  }

  const deltaArrow = hasDelta && delta !== null ? (delta > 0 ? '+' : '') : ''

  // Format display value
  const displayValue = hasPercentiles
    ? (currentValue > 0 ? currentValue.toFixed(1) + 'd' : '—')
    : card.value

  // Sparkline color: matches trend direction
  const sparkColor = deltaColor === TEXT_MUTED ? '#97A0AF' : deltaColor

  // Target border
  const hasTarget = card.target !== null && card.target !== undefined
  let borderColor = '#DFE1E6'
  if (hasTarget && card.rawValue !== null && card.target !== null) {
    if (isInverse) {
      borderColor = card.rawValue <= card.target ? DSR_GREEN : DSR_RED
    } else {
      borderColor = card.rawValue >= card.target ? DSR_GREEN : DSR_RED
    }
  }

  return (
    <div className="kpi-card" style={{ borderTop: `3px solid ${borderColor}` }}>
      <div className="kpi-card-label" style={{ color: TEXT_SECONDARY }}>{card.label}</div>
      <div className="kpi-card-value" style={{ color: TEXT_PRIMARY }}>{displayValue}</div>
      {hasDelta && delta !== null && (
        <div className="kpi-card-delta" style={{ color: deltaColor }}>
          {deltaArrow}{delta.toFixed(1)}%
        </div>
      )}
      {!hasDelta && card.trend === 'WARNING' && (
        <div className="kpi-card-delta" style={{ color: '#FFAB00' }}>at risk</div>
      )}
      <div className="kpi-card-sample" style={{ color: TEXT_MUTED }}>
        n={card.sampleSize}
      </div>
      {hasPercentiles && (
        <div className="kpi-pct-switcher">
          {Object.keys(PERCENTILE_LABELS).map(key => (
            <button
              key={key}
              className={`kpi-pct-btn ${selectedPct === key ? 'active' : ''}`}
              onClick={() => setSelectedPct(key)}
            >
              {PERCENTILE_LABELS[key]}
            </button>
          ))}
        </div>
      )}
      {hasSparkline && (
        <div className="kpi-sparkline-area">
          {isBarType
            ? <BarSparkline points={sparklinePoints} color={sparkColor} />
            : <LineSparkline points={sparklinePoints} color={sparkColor} />
          }
          {hint && <div className="kpi-sparkline-hint">{hint}</div>}
        </div>
      )}
    </div>
  )
}

function BarSparkline({ points, color }: { points: SparklinePoint[]; color: string }) {
  const values = points.map(p => p.value)
  const max = Math.max(...values, 1)
  const w = 120
  const h = 28
  const barWidth = Math.max(Math.floor((w - (values.length - 1) * 3) / values.length), 4)
  const gap = 3

  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="kpi-sparkline-svg">
      {values.map((v, i) => {
        const barH = (v / max) * h
        const x = i * (barWidth + gap)
        const opacity = 0.4 + (i / values.length) * 0.6
        return (
          <rect
            key={i}
            x={x}
            y={h - barH}
            width={barWidth}
            height={barH}
            rx={2}
            fill={color}
            opacity={opacity}
          />
        )
      })}
    </svg>
  )
}

function LineSparkline({ points, color }: { points: SparklinePoint[]; color: string }) {
  const values = points.map(p => p.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const w = 120
  const h = 28
  const pad = 4

  const pts = values.map((v, i) => {
    const x = (i / (values.length - 1)) * (w - pad * 2) + pad
    const y = h - pad - ((v - min) / range) * (h - pad * 2)
    return `${x},${y}`
  }).join(' ')

  const lastX = ((values.length - 1) / (values.length - 1)) * (w - pad * 2) + pad
  const lastY = h - pad - ((values[values.length - 1] - min) / range) * (h - pad * 2)

  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="kpi-sparkline-svg">
      <polyline
        points={pts}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={lastX} cy={lastY} r={2.5} fill={color} />
    </svg>
  )
}
