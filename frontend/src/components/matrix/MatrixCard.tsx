import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../board/helpers'
import { StatusAgeBadge } from '../StatusAgeBadge'
import type { MatrixCard as MatrixCardData } from '../../api/matrixApi'

interface MatrixCardProps {
  card: MatrixCardData
  jiraBaseUrl: string
}

export function MatrixCard({ card, jiraBaseUrl }: MatrixCardProps) {
  const { getIssueTypeIconUrl, getIssueTypeCategory, getPriorityIconUrl } = useWorkflowConfig()

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: card.issueKey,
  })

  const style: React.CSSProperties = {
    transform: CSS.Translate.toString(transform),
    opacity: isDragging ? 0.4 : 1,
    cursor: 'grab',
  }

  const priorityIconUrl = getPriorityIconUrl(card.priority)
  const estimateLabel = card.estimateHours != null ? `${card.estimateHours}h` : '—'

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="matrix-card"
      {...listeners}
      {...attributes}
    >
      <div className="matrix-card-head">
        <img
          src={getIssueIcon(card.issueType, getIssueTypeIconUrl(card.issueType), getIssueTypeCategory(card.issueType))}
          alt={card.issueType}
          className="matrix-card-type-icon"
        />
        <a
          href={`${jiraBaseUrl}${card.issueKey}`}
          target="_blank"
          rel="noopener noreferrer"
          className="matrix-card-key"
          onClick={e => e.stopPropagation()}
          onPointerDown={e => e.stopPropagation()}
        >
          {card.issueKey}
        </a>
        {priorityIconUrl && (
          <img
            src={priorityIconUrl}
            alt={card.priority ?? ''}
            title={card.priority ?? undefined}
            className="matrix-card-priority-icon"
          />
        )}
        <StatusAgeBadge days={card.daysInStatus} level={card.statusAgeLevel} reason={card.statusAgeReason} />
      </div>
      <div className="matrix-card-summary" title={card.summary}>{card.summary}</div>
      <div className="matrix-card-meta">
        <span className="matrix-card-estimate">{estimateLabel}</span>
        <span className="matrix-card-assignee">{card.assigneeDisplayName ?? '—'}</span>
      </div>
    </div>
  )
}
