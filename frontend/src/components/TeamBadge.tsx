interface TeamBadgeProps {
  name: string | null
  color: string | null
}

export function TeamBadge({ name, color }: TeamBadgeProps) {
  if (!name) return <span style={{ color: '#999' }}>--</span>

  if (!color) {
    return <span>{name}</span>
  }

  return (
    <span
      title={name}
      style={{
        display: 'inline-block',
        maxWidth: '100%',
        padding: '2px 8px',
        borderRadius: 4,
        borderLeft: `3px solid ${color}`,
        backgroundColor: color + '14',
        color: color,
        fontSize: 12,
        fontWeight: 600,
        lineHeight: '18px',
        wordBreak: 'break-word',
      }}
    >
      {name}
    </span>
  )
}
