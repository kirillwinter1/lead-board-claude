import { createPortal } from 'react-dom'
import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import type { BoardNode } from './types'
import { SEVERITY_COLORS } from '../../constants/colors'

/** Small colour-coded severity dot — replaces the 🔴🟡🔵 emoji. Colour from SEVERITY_COLORS. */
function SeverityDot({ severity }: { severity: string }) {
  const colors = SEVERITY_COLORS[severity] || SEVERITY_COLORS.INFO
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'inline-block',
        width: 8,
        height: 8,
        borderRadius: '50%',
        background: colors.text,
        flexShrink: 0,
      }}
    />
  )
}

export function AlertIcon({ node }: { node: BoardNode }) {
  const { ref, showTooltip, tooltipPos, handleMouseEnter, handleMouseLeave } = useTooltipPosition<HTMLDivElement>({
    tooltipWidth: 320,
    minSpaceNeeded: 150,
  })

  const alerts = node.alerts || []

  if (alerts.length === 0) {
    return <span className="no-alert">--</span>
  }

  // Find the highest severity
  const hasError = alerts.some(a => a.severity === 'ERROR')
  const hasWarning = alerts.some(a => a.severity === 'WARNING')

  const severityClass = hasError ? 'error' : hasWarning ? 'warning' : 'info'
  const count = alerts.length

  // Severity labels
  const severityLabels: Record<string, string> = {
    ERROR: 'ERROR',
    WARNING: 'WARNING',
    INFO: 'INFO'
  }

  return (
    <div
      ref={ref}
      className="alert-icon-wrapper"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className={`alert-icon alert-${severityClass}`} role="img" aria-label={`${count} data quality alerts`}>
        {count}
      </span>

      {showTooltip && tooltipPos && createPortal(
        <div
          className="alert-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="alert-tooltip-header">
            <strong>Data quality issues ({count})</strong>
          </div>
          <div className="alert-tooltip-list">
            {alerts.map((alert, idx) => (
              <div key={idx} className={`alert-tooltip-item alert-${alert.severity.toLowerCase()}`}>
                <div className="alert-item-header">
                  <span className="alert-severity" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <SeverityDot severity={alert.severity} />
                    {severityLabels[alert.severity] || alert.severity}
                  </span>
                </div>
                <div className="alert-message">
                  {alert.label || alert.rule}
                </div>
              </div>
            ))}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
