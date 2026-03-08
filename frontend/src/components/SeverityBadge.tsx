import './SeverityBadge.css'

const SEVERITY_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  ERROR: { bg: '#fee2e2', text: '#dc2626', border: '#fca5a5' },
  WARNING: { bg: '#fef3c7', text: '#d97706', border: '#fcd34d' },
  INFO: { bg: '#f3f4f6', text: '#6b7280', border: '#d1d5db' },
}

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
