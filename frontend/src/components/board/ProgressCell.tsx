import { formatCompact } from './helpers'
import type { ProgressCellProps } from './types'

export function ProgressCell({ loggedSeconds, estimateSeconds, progress }: ProgressCellProps) {
  const loggedDays = (loggedSeconds || 0) / 3600 / 8
  const estimateDays = (estimateSeconds || 0) / 3600 / 8
  const remainingDays = Math.max(0, estimateDays - loggedDays)
  const progressValue = progress || 0
  const isComplete = progressValue >= 100

  return (
    <div className="unified-progress-cell">
      <div className="unified-progress-header">
        <span className={`unified-progress-percent ${isComplete ? 'complete' : ''}`}>
          {Math.round(progressValue)}%
        </span>
        <span className="unified-progress-remaining">
          {formatCompact(remainingDays)}d
        </span>
      </div>
      <div className="unified-progress-bar">
        <div
          className={`unified-progress-fill ${isComplete ? 'complete' : ''}`}
          style={{ width: `${Math.min(progressValue, 100)}%` }}
        />
      </div>
      <div className="unified-progress-times">
        <span className="time-logged">{formatCompact(loggedDays)}</span>
        <span className="time-arrow">→</span>
        <span className="time-estimate">{formatCompact(estimateDays)}</span>
      </div>
    </div>
  )
}
