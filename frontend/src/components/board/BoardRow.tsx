import { isEpic, getIssueIcon } from './helpers'
import { ProgressCell } from './ProgressCell'
import { RoleChips } from './RoleChips'
import { PriorityCell } from './PriorityCell'
import { StatusBadge } from './StatusBadge'
import { ExpectedDoneCell } from './ExpectedDoneCell'
import { StoryExpectedDoneCell } from './StoryExpectedDoneCell'
import { AlertIcon } from './AlertIcon'
import { TeamBadge } from '../TeamBadge'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import type { BoardRowProps } from './types'

export function BoardRow({ node, level, expanded, onToggle, hasChildren, roughEstimateConfig, onRoughEstimateUpdate, forecast, canReorder, isJustDropped, actualPosition, recommendedPosition, dragHandleProps, storyPlanning }: BoardRowProps) {
  const { getIssueTypeIconUrl } = useWorkflowConfig()
  const isEpicRow = isEpic(node.issueType) && level === 0
  const isStoryRow = (node.issueType === 'Story' || node.issueType === 'История' || node.issueType === 'Bug' || node.issueType === 'Баг') && level === 1

  const justDroppedEffects = isJustDropped ? 'just-dropped' : ''

  return (
    <div className={`board-row level-${level} ${node.flagged ? 'flagged' : ''} ${justDroppedEffects}`}>
      <div className="cell cell-expander">
        {hasChildren ? (
          <button
            className={`expander-btn ${expanded ? 'expanded' : ''}`}
            onClick={onToggle}
          >
            <svg className="expander-icon" width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M6 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        ) : (
          <span className="expander-placeholder" />
        )}
      </div>
      <div className="cell cell-name">
        <div
          className="name-content"
          style={{
            paddingLeft: `${level * 20}px`,
          }}
        >
          {(isEpicRow || isStoryRow) && canReorder && dragHandleProps && (
            <span
              className="drag-handle"
              title={isStoryRow ? "Drag to reorder within epic" : "Drag to reorder"}
              style={{ cursor: 'grab', touchAction: 'none' }}
              {...dragHandleProps}
            >⋮⋮</span>
          )}
          <img src={getIssueIcon(node.issueType, getIssueTypeIconUrl(node.issueType))} alt={node.issueType} className="issue-type-icon" />
          <a href={node.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
            {node.issueKey}
          </a>
          {node.flagged && <span className="flag-indicator" title="Flagged — work paused">🚩</span>}
          {node.parentProjectKey && (
            <span style={{
              fontSize: 10,
              padding: '1px 5px',
              borderRadius: 3,
              background: '#DEEBFF',
              color: '#0747A6',
              fontWeight: 500,
              whiteSpace: 'nowrap',
              lineHeight: '16px',
            }}>
              {node.parentProjectKey}
            </span>
          )}
          <span className="issue-title">{node.title}</span>
        </div>
      </div>
      <div className="cell cell-team"><TeamBadge name={node.teamName} color={node.teamColor} /></div>
      <div className="cell cell-priority">
        {node.autoScore !== null && node.autoScore !== undefined ? (
          <PriorityCell
            node={node}
            recommendedPosition={recommendedPosition}
            actualPosition={actualPosition}
          />
        ) : (
          <span className="priority-empty">--</span>
        )}
      </div>
      <div className="cell cell-expected-done">
        {isEpic(node.issueType) ? (
          <ExpectedDoneCell forecast={forecast} />
        ) : (
          <StoryExpectedDoneCell endDate={node.expectedDone} assignee={node.assigneeDisplayName} storyPlanning={storyPlanning || null} />
        )}
      </div>
      <div className="cell cell-progress">
        <ProgressCell
          loggedSeconds={node.loggedSeconds}
          estimateSeconds={node.estimateSeconds}
          progress={node.progress}
        />
      </div>
      <div className="cell cell-roles">
        <RoleChips
          node={node}
          config={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
        />
      </div>
      <div className="cell cell-status">
        <StatusBadge status={node.status} />
      </div>
      <div className="cell cell-alerts">
        <AlertIcon node={node} />
      </div>
    </div>
  )
}
