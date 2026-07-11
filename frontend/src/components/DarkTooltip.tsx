import type { ReactNode, CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import {
  TOOLTIP_BG, TOOLTIP_HIGHLIGHT, TOOLTIP_TEXT, TOOLTIP_LABEL, TOOLTIP_VALUE,
  TOOLTIP_DIVIDER, TOOLTIP_PROGRESS_TRACK, TOOLTIP_SUCCESS, TOOLTIP_DANGER,
} from '../constants/colors'

// F91 — the single dark-tooltip shell. Portal-rendered, position:fixed, navy palette
// from the TOOLTIP_* tokens. Used by the Timeline (EpicLabel, StoryBars, RoughEstimateBars);
// callers pass ready fixed coordinates and compose content from the sub-components below.
//
// NOTE: the light hover-cards on the Board (IssueTooltip / ProjectTooltip / TooltipIssueHeader,
// all built on HoverInfoCard, plus StatusHistoryTooltip) are a DELIBERATELY SEPARATE pattern —
// white background, anchored to the trigger, lazy data loading — and must NOT use this component.
// The small CSS-anchored navy tooltips with arrow pointers (MyWorklogCalendar, AbsenceTimeline)
// are also a distinct pattern (anchored + ::after arrow, not portal/fixed-coordinate).

interface DarkTooltipProps {
  top: number
  left: number
  minWidth?: number
  maxWidth?: number
  interactive?: boolean
  children: ReactNode
}

// Keep the tooltip within the viewport horizontally. maxWidth is the worst-case width,
// so clamping against it guarantees the box never overflows the right edge.
function clampLeft(left: number, maxWidth: number): number {
  if (typeof window === 'undefined') return left
  const margin = 8
  return Math.max(margin, Math.min(left, window.innerWidth - maxWidth - margin))
}

export function DarkTooltip({
  top, left, minWidth = 300, maxWidth = 420, interactive = false, children,
}: DarkTooltipProps) {
  return createPortal(
    <div
      style={{
        position: 'fixed',
        top,
        left: clampLeft(left, maxWidth),
        zIndex: 10000,
        pointerEvents: interactive ? 'auto' : 'none',
        background: TOOLTIP_BG,
        borderRadius: 8,
        padding: 14,
        minWidth,
        maxWidth,
        boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
        color: TOOLTIP_TEXT,
        fontSize: 13,
      }}
    >
      {children}
    </div>,
    document.body,
  )
}

// Emphasised key / heading text (issue key, epic key). Highlight blue, semibold.
function Title({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <span style={{ color: TOOLTIP_HIGHLIGHT, fontWeight: 600, ...style }}>{children}</span>
}

// Muted secondary text (field labels, dates, autoscore).
function Label({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <span style={{ color: TOOLTIP_LABEL, ...style }}>{children}</span>
}

// Foreground value text (hours, counts).
function Value({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <span style={{ color: TOOLTIP_VALUE, ...style }}>{children}</span>
}

// Section separator.
function Divider({ style }: { style?: CSSProperties }) {
  return <div style={{ borderTop: `1px solid ${TOOLTIP_DIVIDER}`, marginTop: 10, marginBottom: 10, ...style }} />
}

// Mini progress bar. Green fill up to max, red when value exceeds max (over-logged).
function Progress({ value, max, style }: { value: number; max: number; style?: CSSProperties }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  const over = value > max
  return (
    <div style={{ width: '100%', height: 6, background: TOOLTIP_PROGRESS_TRACK, borderRadius: 3, overflow: 'hidden', ...style }}>
      <div style={{ width: `${pct}%`, height: '100%', background: over ? TOOLTIP_DANGER : TOOLTIP_SUCCESS, borderRadius: 3 }} />
    </div>
  )
}

DarkTooltip.Title = Title
DarkTooltip.Label = Label
DarkTooltip.Value = Value
DarkTooltip.Divider = Divider
DarkTooltip.Progress = Progress
