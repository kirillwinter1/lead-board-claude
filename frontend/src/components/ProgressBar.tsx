import { PROGRESS_COMPLETE, PROGRESS_IN_PROGRESS, PROGRESS_TRACK } from '../constants/colors'

interface ProgressBarProps {
  /** Current value. Clamped to [0, max]. */
  value: number
  /** Full-scale value. Default 100. */
  max?: number
  /**
   * Fill colour. Default: green (PROGRESS_COMPLETE) once value >= max,
   * otherwise blue (PROGRESS_IN_PROGRESS). Pass a colour to override — e.g.
   * ProjectCommitmentView drives the DSR palette off its own status logic.
   */
  color?: string
  /** Track (unfilled) colour. Default PROGRESS_TRACK. */
  trackColor?: string
  /** Bar height in px. Default 6. */
  height?: number
  /** Bar width — number (px) or CSS length. Default '100%'. */
  width?: number | string
  /** Required for a11y — describes what the bar measures. */
  ariaLabel: string
  /** Minimum fill width in px when value > 0, so a thin sliver stays visible. Default 2. */
  minFillPx?: number
}

/**
 * F91 — shared single-segment progress bar. Replaces the ad-hoc copies that
 * lived in ProjectsPage, ProjectCommitmentView, etc. Renders the ARIA
 * progressbar role with valuemin/max/now so screen readers announce progress.
 *
 * Stacked / multi-segment bars (CapacityBars, Gantt) intentionally stay custom.
 */
export function ProgressBar({
  value,
  max = 100,
  color,
  trackColor = PROGRESS_TRACK,
  height = 6,
  width = '100%',
  ariaLabel,
  minFillPx = 2,
}: ProgressBarProps) {
  const safeMax = max > 0 ? max : 0
  const clamped = Math.max(0, Math.min(value, safeMax))
  const percent = safeMax > 0 ? (clamped / safeMax) * 100 : 0
  const fillColor = color ?? (clamped >= safeMax && safeMax > 0 ? PROGRESS_COMPLETE : PROGRESS_IN_PROGRESS)

  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={safeMax}
      aria-valuenow={clamped}
      aria-label={ariaLabel}
      style={{
        width,
        height,
        background: trackColor,
        borderRadius: height / 2,
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {percent > 0 && (
        <div
          style={{
            width: `${percent}%`,
            minWidth: minFillPx,
            height: '100%',
            background: fillColor,
            borderRadius: height / 2,
            transition: 'width 0.15s ease',
          }}
        />
      )}
    </div>
  )
}
