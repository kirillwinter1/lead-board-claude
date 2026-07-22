import { lightenColor } from '../constants/colors'

// F92 — role-derived status colors. Mirrors backend StatusColorResolver.resolve
// (com.leadboard.config.service.StatusColorResolver) so the wizard can preview the
// derived color instantly, without a round-trip. Manual override handling (color !==
// null wins) is the caller's job — this helper only computes the *derived* value.

export type StatusKind = 'WORK' | 'REVIEW' | 'WAITING'

const REVIEW_TINT = 0.65 // = TIMELINE_PHASE_TINT / backend StatusColorResolver.REVIEW_TINT
const WAITING_GREY = '#DFE1E6'

const CATEGORY_DEFAULTS: Record<string, string> = {
  NEW: '#DFE1E6',
  IN_PROGRESS: '#DEEBFF',
  DONE: '#E3FCEF',
  REQUIREMENTS: '#E6FCFF',
  PLANNED: '#EAE6FF',
  DEV_DONE: '#FFF0B3',
}

/**
 * Derive a status's background color from its role color and kind, the same way the
 * backend does when `color` is null (an "auto" status with no manual override).
 *
 * Kind and role color only apply to IN_PROGRESS statuses (NEW/DONE carry no kind):
 * - WAITING kind -> grey, even if a role color is available.
 * - Role color present -> WORK/null-kind uses it as-is (active work is saturated),
 *   REVIEW lightens it by 0.65 (decision 22.07 — review is the translucent tone).
 * Everything else falls back to the statusCategory default (grey if unmapped).
 */
export function deriveStatusColor(
  roleColor: string | null,
  kind: StatusKind | null,
  statusCategory: string,
): string {
  if (statusCategory === 'IN_PROGRESS') {
    if (kind === 'WAITING') return WAITING_GREY
    if (roleColor) {
      return kind === 'REVIEW' ? lightenColor(roleColor, REVIEW_TINT) : roleColor
    }
  }
  return CATEGORY_DEFAULTS[statusCategory] || WAITING_GREY
}
