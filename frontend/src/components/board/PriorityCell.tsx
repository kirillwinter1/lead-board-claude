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

  // Indicators
  const indicators: { key: string; label: string; color: string; bg: string; text: string }[] = []
  if (isBug(node.issueType)) {
    indicators.push({ key: 'bug', label: 'BUG', color: '#de350b', bg: '#ffebe6', text: 'Bug' })
  }
  if (node.blockedBy && node.blockedBy.length > 0) {
    indicators.push({ key: 'blocked', label: 'BLK', color: '#6b778c', bg: '#f4f5f7', text: 'Blocked by other issues' })
  }
  if (node.flagged) {
    indicators.push({ key: 'flagged', label: 'FLG', color: '#ff5630', bg: '#ffebe6', text: 'Work paused' })
  }
  if (score > 80) {
    indicators.push({ key: 'high', label: 'HI', color: '#36b37e', bg: '#e3fcef', text: 'High priority (>80)' })
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

  // Factor labels
  const factorLabels: Record<string, string> = {
    issueType: 'Issue Type',
    status: 'Status',
    progress: 'Progress',
    priority: 'Priority',
    dependency: 'Dependencies',
    dueDate: 'Due Date',
    estimateQuality: 'Estimate Quality',
    flagged: 'Flag',
    statusWeight: 'Status',
    storyCompletion: 'Story Completion',
    dueDateWeight: 'Due Date',
    size: 'Size',
    age: 'Age',
    riceBoost: 'RICE Score',
    alignmentBoost: 'Alignment',
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
      {indicators.length > 0 && (
        <span className="priority-icons" style={{ marginLeft: '4px', display: 'inline-flex', gap: '3px' }}>
          {indicators.map(ind => (
            <span
              key={ind.key}
              style={{
                fontSize: 9,
                fontWeight: 700,
                lineHeight: '16px',
                padding: '0 4px',
                borderRadius: 3,
                color: ind.color,
                backgroundColor: ind.bg,
                whiteSpace: 'nowrap',
              }}
            >
              {ind.label}
            </span>
          ))}
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
            Total Score: <strong>{breakdown?.totalScore != null ? breakdown.totalScore.toFixed(1) : score.toFixed(1)}</strong>
          </div>

          {recommendedPosition !== undefined && actualPosition !== undefined && (
            <div className="priority-tooltip-recommendation">
              Recommended position: <strong>{recommendedPosition}</strong>
              {actualPosition !== recommendedPosition && (
                <span className="current-position"> (current: {actualPosition})</span>
              )}
            </div>
          )}

          {loading && (
            <div className="priority-tooltip-loading">Loading...</div>
          )}

          {loadError && (
            <div className="priority-tooltip-loading" style={{ color: '#de350b' }}>Failed to load</div>
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

          {indicators.length > 0 && (
            <div className="priority-tooltip-indicators">
              <div className="priority-tooltip-title">Indicators:</div>
              {indicators.map(ind => (
                <div key={ind.key} className="priority-indicator-item">
                  <span
                    className="indicator-icon"
                    style={{
                      fontSize: 9,
                      fontWeight: 700,
                      padding: '0 4px',
                      borderRadius: 3,
                      color: ind.color,
                      backgroundColor: ind.bg,
                    }}
                  >
                    {ind.label}
                  </span>
                  <span className="indicator-description">{ind.text}</span>
                </div>
              ))}
            </div>
          )}
        </div>,
        document.body
      )}
    </div>
  )
}
