import { describe, it, expect } from 'vitest'
import { deriveStatusColor } from './deriveStatusColor'

// F92 — mirrors backend StatusColorResolverTest matrix (manual override is the
// caller's job, not part of this helper — see StatusColorResolver.resolve in Java).
const ROLE_COLOR = '#10b981'

describe('deriveStatusColor', () => {
  it('uses the saturated role color as-is for WORK kind', () => {
    expect(deriveStatusColor(ROLE_COLOR, 'WORK', 'IN_PROGRESS')).toBe('#10b981')
  })

  it('treats a null kind the same as WORK when a role color is present', () => {
    expect(deriveStatusColor(ROLE_COLOR, null, 'IN_PROGRESS')).toBe('#10b981')
  })

  it('lightens the role color for REVIEW kind', () => {
    expect(deriveStatusColor(ROLE_COLOR, 'REVIEW', 'IN_PROGRESS')).toBe('#abe7d3')
  })

  it('is grey for WAITING kind even with a role color', () => {
    expect(deriveStatusColor(ROLE_COLOR, 'WAITING', 'IN_PROGRESS')).toBe('#DFE1E6')
  })

  it('is grey for WAITING kind with no role color', () => {
    expect(deriveStatusColor(null, 'WAITING', 'IN_PROGRESS')).toBe('#DFE1E6')
  })

  it('falls back to category defaults when there is no role color', () => {
    expect(deriveStatusColor(null, null, 'IN_PROGRESS')).toBe('#DEEBFF')
    expect(deriveStatusColor(null, null, 'NEW')).toBe('#DFE1E6')
    expect(deriveStatusColor(null, null, 'DONE')).toBe('#E3FCEF')
    expect(deriveStatusColor(null, null, 'REQUIREMENTS')).toBe('#E6FCFF')
    expect(deriveStatusColor(null, null, 'PLANNED')).toBe('#EAE6FF')
    expect(deriveStatusColor(null, null, 'DEV_DONE')).toBe('#FFF0B3')
  })

  it('falls back to the grey default for an unmapped category', () => {
    expect(deriveStatusColor(null, null, 'UNKNOWN_CATEGORY')).toBe('#DFE1E6')
  })
})
