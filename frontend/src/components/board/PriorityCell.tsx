import { useState, useRef, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { getRecommendationIcon } from './helpers'
import { getScoreBreakdown } from '../../api/board'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import type { PriorityCellProps, ScoreBreakdown } from './types'

export function PriorityCell({ node, recommendedPosition, actualPosition }: PriorityCellProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [breakdown, setBreakdown] = useState<ScoreBreakdown | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<{ top: number; left: number; showAbove?: boolean } | null>(null)
  const cellRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { isBug } = useWorkflowConfig()
  const score = node.autoScore || 0

  // Color based on score
  let color = '#888' // gray for low
  if (score > 80) color = '#36b37e' // green for high
  else if (score >= 40) color = '#ffab00' // yellow for medium
  else if (score < 0) color = '#de350b' // red for negative (blocked)

  // Icons
  const icons: string[] = []
  if (isBug(node.issueType)) {
    icons.push('🐞')
  }
  if (node.blockedBy && node.blockedBy.length > 0) {
    icons.push('🔒')
  }
  if (node.flagged) {
    icons.push('🚩')
  }
  if (score > 80) {
    icons.push('⚡')
  }

  const hasNoEstimates = node.estimateSeconds === null || node.estimateSeconds === 0
  if (hasNoEstimates && !node.issueType?.toLowerCase().includes('epic') && !node.issueType?.toLowerCase().includes('эпик')) {
    icons.push('⚠️')
  }

  // Fetch breakdown with debounce to avoid unnecessary API calls on quick mouse-overs
  const fetchBreakdown = useCallback(async () => {
    if (breakdown) return
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    setLoadError(false)
    try {
      const data = await getScoreBreakdown(node.issueKey)
      if (!controller.signal.aborted) {
        setBreakdown(data)
      }
    } catch (err) {
      if (!controller.signal.aborted) {
        setLoadError(true)
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false)
      }
    }
  }, [node.issueKey, breakdown])

  // Show tooltip instantly, debounce API call
  const handleMouseEnter = () => {
    setShowTooltip(true)

    // Calculate tooltip position
    if (cellRef.current) {
      const rect = cellRef.current.getBoundingClientRect()
      const tooltipWidth = 260
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      const minSpaceNeeded = 150

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      if (spaceBelow >= minSpaceNeeded) {
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        top = rect.top - 8
      } else {
        top = rect.bottom + 8
      }

      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }

    // Debounce API call — 300ms delay so quick mouse-overs don't fire requests
    if (!breakdown && !loading) {
      debounceRef.current = setTimeout(fetchBreakdown, 300)
    }
  }

  // Factor labels in Russian
  const factorLabels: Record<string, string> = {
    issueType: 'Тип задачи',
    status: 'Статус',
    progress: 'Прогресс',
    priority: 'Приоритет',
    dependency: 'Зависимости',
    dueDate: 'Срок',
    estimateQuality: 'Качество оценки',
    flagged: 'Флаг',
    statusWeight: 'Статус',
    storyCompletion: 'Завершение историй',
    dueDateWeight: 'Срок'
  }

  return (
    <div
      ref={cellRef}
      className="priority-cell"
      style={{ color }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => { setShowTooltip(false); if (debounceRef.current) clearTimeout(debounceRef.current); abortRef.current?.abort() }}
    >
      <span className="priority-score" style={{ fontWeight: 600 }}>
        {score.toFixed(0)}
      </span>
      {actualPosition !== undefined && recommendedPosition !== undefined && (
        <span className={`recommendation-indicator ${getRecommendationIcon(actualPosition, recommendedPosition, node.autoScore).className}`}>
          {getRecommendationIcon(actualPosition, recommendedPosition, node.autoScore).icon}
        </span>
      )}
      {icons.length > 0 && (
        <span className="priority-icons" style={{ marginLeft: '6px' }}>
          {icons.join(' ')}
        </span>
      )}

      {showTooltip && tooltipPos && createPortal(
        <div
          className="priority-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none',
            pointerEvents: 'none'
          }}
        >
          <div className="priority-tooltip-header">
            <strong>{node.issueKey}</strong>
            <span className="priority-tooltip-type">{node.issueType}</span>
          </div>
          <div className="priority-tooltip-total">
            Total Score: <strong>{score.toFixed(1)}</strong>
          </div>

          {recommendedPosition !== undefined && actualPosition !== undefined && (
            <div className="priority-tooltip-recommendation">
              Рекомендуемая позиция: <strong>{recommendedPosition}</strong>
              {actualPosition !== recommendedPosition && (
                <span className="current-position"> (сейчас: {actualPosition})</span>
              )}
            </div>
          )}

          {loading && (
            <div className="priority-tooltip-loading">Загрузка...</div>
          )}

          {loadError && (
            <div className="priority-tooltip-loading" style={{ color: '#de350b' }}>Ошибка загрузки</div>
          )}

          {breakdown && breakdown.breakdown && (
            <div className="priority-tooltip-breakdown">
              <div className="priority-tooltip-title">Breakdown:</div>
              {Object.entries(breakdown.breakdown)
                .filter(([_, value]) => value !== 0)
                .sort(([_a, a], [_b, b]) => Math.abs(b) - Math.abs(a))
                .map(([factor, value]) => (
                  <div key={factor} className="priority-breakdown-item">
                    <span className="factor-name">{factorLabels[factor] || factor}</span>
                    <span className={`factor-value ${value > 0 ? 'positive' : value < 0 ? 'negative' : ''}`}>
                      {value > 0 ? '+' : ''}{value.toFixed(1)}
                    </span>
                  </div>
                ))}
            </div>
          )}

          {icons.length > 0 && (
            <div className="priority-tooltip-indicators">
              <div className="priority-tooltip-title">Индикаторы:</div>
              {icons.map((icon, idx) => {
                let description = ''
                if (icon === '🐞') description = 'Баг'
                else if (icon === '🔒') description = 'Заблокировано другими задачами'
                else if (icon === '🚩') description = 'Работа приостановлена'
                else if (icon === '⚡') description = 'Высокий приоритет (>80)'
                else if (icon === '⚠️') description = 'Нет оценки времени'

                return (
                  <div key={idx} className="priority-indicator-item">
                    <span className="indicator-icon">{icon}</span>
                    <span className="indicator-description">{description}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>,
        document.body
      )}
    </div>
  )
}
