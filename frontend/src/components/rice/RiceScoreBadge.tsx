import { BG_SUBTLE, DSR_GREEN, WARNING_ORANGE, TEXT_MUTED, TEXT_SUBTLE } from '../../constants/colors'

export function RiceScoreBadge({ score, normalized }: { score: number | null; normalized?: number | null }) {
  if (score == null) {
    return (
      <span style={{
        fontSize: 11,
        color: TEXT_SUBTLE,
        background: BG_SUBTLE,
        padding: '2px 6px',
        borderRadius: 3,
        fontWeight: 500,
      }}>
        RICE: —
      </span>
    )
  }

  const displayValue = normalized != null ? normalized : score
  const color = displayValue >= 70 ? DSR_GREEN : displayValue >= 40 ? WARNING_ORANGE : TEXT_MUTED

  return (
    <span style={{
      fontSize: 11,
      color: '#fff',
      background: color,
      padding: '2px 6px',
      borderRadius: 3,
      fontWeight: 600,
    }}
    title={`Raw: ${score.toFixed(1)}${normalized != null ? ` | Normalized: ${normalized.toFixed(0)}/100` : ''}`}
    >
      RICE: {normalized != null ? `${normalized.toFixed(0)}` : score.toFixed(1)}
    </span>
  )
}
