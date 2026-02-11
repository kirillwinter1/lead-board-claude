import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import type { BoardNode } from './types'

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
    ERROR: 'Ошибка',
    WARNING: 'Предупреждение',
    INFO: 'Инфо'
  }

  // Severity icons
  const severityIcons: Record<string, string> = {
    ERROR: '🔴',
    WARNING: '🟡',
    INFO: '🔵'
  }

  return (
    <div
      ref={ref}
      className="alert-icon-wrapper"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className={`alert-icon alert-${severityClass}`}>
        {count}
      </span>

      {showTooltip && tooltipPos && (
        <div
          className="alert-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="alert-tooltip-header">
            <strong>Data Quality Issues ({count})</strong>
          </div>
          <div className="alert-tooltip-list">
            {alerts.map((alert, idx) => (
              <div key={idx} className={`alert-tooltip-item alert-${alert.severity.toLowerCase()}`}>
                <div className="alert-item-header">
                  <span className="alert-severity">
                    {severityIcons[alert.severity]} {severityLabels[alert.severity] || alert.severity}
                  </span>
                  <span className="alert-rule">{alert.rule}</span>
                </div>
                <div className="alert-message">
                  {alert.message}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
