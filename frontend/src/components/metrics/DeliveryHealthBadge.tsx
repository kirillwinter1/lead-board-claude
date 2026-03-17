import { useState, useEffect, useRef } from 'react'
import { getDeliveryHealth, DeliveryHealth } from '../../api/metrics'
import { DSR_GREEN, DSR_YELLOW, DSR_RED, TEXT_MUTED, TEXT_PRIMARY } from '../../constants/colors'
import './DeliveryHealthBadge.css'

interface DeliveryHealthBadgeProps {
  teamId: number
  from: string
  to: string
  onAlerts?: (alerts: DeliveryHealth['alerts']) => void
}

function getGradeColor(grade: string): string {
  if (grade === 'A' || grade === 'B') return DSR_GREEN
  if (grade === 'C') return DSR_YELLOW
  return DSR_RED
}

function getDimensionStatusColor(status: string): string {
  if (status === 'GOOD') return DSR_GREEN
  if (status === 'WARNING') return DSR_YELLOW
  return DSR_RED
}

export function DeliveryHealthBadge({ teamId, from, to, onAlerts }: DeliveryHealthBadgeProps) {
  const [data, setData] = useState<DeliveryHealth | null>(null)
  const [loading, setLoading] = useState(true)
  const onAlertsRef = useRef(onAlerts)
  onAlertsRef.current = onAlerts

  useEffect(() => {
    setLoading(true)
    getDeliveryHealth(teamId, from, to)
      .then(health => {
        setData(health)
        onAlertsRef.current?.(health.alerts)
      })
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [teamId, from, to])

  if (loading) return <div className="health-badge-placeholder" />
  if (!data) return null

  const gradeColor = getGradeColor(data.grade)

  return (
    <div className="health-badge">
      <div className="health-badge-score" style={{ borderColor: gradeColor }}>
        <div className="health-badge-grade" style={{ color: gradeColor }}>{data.grade}</div>
        <div className="health-badge-number" style={{ color: TEXT_PRIMARY }}>{data.score}</div>
      </div>
      <div className="health-badge-label" style={{ color: TEXT_MUTED }}>Health</div>
      <div className="health-badge-dimensions">
        {data.dimensions.map(dim => (
          <div key={dim.name} className="health-dim" title={`${dim.name}: ${dim.score}`}>
            <div className="health-dim-bar">
              <div
                className="health-dim-fill"
                style={{
                  width: `${Math.min(Number(dim.score), 100)}%`,
                  background: getDimensionStatusColor(dim.status),
                }}
              />
            </div>
            <span className="health-dim-name">{dim.name.length > 6 ? dim.name.slice(0, 5) + '.' : dim.name}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
