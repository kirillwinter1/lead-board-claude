export function RiceScoreBadge({ score, normalized }: { score: number | null; normalized?: number | null }) {
  if (score == null) {
    return (
      <span style={{
        fontSize: 11,
        color: '#97A0AF',
        background: '#F4F5F7',
        padding: '2px 6px',
        borderRadius: 3,
        fontWeight: 500,
      }}>
        RICE: —
      </span>
    )
  }

  const displayValue = normalized != null ? normalized : score
  const color = displayValue >= 70 ? '#36B37E' : displayValue >= 40 ? '#FF8B00' : '#6B778C'

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
