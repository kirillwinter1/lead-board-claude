import { STATUS_AGE_COLORS } from '../constants/colors'

export type StatusAgeLevel = 'NORMAL' | 'WARNING' | 'CRITICAL'

interface StatusAgeBadgeProps {
  days: number | null
  level?: StatusAgeLevel
  reason?: string | null
}

// Small "days in status" pill (F79). The number shows for all tasks; the color comes
// ONLY from the backend-decided level (backlog/done already mapped to NORMAL).
export function StatusAgeBadge({ days, level, reason }: StatusAgeBadgeProps) {
  if (days == null) return null

  const { bg, fg } = STATUS_AGE_COLORS[level ?? 'NORMAL']

  return (
    <span
      className="status-age-badge"
      title={reason ?? undefined}
      style={{
        fontSize: 10,
        padding: '1px 5px',
        borderRadius: 3,
        background: bg,
        color: fg,
        fontWeight: 500,
        lineHeight: '16px',
        whiteSpace: 'nowrap',
      }}
    >
      {days}д
    </span>
  )
}
