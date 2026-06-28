import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../board/helpers'
import { QUADRANT_COLORS } from '../../constants/colors'
import type { RecCard, RecommendationView, RoleRecommendation } from '../../api/matrixApi'
import './MatrixRecommendations.css'

interface Props {
  data: RecommendationView | null
  jiraBaseUrl: string
}

export function MatrixRecommendations({ data, jiraBaseUrl }: Props) {
  if (!data) return null
  const { zeroBugPolicy, roles } = data

  return (
    <section className="matrix-recommendations">
      <ZeroBugSection bugs={zeroBugPolicy.bugs} count={zeroBugPolicy.openBugCount} jiraBaseUrl={jiraBaseUrl} />

      {roles.length === 0 ? (
        <div className="rec-empty">Все роли загружены — рекомендаций нет</div>
      ) : (
        roles.map(role => (
          <RoleSection key={role.roleCode} role={role} jiraBaseUrl={jiraBaseUrl} />
        ))
      )}
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
        {bugs.map(b => <RecCardView key={b.issueKey} card={b} jiraBaseUrl={jiraBaseUrl} />)}
      </div>
    </div>
  )
}

function RoleSection({ role, jiraBaseUrl }: { role: RoleRecommendation; jiraBaseUrl: string }) {
  const { getRoleColor, getRoleDisplayName } = useWorkflowConfig()
  const accent = getRoleColor(role.roleCode)
  return (
    <div className="rec-block" style={{ borderLeftColor: accent }}>
      <div className="rec-block-head" style={{ color: accent }}>
        {getRoleDisplayName(role.roleCode)} — простой {role.idleHours}ч
      </div>

      <div className="rec-subhead">Готово взять</div>
      {role.ready.length === 0 ? (
        <div className="rec-empty-line">—</div>
      ) : (
        <div className="rec-cards">
          {role.ready.map(c => (
            <RecCardView key={c.issueKey} card={c} jiraBaseUrl={jiraBaseUrl} dimmed={c.fitsInIdle === false} />
          ))}
        </div>
      )}

      <div className="rec-subhead rec-subhead-warn">Требует оценки</div>
      {role.needsEstimation.length === 0 ? (
        <div className="rec-empty-line">—</div>
      ) : (
        <div className="rec-cards">
          {role.needsEstimation.map(c => (
            <RecCardView key={c.issueKey} card={c} jiraBaseUrl={jiraBaseUrl} warn />
          ))}
        </div>
      )}
    </div>
  )
}

function RecCardView({ card, jiraBaseUrl, dimmed, warn }: { card: RecCard; jiraBaseUrl: string; dimmed?: boolean; warn?: boolean }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory, getRoleColor } = useWorkflowConfig()
  const quadrant = card.quadrant
  const roleHours = card.roleEstimateHours != null ? `${card.roleEstimateHours}ч` : null
  const estimateLabel = card.estimateHours != null ? `${card.estimateHours}ч` : 'не оценён'

  return (
    <div className={`rec-card ${dimmed ? 'rec-card-dimmed' : ''} ${warn ? 'rec-card-warn' : ''}`}>
      <div className="rec-card-head">
        <img
          src={getIssueIcon(card.issueType, getIssueTypeIconUrl(card.issueType), getIssueTypeCategory(card.issueType))}
          alt={card.issueType}
          className="rec-card-type-icon"
        />
        <a
          href={`${jiraBaseUrl}${card.issueKey}`}
          target="_blank"
          rel="noopener noreferrer"
          className="rec-card-key"
        >
          {card.issueKey}
        </a>
        {quadrant && (
          <span className="rec-card-quadrant" style={{ background: QUADRANT_COLORS[quadrant].accent }}>
            {quadrant}
          </span>
        )}
        {card.workflowRole && (
          <span className="rec-card-role" style={{ background: getRoleColor(card.workflowRole) }}>
            {card.workflowRole}
          </span>
        )}
      </div>
      <div className="rec-card-summary" title={card.summary}>{card.summary}</div>
      <div className="rec-card-meta">
        <span>{roleHours ?? estimateLabel}</span>
        {card.cumulativeHours != null && <span className="rec-card-cumulative">Σ {card.cumulativeHours}ч</span>}
      </div>
    </div>
  )
}
