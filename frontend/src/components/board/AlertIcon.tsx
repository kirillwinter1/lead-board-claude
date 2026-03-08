import { createPortal } from 'react-dom'
import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import type { BoardNode } from './types'

export function AlertIcon({ node }: { node: BoardNode }) {
  const { ref, showTooltip, tooltipPos, handleMouseEnter, handleMouseLeave } = useTooltipPosition<HTMLDivElement>({
    tooltipWidth: 320,
    minSpaceNeeded: 150,
  })

  const alerts = node.alerts || []

  if (alerts.length === 0) {
    return <span className="no-alert">--</span>
  }

  // Find the highest severity
  const hasError = alerts.some(a => a.severity === 'ERROR')
  const hasWarning = alerts.some(a => a.severity === 'WARNING')

  const severityClass = hasError ? 'error' : hasWarning ? 'warning' : 'info'
  const count = alerts.length

  // Severity labels
  const severityLabels: Record<string, string> = {
    ERROR: 'ERROR',
    WARNING: 'WARNING',
    INFO: 'INFO'
  }

  // Severity icons
  const severityIcons: Record<string, string> = {
    ERROR: '🔴',
    WARNING: '🟡',
    INFO: '🔵'
  }

  // Human-readable rule names
  const ruleLabels: Record<string, string> = {
    TIME_LOGGED_WRONG_EPIC_STATUS: 'Time logged on wrong epic status',
    TIME_LOGGED_NOT_IN_SUBTASK: 'Time logged not in a subtask',
    CHILD_IN_PROGRESS_EPIC_NOT: 'Child issue in progress, epic is not',
    SUBTASK_IN_PROGRESS_STORY_NOT: 'Subtask in progress, story is not',
    EPIC_NO_ESTIMATE: 'Epic without estimate',
    SUBTASK_NO_ESTIMATE: 'Subtask without estimate',
    SUBTASK_WORK_NO_ESTIMATE: 'Time logged without estimate',
    SUBTASK_OVERRUN: 'Subtask estimate exceeded',
    EPIC_NO_TEAM: 'Epic without team',
    EPIC_TEAM_NO_MEMBERS: 'Epic team has no members',
    EPIC_NO_DUE_DATE: 'Epic without due date',
    EPIC_OVERDUE: 'Epic overdue',
    EPIC_FORECAST_LATE: 'Forecast later than due date',
    EPIC_DONE_OPEN_CHILDREN: 'Epic closed with open children',
    STORY_DONE_OPEN_CHILDREN: 'Story closed with open subtasks',
    EPIC_IN_PROGRESS_NO_STORIES: 'Epic in progress without stories',
    STORY_IN_PROGRESS_NO_SUBTASKS: 'Story in progress without subtasks',
    STORY_NO_SUBTASK_ESTIMATES: 'Story without subtask estimates',
    STORY_BLOCKED_BY_MISSING: 'Blocker not found',
    STORY_CIRCULAR_DEPENDENCY: 'Circular dependency',
    STORY_BLOCKED_NO_PROGRESS: 'Blocked with no progress >30 days',
    SUBTASK_DONE_NO_TIME_LOGGED: 'Subtask closed without time logged',
    SUBTASK_TIME_LOGGED_BUT_TODO: 'Time logged but subtask is in TODO',
    SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED: 'Time logged while epic is flagged',
    RICE_MISSING_ASSESSMENT: 'Missing RICE assessment',
    BUG_SLA_BREACH: 'Bug exceeded SLA',
    BUG_STALE: 'Bug with no updates >14 days',
    STORY_FULLY_LOGGED_NOT_DONE: 'All time logged but issue not closed',
  }

  return (
    <div
      ref={ref}
      className="alert-icon-wrapper"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className={`alert-icon alert-${severityClass}`} role="img" aria-label={`${count} data quality alerts`}>
        {count}
      </span>

      {showTooltip && tooltipPos && createPortal(
        <div
          className="alert-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="alert-tooltip-header">
            <strong>Data quality issues ({count})</strong>
          </div>
          <div className="alert-tooltip-list">
            {alerts.map((alert, idx) => (
              <div key={idx} className={`alert-tooltip-item alert-${alert.severity.toLowerCase()}`}>
                <div className="alert-item-header">
                  <span className="alert-severity">
                    {severityIcons[alert.severity]} {severityLabels[alert.severity] || alert.severity}
                  </span>
                </div>
                <div className="alert-message">
                  {ruleLabels[alert.rule] || alert.rule}
                </div>
              </div>
            ))}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
