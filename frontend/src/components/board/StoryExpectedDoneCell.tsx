import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import type { StoryExpectedDoneCellProps } from './types'

export function StoryExpectedDoneCell({ endDate, assignee, storyPlanning }: StoryExpectedDoneCellProps) {
  const { getRoleColor } = useWorkflowConfig()
  const { ref, showTooltip, tooltipPos, handleMouseEnter, handleMouseLeave } = useTooltipPosition<HTMLDivElement>({
    tooltipWidth: 280,
    minSpaceNeeded: 180,
  })

  if (!endDate) {
    return <span className="expected-done-empty">--</span>
  }

  const formatDate = (dateStr: string): string => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  const formatDateRange = (start: string | null | undefined, end: string | null | undefined): string => {
    if (!start && !end) return '\u2014'
    if (!start) return `\u2192 ${formatDate(end!)}`
    if (!end) return `${formatDate(start)} \u2192`
    return `${formatDate(start)} \u2192 ${formatDate(end)}`
  }

  return (
    <div
      ref={ref}
      className="expected-done-cell story-expected-done"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className="expected-done-date">{formatDate(endDate)}</span>
      {assignee && (
        <span className="expected-done-assignee">{assignee}</span>
      )}

      {showTooltip && tooltipPos && storyPlanning && (
        <div
          className="forecast-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="forecast-tooltip-header">
            <span><strong>{storyPlanning.storyKey}</strong></span>
            {storyPlanning.autoScore !== null && (
              <span>AutoScore: <strong>{storyPlanning.autoScore.toFixed(1)}</strong></span>
            )}
          </div>

          <div className="forecast-tooltip-section">
            <div className="forecast-tooltip-title">Расписание фаз</div>
            {Object.entries(storyPlanning.phases).map(([role, phase]) => phase && (
              <div key={role} className="forecast-phase">
                <span className="phase-label" style={{ color: getRoleColor(role) }}>{role}</span>
                <span className="phase-dates">{formatDateRange(phase.startDate, phase.endDate)}</span>
                {phase.assigneeDisplayName && (
                  <span className="phase-assignee">{phase.assigneeDisplayName}</span>
                )}
              </div>
            ))}
            {Object.keys(storyPlanning.phases).length === 0 && (
              <div className="forecast-phase" style={{ color: '#666' }}>Нет данных о фазах</div>
            )}
          </div>

          {storyPlanning.blockedBy && storyPlanning.blockedBy.length > 0 && (
            <div className="forecast-tooltip-footer" style={{ color: '#de350b' }}>
              Заблокировано: {storyPlanning.blockedBy.join(', ')}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
