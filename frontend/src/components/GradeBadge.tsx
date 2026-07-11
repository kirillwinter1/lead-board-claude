import { GRADE_COLORS, BG_SUBTLE, TEXT_SUBTLE } from '../constants/colors'

interface GradeBadgeProps {
  grade: string
}

/**
 * Unified member-seniority pill (junior/middle/senior). Colours come from
 * GRADE_COLORS; unknown grades fall back to a neutral grey. Same visual shape
 * as RoleBadge. Replaces per-page .grade-badge CSS classes.
 */
export function GradeBadge({ grade }: GradeBadgeProps) {
  const colors = GRADE_COLORS[grade.toLowerCase()] ?? { bg: BG_SUBTLE, text: TEXT_SUBTLE }
  return (
    <span
      style={{
        background: colors.bg,
        color: colors.text,
        padding: '2px 8px',
        borderRadius: 4,
        fontSize: 12,
        fontWeight: 600,
        display: 'inline-block',
      }}
    >
      {grade}
    </span>
  )
}
