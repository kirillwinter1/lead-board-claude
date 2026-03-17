import { useState, useEffect } from 'react'
import { useMetricsFilter } from '../../contexts/MetricsFilterContext'
import { getMetricsDataStatus, MetricsDataStatus } from '../../api/metrics'
import { TEXT_MUTED, TEXT_PRIMARY, DSR_GREEN, DSR_YELLOW, LINK_COLOR, BORDER_DEFAULT, BG_PANEL } from '../../constants/colors'

export function DataStatusBar() {
  const { teamId, from, to } = useMetricsFilter()
  const [status, setStatus] = useState<MetricsDataStatus | null>(null)

  useEffect(() => {
    if (!teamId) return
    getMetricsDataStatus(teamId, from, to).then(setStatus).catch(() => setStatus(null))
  }, [teamId, from, to])

  if (!status) return null

  const syncAgo = status.lastSyncCompletedAt
    ? formatTimeAgo(new Date(status.lastSyncCompletedAt))
    : 'never'

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 16,
      padding: '6px 16px',
      background: BG_PANEL,
      borderRadius: 6,
      border: `1px solid ${BORDER_DEFAULT}`,
      fontSize: 12,
      color: TEXT_MUTED,
    }}>
      <span>
        Last sync: <strong style={{ color: status.syncInProgress ? LINK_COLOR : TEXT_PRIMARY }}>
          {status.syncInProgress ? 'in progress...' : syncAgo}
        </strong>
      </span>
      <span style={{ color: BORDER_DEFAULT }}>|</span>
      <span>{status.issuesInScope} issues in scope</span>
      <span style={{ color: BORDER_DEFAULT }}>|</span>
      <span>
        Coverage: <strong style={{ color: status.dataCoveragePercent >= 80 ? DSR_GREEN : DSR_YELLOW }}>
          {status.dataCoveragePercent}%
        </strong>
      </span>
    </div>
  )
}

function formatTimeAgo(date: Date): string {
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}
