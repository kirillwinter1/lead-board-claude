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
    ERROR: 'ОШИБКА',
    WARNING: 'ПРЕДУПРЕЖДЕНИЕ',
    INFO: 'ИНФО'
  }

  // Severity icons
  const severityIcons: Record<string, string> = {
    ERROR: '🔴',
    WARNING: '🟡',
    INFO: '🔵'
  }

  // Human-readable Russian rule names
  const ruleLabels: Record<string, string> = {
    TIME_LOGGED_WRONG_EPIC_STATUS: 'Списание времени при неверном статусе эпика',
    TIME_LOGGED_NOT_IN_SUBTASK: 'Время списано не в подзадачу',
    CHILD_IN_PROGRESS_EPIC_NOT: 'Дочерняя задача в работе, эпик — нет',
    SUBTASK_IN_PROGRESS_STORY_NOT: 'Подзадача в работе, стори — нет',
    EPIC_NO_ESTIMATE: 'Эпик без оценки',
    SUBTASK_NO_ESTIMATE: 'Подзадача без оценки',
    SUBTASK_WORK_NO_ESTIMATE: 'Списано время без оценки',
    SUBTASK_OVERRUN: 'Превышение оценки подзадачи',
    EPIC_NO_TEAM: 'Эпик без команды',
    EPIC_TEAM_NO_MEMBERS: 'Команда эпика без участников',
    EPIC_NO_DUE_DATE: 'Эпик без дедлайна',
    EPIC_OVERDUE: 'Эпик просрочен',
    EPIC_FORECAST_LATE: 'Прогноз позже дедлайна',
    EPIC_DONE_OPEN_CHILDREN: 'Эпик закрыт, есть открытые дочерние',
    STORY_DONE_OPEN_CHILDREN: 'Стори закрыта, есть открытые подзадачи',
    EPIC_IN_PROGRESS_NO_STORIES: 'Эпик в работе без сторей',
    STORY_IN_PROGRESS_NO_SUBTASKS: 'Стори в работе без подзадач',
    STORY_NO_SUBTASK_ESTIMATES: 'Стори без оценок в подзадачах',
    STORY_BLOCKED_BY_MISSING: 'Блокировщик не найден',
    STORY_CIRCULAR_DEPENDENCY: 'Циклическая зависимость',
    STORY_BLOCKED_NO_PROGRESS: 'Блокировка без прогресса >30 дней',
    SUBTASK_DONE_NO_TIME_LOGGED: 'Подзадача закрыта без списания времени',
    SUBTASK_TIME_LOGGED_BUT_TODO: 'Списано время, но подзадача в TODO',
    SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED: 'Списано время, но эпик приостановлен',
    RICE_MISSING_ASSESSMENT: 'Нет RICE-оценки',
    BUG_SLA_BREACH: 'Баг превысил SLA',
    BUG_STALE: 'Баг без обновлений >14 дней',
    STORY_FULLY_LOGGED_NOT_DONE: 'Всё время списано, но задача не закрыта',
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
            <strong>Проблемы качества данных ({count})</strong>
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
