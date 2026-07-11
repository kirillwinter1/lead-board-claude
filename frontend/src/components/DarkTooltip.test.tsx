import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DarkTooltip } from './DarkTooltip'

describe('DarkTooltip', () => {
  it('portals its children to document.body as a fixed-position box', () => {
    render(
      <DarkTooltip top={100} left={200}>
        <span>tooltip body</span>
      </DarkTooltip>,
    )
    const content = screen.getByText('tooltip body')
    const box = content.parentElement as HTMLElement
    expect(box.style.position).toBe('fixed')
    expect(box.style.zIndex).toBe('10000')
    // portalled to body, not nested in the React tree root
    expect(box.parentElement).toBe(document.body)
  })

  it('is non-interactive by default and interactive when asked', () => {
    const { rerender } = render(<DarkTooltip top={0} left={0}><span>a</span></DarkTooltip>)
    expect((screen.getByText('a').parentElement as HTMLElement).style.pointerEvents).toBe('none')
    rerender(<DarkTooltip top={0} left={0} interactive><span>a</span></DarkTooltip>)
    expect((screen.getByText('a').parentElement as HTMLElement).style.pointerEvents).toBe('auto')
  })

  it('clamps left so a wide tooltip never overflows the viewport right edge', () => {
    // jsdom default innerWidth is 1024; left=2000 must be pulled back in.
    render(<DarkTooltip top={0} left={2000} maxWidth={420}><span>clamp</span></DarkTooltip>)
    const box = screen.getByText('clamp').parentElement as HTMLElement
    const left = parseInt(box.style.left, 10)
    expect(left).toBeLessThanOrEqual(window.innerWidth - 420 - 8)
  })

  it('Progress caps its fill at 100% and recolors when over max', () => {
    const { rerender } = render(
      <DarkTooltip top={0} left={0}><DarkTooltip.Progress value={50} max={100} /></DarkTooltip>,
    )
    const underFill = document.querySelector('div[style*="width: 50%"]') as HTMLElement
    expect(underFill).toBeTruthy()
    const underColor = underFill.style.background

    rerender(<DarkTooltip top={0} left={0}><DarkTooltip.Progress value={150} max={100} /></DarkTooltip>)
    // width is clamped to 100% even though value exceeds max
    const overFill = document.querySelector('div[style*="width: 100%"]') as HTMLElement
    expect(overFill).toBeTruthy()
    // over-max fill uses the danger color, distinct from the success color
    expect(overFill.style.background).not.toBe(underColor)
  })
})
