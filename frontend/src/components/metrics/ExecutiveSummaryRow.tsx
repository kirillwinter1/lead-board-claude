import { useState, useEffect } from 'react'
import { getExecutiveSummary, ExecutiveSummary, KpiCard as KpiCardData } from '../../api/metrics'
import { DSR_GREEN, DSR_RED, TEXT_MUTED, TEXT_PRIMARY, TEXT_SECONDARY } from '../../constants/colors'
import './ExecutiveSummaryRow.css'

interface ExecutiveSummaryRowProps {
  teamId: number
  from: string
  to: string
}

// For these metrics, UP is bad (higher = slower)
const INVERSE_METRICS = new Set(['Cycle Time', 'Lead Time', 'Blocked/Aging'])

export function ExecutiveSummaryRow({ teamId, from, to }: ExecutiveSummaryRowProps) {
  const [data, setData] = useState<ExecutiveSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getExecutiveSummary(teamId, from, to)
      .then(setData)
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
      {cards.map(card => (
        <KpiCardComponent key={card.label} card={card} />
      ))}
    </div>
  )
}

function KpiCardComponent({ card }: { card: KpiCardData }) {
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
      <div className="kpi-card-value" style={{ color: TEXT_PRIMARY }}>{card.value}</div>
      {hasDelta && (
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
    </div>
  )
}
