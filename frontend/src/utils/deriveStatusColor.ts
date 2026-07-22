import { lightenColor } from '../constants/colors'

// F92 — role-derived status colors. Mirrors backend StatusColorResolver.resolve
// (com.leadboard.config.service.StatusColorResolver) so the wizard can preview the
// derived color instantly, without a round-trip. Manual override handling (color !==
// null wins) is the caller's job — this helper only computes the *derived* value.

export type StatusKind = 'WORK' | 'REVIEW' | 'WAITING'

const WORK_TINT = 0.65 // = TIMELINE_PHASE_TINT / backend StatusColorResolver.WORK_TINT
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
 * - WAITING kind -> always grey, even if a role color is available.
 * - Role color present -> REVIEW uses it as-is, WORK/null-kind lightens it by 0.65.
 * - No/unknown role color -> falls back to the statusCategory default (grey if unmapped).
 */
export function deriveStatusColor(
  roleColor: string | null,
  kind: StatusKind | null,
  statusCategory: string,
): string {
  if (kind === 'WAITING') return WAITING_GREY
  if (roleColor) {
    return kind === 'REVIEW' ? roleColor : lightenColor(roleColor, WORK_TINT)
  }
  return CATEGORY_DEFAULTS[statusCategory] || WAITING_GREY
}
