import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import type { StoryExpectedDoneCellProps } from './types'

export function StoryExpectedDoneCell({ endDate, assignee, storyPlanning }: StoryExpectedDoneCellProps) {
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
    if (!start && !end) return '—'
    if (!start) return `→ ${formatDate(end!)}`
    if (!end) return `${formatDate(start)} →`
    return `${formatDate(start)} → ${formatDate(end)}`
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
            {storyPlanning.phases.sa && (
              <div className="forecast-phase">
                <span className="phase-label sa">SA</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.sa.startDate, storyPlanning.phases.sa.endDate)}</span>
                {storyPlanning.phases.sa.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.sa.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {storyPlanning.phases.dev && (
              <div className="forecast-phase">
                <span className="phase-label dev">DEV</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.dev.startDate, storyPlanning.phases.dev.endDate)}</span>
                {storyPlanning.phases.dev.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.dev.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {storyPlanning.phases.qa && (
              <div className="forecast-phase">
                <span className="phase-label qa">QA</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.qa.startDate, storyPlanning.phases.qa.endDate)}</span>
                {storyPlanning.phases.qa.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.qa.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {!storyPlanning.phases.sa && !storyPlanning.phases.dev && !storyPlanning.phases.qa && (
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
