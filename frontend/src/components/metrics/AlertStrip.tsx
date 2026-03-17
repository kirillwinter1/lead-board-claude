import { useState } from 'react'
import { DeliveryHealth } from '../../api/metrics'
import { SEVERITY_COLORS } from '../../constants/colors'
import './AlertStrip.css'

interface AlertStripProps {
  alerts: DeliveryHealth['alerts']
}

export function AlertStrip({ alerts }: AlertStripProps) {
  const [expandedIdx, setExpandedIdx] = useState<number | null>(null)

  if (!alerts || alerts.length === 0) return null

  const visible = alerts.slice(0, 3)

  return (
    <div className="alert-strip">
      {visible.map((alert, i) => {
        const colors = alert.severity === 'CRITICAL'
          ? SEVERITY_COLORS.ERROR
          : alert.severity === 'WARNING'
            ? SEVERITY_COLORS.WARNING
            : SEVERITY_COLORS.INFO
        const isExpanded = expandedIdx === i

        return (
          <div key={i} className="alert-strip-item" style={{ background: colors.bg, borderColor: colors.border }}>
            <button
              className="alert-strip-pill"
              onClick={() => setExpandedIdx(isExpanded ? null : i)}
              style={{ color: colors.text }}
            >
              <span className="alert-strip-icon">
                {alert.severity === 'CRITICAL' ? '!' : alert.severity === 'WARNING' ? '!' : 'i'}
              </span>
              <span className="alert-strip-title">{alert.title}</span>
              <span className="alert-strip-expand">{isExpanded ? '−' : '+'}</span>
            </button>
            {isExpanded && (
              <div className="alert-strip-details" style={{ color: colors.text }}>
                <div className="alert-strip-desc">{alert.description}</div>
                {alert.recommendation && (
                  <div className="alert-strip-rec">{alert.recommendation}</div>
                )}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
