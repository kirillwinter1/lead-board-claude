import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatusAgeBadge } from './StatusAgeBadge'
import { STATUS_AGE_COLORS } from '../constants/colors'

describe('StatusAgeBadge', () => {
  it('renders the day count with the "d" suffix', () => {
    render(<StatusAgeBadge days={5} level="NORMAL" />)
    expect(screen.getByText('5d')).toBeInTheDocument()
  })

  it('applies the CRITICAL background color', () => {
    render(<StatusAgeBadge days={12} level="CRITICAL" reason="stuck" />)
    const badge = screen.getByText('12d')
    expect(badge).toHaveStyle({ background: STATUS_AGE_COLORS.CRITICAL.bg })
  })

  it('renders nothing when days is null', () => {
    const { container } = render(<StatusAgeBadge days={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})
