interface MetricCardProps {
  title: string
  value: string | number
  subtitle?: string
  trend?: 'up' | 'down' | 'neutral'
}

export function MetricCard({ title, value, subtitle, trend }: MetricCardProps) {
  return (
    <div className="metric-card">
      <div className="metric-card-title">{title}</div>
      <div className={`metric-card-value ${trend ? `trend-${trend}` : ''}`}>
        {value}
      </div>
      {subtitle && <div className="metric-card-subtitle">{subtitle}</div>}
    </div>
  )
}
