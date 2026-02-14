import { useStatusStyles } from './StatusStylesContext'

function getContrastColor(hex: string): string {
  const c = hex.replace('#', '')
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return luminance > 0.6 ? '#172b4d' : '#ffffff'
}

export function StatusBadge({ status, color }: { status: string; color?: string | null }) {
  const statusStyles = useStatusStyles()
  const statusClass = status.toLowerCase().replace(/\s+/g, '-')

  const effectiveColor = color ?? statusStyles[status]?.color

  if (effectiveColor) {
    return (
      <span
        className="status-badge"
        style={{ backgroundColor: effectiveColor, color: getContrastColor(effectiveColor) }}
      >
        {status}
      </span>
    )
  }

  return <span className={`status-badge ${statusClass}`}>{status}</span>
}
