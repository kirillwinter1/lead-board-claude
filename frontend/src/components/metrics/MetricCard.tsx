interface MetricCardProps {
  title: string
  value: string | number
  subtitle?: string
  trend?: 'up' | 'down' | 'neutral'
  tooltip?: string
}

export function MetricCard({ title, value, subtitle, trend, tooltip }: MetricCardProps) {
  return (
    <div className="metric-card">
      <div className="metric-card-header">
        <div className="metric-card-title">{title}</div>
        {tooltip && (
          <div className="metric-tooltip-wrapper">
            <span className="metric-tooltip-icon">?</span>
            <div className="metric-tooltip-text">{tooltip}</div>
          </div>
        )}
      </div>
      <div className={`metric-card-value ${trend ? `trend-${trend}` : ''}`}>
        {value}
      </div>
      {subtitle && <div className="metric-card-subtitle">{subtitle}</div>}
    </div>
  )
}
