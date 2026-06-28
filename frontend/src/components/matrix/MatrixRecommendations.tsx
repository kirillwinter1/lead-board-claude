import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../board/helpers'
import { QUADRANT_COLORS } from '../../constants/colors'
import type { RecCard, RecommendationView, StoryRec } from '../../api/matrixApi'
import './MatrixRecommendations.css'

interface Props {
  data: RecommendationView | null
  jiraBaseUrl: string
}

// Hours come from seconds/3600 and can be long decimals; show at most 1 decimal,
// dropping a trailing ".0" (e.g. 16 -> "16", 1.6667 -> "1.7").
function fmtHours(n: number): string {
  return String(Math.round(n * 10) / 10)
}

export function MatrixRecommendations({ data, jiraBaseUrl }: Props) {
  if (!data) return null
  const { zeroBugPolicy, recommended = [], needsEstimation = [] } = data

  return (
    <section className="matrix-recommendations">
      <ZeroBugSection bugs={zeroBugPolicy.bugs} count={zeroBugPolicy.openBugCount} jiraBaseUrl={jiraBaseUrl} />

      <div className="rec-block">
        <div className="rec-block-head">Рекомендуем взять из техдолга</div>
        {recommended.length === 0 ? (
          <div className="rec-empty-line">Нет распределённых задач — разложите техдолг по квадрантам матрицы.</div>
        ) : (
          <div className="rec-cards">
            {recommended.map(s => <StoryCard key={s.issueKey} story={s} jiraBaseUrl={jiraBaseUrl} />)}
          </div>
        )}

        {needsEstimation.length > 0 && (
          <>
            <div className="rec-subhead rec-subhead-warn">Требует нарезки / оценки</div>
            <div className="rec-cards">
              {needsEstimation.map(c => <WarnCard key={c.issueKey} card={c} jiraBaseUrl={jiraBaseUrl} />)}
            </div>
          </>
        )}
      </div>
    </section>
  )
}

function ZeroBugSection({ bugs, count, jiraBaseUrl }: { bugs: RecCard[]; count: number; jiraBaseUrl: string }) {
  if (count === 0) {
    return (
      <div className="rec-block rec-zerobug rec-zerobug-clean" role="status">
        <div className="rec-block-head">🐞 Zero Bug Policy — 0 багов, политика соблюдается</div>
      </div>
    )
  }
  return (
    <div className="rec-block rec-zerobug" role="alert">
      <div className="rec-block-head">🐞 Zero Bug Policy — {count} открытых багов</div>
      <div className="rec-cards">
        {bugs.map(b => <WarnCard key={b.issueKey} card={b} jiraBaseUrl={jiraBaseUrl} bug />)}
      </div>
    </div>
  )
}

function StoryCard({ story, jiraBaseUrl }: { story: StoryRec; jiraBaseUrl: string }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory, getRoleColor } = useWorkflowConfig()
  const quadrant = story.quadrant

  return (
    <div className="rec-card">
      <div className="rec-card-head">
        <img
          src={getIssueIcon(story.issueType, getIssueTypeIconUrl(story.issueType), getIssueTypeCategory(story.issueType))}
          alt={story.issueType}
          className="rec-card-type-icon"
        />
        <a href={`${jiraBaseUrl}${story.issueKey}`} target="_blank" rel="noopener noreferrer" className="rec-card-key">
          {story.issueKey}
        </a>
        {quadrant && (
          <span className="rec-card-quadrant" style={{ background: QUADRANT_COLORS[quadrant].accent }}>
            {quadrant}
          </span>
        )}
      </div>
      <div className="rec-card-summary" title={story.summary}>{story.summary}</div>
      <div className="rec-card-roles">
        {story.roles.map(r => (
          <span key={r.roleCode} className="rec-role-chip" style={{ background: getRoleColor(r.roleCode) }}>
            {r.roleCode} {fmtHours(r.hours)}ч
          </span>
        ))}
      </div>
      <div className="rec-card-total">Всего {fmtHours(story.totalHours)}ч</div>
    </div>
  )
}

function WarnCard({ card, jiraBaseUrl, bug }: { card: RecCard; jiraBaseUrl: string; bug?: boolean }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory, getRoleColor } = useWorkflowConfig()
  const estimateLabel = card.estimateHours != null ? `${fmtHours(card.estimateHours)}ч` : 'не оценён'

  return (
    <div className={`rec-card ${bug ? '' : 'rec-card-warn'}`}>
      <div className="rec-card-head">
        <img
          src={getIssueIcon(card.issueType, getIssueTypeIconUrl(card.issueType), getIssueTypeCategory(card.issueType))}
          alt={card.issueType}
          className="rec-card-type-icon"
        />
        <a href={`${jiraBaseUrl}${card.issueKey}`} target="_blank" rel="noopener noreferrer" className="rec-card-key">
          {card.issueKey}
        </a>
        {card.quadrant && (
          <span className="rec-card-quadrant" style={{ background: QUADRANT_COLORS[card.quadrant].accent }}>
            {card.quadrant}
          </span>
        )}
        {card.workflowRole && (
          <span className="rec-role-chip" style={{ background: getRoleColor(card.workflowRole) }}>
            {card.workflowRole}
          </span>
        )}
      </div>
      <div className="rec-card-summary" title={card.summary}>{card.summary}</div>
      <div className="rec-card-meta"><span>{estimateLabel}</span></div>
    </div>
  )
}
