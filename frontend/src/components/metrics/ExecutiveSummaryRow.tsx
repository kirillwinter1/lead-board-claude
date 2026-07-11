import { useState, useEffect } from 'react'
import {
  getExecutiveSummary,
  getSparklines,
  ExecutiveSummary,
  KpiCard as KpiCardData,
  SparklineData,
  SparklinePoint,
} from '../../api/metrics'
import { DSR_GREEN, DSR_RED, DSR_YELLOW, TEXT_MUTED, TEXT_PRIMARY, TEXT_SECONDARY, TEXT_SUBTLE, BORDER_DEFAULT } from '../../constants/colors'
import './ExecutiveSummaryRow.css'

interface ExecutiveSummaryRowProps {
  teamId: number
  from: string
  to: string
}

// For these metrics, UP is bad (higher = slower)
const INVERSE_METRICS = new Set(['Cycle Time', 'Lead Time', 'Blocked/Aging'])

// Sparkline keys per KPI card (by position, not label — labels vary e.g. "Capacity").
// blockedRisk has no weekly series, so its key is null.
type SparkKey = keyof SparklineData

// Bar-style sparkline for count / percentage series; line-style for duration series.
const BAR_KEYS = new Set<SparkKey>(['throughput', 'utilization'])

// Short hint shown under each sparkline.
const SPARKLINE_HINTS: Record<SparkKey, string> = {
  throughput: 'Stories closed / week',
  cycleTimeMedian: 'Median days, start → done',
  leadTimeMedian: 'Median days, created → done',
  predictability: '% epics on time',
  utilization: 'Logged hours / capacity',
}

export function ExecutiveSummaryRow({ teamId, from, to }: ExecutiveSummaryRowProps) {
  const [data, setData] = useState<ExecutiveSummary | null>(null)
  const [sparklines, setSparklines] = useState<SparklineData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      getExecutiveSummary(teamId, from, to),
      // Sparklines are a progressive enhancement — never block KPIs on them.
      getSparklines(teamId, from, to).catch(() => null),
    ])
      .then(([summary, sparks]) => {
        setData(summary)
        setSparklines(sparks)
      })
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [teamId, from, to])

  if (loading) return <div className="kpi-loading">Loading KPIs...</div>
  if (!data) return null

  const cards: { card: KpiCardData; sparkKey: SparkKey | null }[] = [
    { card: data.throughput, sparkKey: 'throughput' },
    { card: data.cycleTimeMedian, sparkKey: 'cycleTimeMedian' },
    { card: data.leadTimeMedian, sparkKey: 'leadTimeMedian' },
    { card: data.predictability, sparkKey: 'predictability' },
    { card: data.capacityUtilization, sparkKey: 'utilization' },
    { card: data.blockedRisk, sparkKey: null },
  ]

  return (
    <div className="kpi-grid">
      {cards.map(({ card, sparkKey }) => {
        const points = sparkKey && sparklines ? (sparklines[sparkKey] ?? []) : []
        return (
          <KpiCardComponent
            key={card.label}
            card={card}
            sparkKey={sparkKey}
            sparklinePoints={points}
          />
        )
      })}
    </div>
  )
}

