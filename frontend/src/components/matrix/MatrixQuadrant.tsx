import { useDroppable } from '@dnd-kit/core'
import { MatrixCard } from './MatrixCard'
import { QUADRANT_COLORS } from '../../constants/colors'
import type { MatrixCard as MatrixCardData, Quadrant } from '../../api/matrixApi'

interface MatrixQuadrantProps {
  quadrant: Quadrant
  title: string
  subtitle: string
  cards: MatrixCardData[]
  jiraBaseUrl: string
}

export function MatrixQuadrant({ quadrant, title, subtitle, cards, jiraBaseUrl }: MatrixQuadrantProps) {
  const { setNodeRef, isOver } = useDroppable({ id: quadrant })
  const color = QUADRANT_COLORS[quadrant]

  return (
    <div
      ref={setNodeRef}
      className={`matrix-zone ${isOver ? 'matrix-zone-over' : ''}`}
      style={{
        background: color.bg,
        borderColor: color.accent,
      }}
      data-testid={`matrix-zone-${quadrant}`}
    >
      <div className="matrix-zone-header" style={{ borderLeftColor: color.accent }}>
        <span className="matrix-zone-badge" style={{ background: color.accent }}>{quadrant}</span>
        <div className="matrix-zone-titles">
          <span className="matrix-zone-title">{title}</span>
          <span className="matrix-zone-subtitle">{subtitle}</span>
        </div>
        <span className="matrix-zone-count">{cards.length}</span>
      </div>
      <div className="matrix-zone-cards">
        {cards.map(card => (
          <MatrixCard key={card.issueKey} card={card} jiraBaseUrl={jiraBaseUrl} />
        ))}
      </div>
    </div>
  )
}
