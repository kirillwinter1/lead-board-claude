import { StatusBadge } from './board/StatusBadge'
import { formatDuration, type StatusHistory } from '../api/statusHistory'
import {
  TEXT_PRIMARY, TEXT_MUTED, LINK_COLOR, SEPARATOR,
  TOOLTIP_VALUE, TOOLTIP_LABEL, TOOLTIP_HIGHLIGHT, TOOLTIP_DIVIDER,
} from '../constants/colors'

interface StatusPathContentProps {
  history: StatusHistory
  variant: 'light' | 'dark'
  // The "Excl. <first status>" row compensates for time spent in the initial (backlog)
  // status. Callers that pre-filter those segments out (Timeline status mode) pass false.
  showExcl?: boolean
}

// F92 — segment list + Total/Excl summary shared by the (light) Board status-age
// tooltip (StatusHistoryTooltip) and the (dark) Timeline story-bar tooltip in
// Story-statuses mode. Text colors switch by variant; StatusBadge always renders
// with its own configured background regardless of the surrounding tooltip theme.
export function StatusPathContent({ history, variant, showExcl = true }: StatusPathContentProps) {
  const colors = variant === 'dark'
    ? { primary: TOOLTIP_VALUE, muted: TOOLTIP_LABEL, link: TOOLTIP_HIGHLIGHT, separator: TOOLTIP_DIVIDER }
    : { primary: TEXT_PRIMARY, muted: TEXT_MUTED, link: LINK_COLOR, separator: SEPARATOR }

  return (
    <>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {history.segments.map((seg, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 8,
              opacity: seg.current ? 1 : 0.9,
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
              <StatusBadge status={seg.status} />
              {seg.current && (
                <span style={{ fontSize: 10, color: colors.link, fontWeight: 600, whiteSpace: 'nowrap' }}>
                  now
                </span>
              )}
            </span>
            <span
              style={{
                color: colors.primary,
                fontWeight: seg.current ? 700 : 500,
                whiteSpace: 'nowrap',
              }}
            >
              {formatDuration(seg.durationSeconds)}
            </span>
          </div>
        ))}
      </div>
      <div
        style={{
          borderTop: `1px solid ${colors.separator}`,
          marginTop: 8,
          paddingTop: 6,
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          color: colors.muted,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>Total</span>
          <span style={{ fontWeight: 600, color: colors.primary }}>{formatDuration(history.totalSeconds)}</span>
        </div>
        {showExcl && history.segments.length > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>Excl. “{history.segments[0].status}”</span>
            <span style={{ fontWeight: 600, color: colors.primary }}>
              {formatDuration(history.totalSeconds - history.segments[0].durationSeconds)}
            </span>
          </div>
        )}
      </div>
    </>
  )
}
