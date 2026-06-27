import { useDroppable } from '@dnd-kit/core'
import { MatrixCard } from './MatrixCard'
import { QUADRANT_UNASSIGNED } from '../../constants/colors'
import type { MatrixCard as MatrixCardData } from '../../api/matrixApi'

// Droppable id for the unassigned zone. Dropping here clears the card's quadrant (null).
export const UNASSIGNED_ZONE_ID = 'unassigned'

interface MatrixUnassignedProps {
  cards: MatrixCardData[]
  jiraBaseUrl: string
}

export function MatrixUnassigned({ cards, jiraBaseUrl }: MatrixUnassignedProps) {
  const { setNodeRef, isOver } = useDroppable({ id: UNASSIGNED_ZONE_ID })
  const color = QUADRANT_UNASSIGNED

  return (
    <div
      ref={setNodeRef}
      className={`matrix-zone matrix-zone-unassigned ${isOver ? 'matrix-zone-over' : ''}`}
      style={{ background: color.bg, borderColor: color.accent }}
      data-testid="matrix-zone-unassigned"
    >
      <div className="matrix-zone-header" style={{ borderLeftColor: color.accent }}>
        <div className="matrix-zone-titles">
          <span className="matrix-zone-title">Нераспределённые</span>
          <span className="matrix-zone-subtitle">Перетащите карточку в квадрант</span>
        </div>
        <span className="matrix-zone-count">{cards.length}</span>
      </div>
      <div className="matrix-zone-cards matrix-zone-cards-row">
        {cards.map(card => (
          <MatrixCard key={card.issueKey} card={card} jiraBaseUrl={jiraBaseUrl} />
        ))}
      </div>
    </div>
  )
}
