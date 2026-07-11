import type { CSSProperties } from 'react'
import type { StatusStyle } from '../../api/board'
import { useStatusStyles } from './StatusStylesContext'

// Backgrounds of the .status-badge CSS fallback classes (BoardPage.css) — keep in sync.
// Lets non-badge renderers (timeline/chart segments) paint the exact color a StatusBadge
// would get for statuses that have no configured color.
const SLUG_BG: Record<string, string> = {
  'в-работе': '#deebff',
  'in-progress': '#deebff',
  'готово': '#e3fcef',
  'done': '#e3fcef',
  'closed': '#e3fcef',
  'backlog': '#f4f5f7',
  'planning': '#f4f5f7',
  'to-do': '#f4f5f7',
  'selected-for-development': '#eae6ff',
}
const DEFAULT_STATUS_BG = '#dfe1e6'

/** Background a StatusBadge would use for this status: configured color → CSS-class palette → default grey. */
export function resolveStatusBgColor(status: string, statusStyles: Record<string, StatusStyle>): string {
  const explicit = statusStyles[status]?.color
  if (explicit) return explicit
  return SLUG_BG[status.toLowerCase().replace(/\s+/g, '-')] ?? DEFAULT_STATUS_BG
}

function getContrastColor(hex: string): string {
  const c = hex.replace('#', '')
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return luminance > 0.6 ? '#172b4d' : '#ffffff'
}

const badgeBaseStyle: CSSProperties = {
  display: 'inline-block',
  padding: '4px 10px',
  borderRadius: 4,
  fontWeight: 700,
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  lineHeight: 1,
  whiteSpace: 'nowrap',
}

export function StatusBadge({ status, color, maxWidth }: { status: string; color?: string | null; maxWidth?: number }) {
  const statusStyles = useStatusStyles()
  const statusClass = status.toLowerCase().replace(/\s+/g, '-')

  const effectiveColor = color ?? statusStyles[status]?.color

  // Opt-in truncation for tight layouts (e.g. Timeline labels). Replaces the former
  // global `.status-badge { max-width }` leak that affected every badge in the app.
  const clampStyle: CSSProperties = maxWidth
    ? { maxWidth, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', verticalAlign: 'middle' }
    : {}

  if (effectiveColor) {
    return (
      <span
        className="status-badge"
        style={{ ...badgeBaseStyle, ...clampStyle, backgroundColor: effectiveColor, color: getContrastColor(effectiveColor) }}
      >
        {status}
      </span>
    )
  }

  return <span className={`status-badge ${statusClass}`} style={{ ...badgeBaseStyle, ...clampStyle }}>{status}</span>
}
