import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { EpicRoleChip } from './EpicRoleChip'
import type { RoleMetrics } from './types'

// The rough-only chip always displays the pre-planning rough estimate (poker
// publishes into subtask Original Estimates, never back into rough estimates),
// so it must never claim to be a poker estimate or render a filled style.
describe('EpicRoleChip rough-only', () => {
  const roughMetrics: RoleMetrics = {
    estimateSeconds: 0,
    loggedSeconds: 0,
    progress: 0,
    roughEstimateDays: 25,
  }

  const baseProps = {
    label: 'SA',
    role: 'SA',
    metrics: roughMetrics,
    epicInTodo: false,
    epicKey: 'LB-1',
    config: null,
    onUpdate: async () => {},
    roleColor: '#1558BC',
  }

  it('labels the value as a rough estimate and stays outlined', () => {
    const { container } = render(<EpicRoleChip {...baseProps} />)
    const chip = container.querySelector('.epic-role-chip.rough-only') as HTMLElement
    expect(chip).not.toBeNull()
    expect(chip.title).toBe('Rough estimate (pre-planning): 25d')
    expect(chip.classList.contains('clean')).toBe(false)
  })
})
