import './CompetencyRating.css'

const LEVEL_LABELS: Record<number, string> = {
  1: 'No exp',
  2: 'Beginner',
  3: 'Competent',
  4: 'Proficient',
  5: 'Expert',
}

const LEVEL_COLORS: Record<number, string> = {
  1: '#dfe1e6',
  2: '#ffc400',
  3: '#36b37e',
  4: '#0065ff',
  5: '#6554c0',
}

interface CompetencyRatingProps {
  level: number
  onChange?: (level: number) => void
  readonly?: boolean
  showLabel?: boolean
}

export function CompetencyRating({ level, onChange, readonly = false, showLabel = false }: CompetencyRatingProps) {
  return (
    <div className="competency-rating">
      <div className="competency-dots">
        {[1, 2, 3, 4, 5].map(i => (
          <span
            key={i}
            className={`competency-dot ${i <= level ? 'filled' : ''} ${!readonly ? 'clickable' : ''}`}
            style={i <= level ? { backgroundColor: LEVEL_COLORS[level] } : undefined}
            onClick={() => !readonly && onChange?.(i)}
            title={LEVEL_LABELS[i]}
          />
        ))}
      </div>
      {showLabel && (
        <span className="competency-label" style={{ color: LEVEL_COLORS[level] }}>
          {LEVEL_LABELS[level]}
        </span>
      )}
    </div>
  )
}

export { LEVEL_LABELS, LEVEL_COLORS }
