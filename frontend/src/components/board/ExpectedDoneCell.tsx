import { useTooltipPosition } from '../../hooks/useTooltipPosition'
import type { ExpectedDoneCellProps } from './types'

export function ExpectedDoneCell({ forecast }: ExpectedDoneCellProps) {
  const { ref, showTooltip, tooltipPos, handleMouseEnter, handleMouseLeave } = useTooltipPosition<HTMLDivElement>({
    tooltipWidth: 280,
    minSpaceNeeded: 200,
  })

  if (!forecast) {
    return <span className="expected-done-empty">--</span>
  }

  const { expectedDone, confidence, dueDateDeltaDays, dueDate, autoScore, remainingByRole, phaseSchedule } = forecast

  // Format date as "15 мар"
  const formatDate = (dateStr: string | null): string => {
    if (!dateStr) return '--'
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  // Format date range
  const formatDateRange = (start: string | null, end: string | null): string => {
    if (!start || !end) return '--'
    return `${formatDate(start)} - ${formatDate(end)}`
  }

  // Confidence colors
  const confidenceClass = confidence.toLowerCase()
  const confidenceLabels = {
    HIGH: 'Высокая',
    MEDIUM: 'Средняя',
    LOW: 'Низкая'
  }

  // Delta indicator
  let deltaClass = ''
  let deltaText = ''
  if (dueDateDeltaDays !== null) {
    if (dueDateDeltaDays > 0) {
      deltaClass = 'delta-late'
      deltaText = `+${dueDateDeltaDays}d`
    } else if (dueDateDeltaDays < 0) {
      deltaClass = 'delta-early'
      deltaText = `${dueDateDeltaDays}d`
    } else {
      deltaClass = 'delta-ontime'
      deltaText = 'on time'
    }
  }

  return (
    <div
      ref={ref}
      className="expected-done-cell"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <span className={`confidence-dot ${confidenceClass}`} />
      <span className="expected-done-date">{formatDate(expectedDone)}</span>
      {deltaText && (
        <span className={`expected-done-delta ${deltaClass}`}>
          {deltaText}
        </span>
      )}

      {showTooltip && tooltipPos && (
        <div
          className="forecast-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="forecast-tooltip-header">
            <span>AutoScore: <strong>{autoScore.toFixed(1)}</strong></span>
            <span className={`confidence-badge ${confidenceClass}`}>
              {confidenceLabels[confidence]}
            </span>
          </div>

          <div className="forecast-tooltip-section">
            <div className="forecast-tooltip-title">Расписание фаз</div>
            <div className="forecast-phase">
              <span className="phase-label sa">SA</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.sa?.startDate, phaseSchedule?.sa?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.sa?.days?.toFixed(1) || 0}d)</span>
            </div>
            <div className="forecast-phase">
              <span className="phase-label dev">DEV</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.dev?.startDate, phaseSchedule?.dev?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.dev?.days?.toFixed(1) || 0}d)</span>
            </div>
            <div className="forecast-phase">
              <span className="phase-label qa">QA</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.qa?.startDate, phaseSchedule?.qa?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.qa?.days?.toFixed(1) || 0}d)</span>
            </div>
          </div>

          {dueDate && (
            <div className="forecast-tooltip-footer">
              Due date: {formatDate(dueDate)}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
