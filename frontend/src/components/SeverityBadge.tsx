import './SeverityBadge.css'
import { SEVERITY_COLORS } from '../constants/colors'

export { SEVERITY_COLORS }

interface SeverityBadgeProps {
  severity: string
}

export function SeverityBadge({ severity }: SeverityBadgeProps) {
  const colors = SEVERITY_COLORS[severity] || SEVERITY_COLORS.INFO
  return (
    <span
      className="severity-badge"
      style={{
        backgroundColor: colors.bg,
        color: colors.text,
        borderColor: colors.border,
      }}
    >
      {severity}
    </span>
  )
}