function KpiCardComponent({
  card,
  sparkKey,
  sparklinePoints,
}: {
  card: KpiCardData
  sparkKey: SparkKey | null
  sparklinePoints: SparklinePoint[]
}) {
  const isInverse = INVERSE_METRICS.has(card.label)
  const delta = card.deltaPercent
  const hasDelta = delta !== null && delta !== undefined

  // Determine color: for inverse metrics, going UP is bad (red), going DOWN is good (green)
  let deltaColor = TEXT_MUTED
  if (hasDelta) {
    const isPositive = delta > 0
    if (isInverse) {
      deltaColor = isPositive ? DSR_RED : DSR_GREEN
    } else {
      deltaColor = isPositive ? DSR_GREEN : DSR_RED
    }
    if (Math.abs(delta) < 3) deltaColor = TEXT_MUTED // Stable
  }

  const deltaArrow = hasDelta ? (delta > 0 ? '+' : '') : ''

  // Target border
  const hasTarget = card.target !== null && card.target !== undefined
  let borderColor = BORDER_DEFAULT
  if (hasTarget && card.rawValue !== null && card.target !== null) {
    if (isInverse) {
      borderColor = card.rawValue <= card.target ? DSR_GREEN : DSR_RED
    } else {
      borderColor = card.rawValue >= card.target ? DSR_GREEN : DSR_RED
    }
  }

  // Sparkline needs at least 2 points to draw a trend; colour follows the delta direction.
  const hasSparkline = sparkKey !== null && sparklinePoints.length > 1
  const sparkColor = deltaColor === TEXT_MUTED ? TEXT_SUBTLE : deltaColor
  const hint = sparkKey ? SPARKLINE_HINTS[sparkKey] : undefined

  return (
    <div className="kpi-card" style={{ borderTop: `3px solid ${borderColor}` }}>
      <div className="kpi-card-label" style={{ color: TEXT_SECONDARY }}>{card.label}</div>
      <div className="kpi-card-value" style={{ color: TEXT_PRIMARY }}>{card.value}</div>
      {hasDelta && (
        <div className="kpi-card-delta" style={{ color: deltaColor }}>
          {deltaArrow}{delta.toFixed(1)}%
        </div>
      )}
      {!hasDelta && card.trend === 'WARNING' && (
        <div className="kpi-card-delta" style={{ color: DSR_YELLOW }}>at risk</div>
      )}
      <div className="kpi-card-sample" style={{ color: TEXT_MUTED }}>
        n={card.sampleSize}
      </div>
      {hasSparkline && (
        <div className="kpi-sparkline-area">
          {sparkKey && BAR_KEYS.has(sparkKey)
            ? <BarSparkline points={sparklinePoints} color={sparkColor} />
            : <LineSparkline points={sparklinePoints} color={sparkColor} />}
          {hint && <div className="kpi-sparkline-hint" style={{ color: TEXT_SUBTLE }}>{hint}</div>}
        </div>
      )}
    </div>
  )
}

const SPARK_W = 120
const SPARK_H = 28

function BarSparkline({ points, color }: { points: SparklinePoint[]; color: string }) {
  const values = points.map(p => p.value)
  const max = Math.max(...values, 1)
  const gap = 3
  const barWidth = Math.max(Math.floor((SPARK_W - (values.length - 1) * gap) / values.length), 3)

  return (
    <svg width={SPARK_W} height={SPARK_H} viewBox={`0 0 ${SPARK_W} ${SPARK_H}`} className="kpi-sparkline-svg" role="img" aria-hidden="true">
      {values.map((v, i) => {
        const barH = max > 0 ? (v / max) * SPARK_H : 0
        const x = i * (barWidth + gap)
        // Fade older bars so the most recent week reads strongest.
        const opacity = 0.4 + (i / values.length) * 0.6
        return (
          <rect key={i} x={x} y={SPARK_H - barH} width={barWidth} height={barH} rx={2} fill={color} opacity={opacity} />
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
  const pad = 4

  const coords = values.map((v, i) => {
    const x = (i / (values.length - 1)) * (SPARK_W - pad * 2) + pad
    const y = SPARK_H - pad - ((v - min) / range) * (SPARK_H - pad * 2)
    return { x, y }
  })
  const polyline = coords.map(c => `${c.x},${c.y}`).join(' ')
  const last = coords[coords.length - 1]

  return (
    <svg width={SPARK_W} height={SPARK_H} viewBox={`0 0 ${SPARK_W} ${SPARK_H}`} className="kpi-sparkline-svg" role="img" aria-hidden="true">
      <polyline points={polyline} fill="none" stroke={color} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={last.x} cy={last.y} r={2.5} fill={color} />
    </svg>
  )
}
