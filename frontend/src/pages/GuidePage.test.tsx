import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, beforeAll, beforeEach } from 'vitest'
import { GuidePage } from './GuidePage'

// IntersectionObserver is not available in jsdom
beforeAll(() => {
  global.IntersectionObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof IntersectionObserver
})

// Reset localStorage before each test so language defaults to RU
beforeEach(() => {
  localStorage.clear()
})

function renderGuide() {
  return render(
    <MemoryRouter initialEntries={['/guide']}>
      <GuidePage />
    </MemoryRouter>
  )
}

describe('GuidePage', () => {
  it('renders without crash', () => {
    renderGuide()
    expect(screen.getByText('Delivery Guide')).toBeInTheDocument()
  })

  it('renders all pipeline stages in sidebar', () => {
    renderGuide()
    // Default language is RU; text may appear in sidebar and section title
    expect(screen.getAllByText('1. Идея').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('8. Готово').length).toBeGreaterThanOrEqual(1)
  })

  it('switches language on toggle click', () => {
    renderGuide()
    const enBtn = screen.getAllByText('EN')[0]
    fireEvent.click(enBtn)
    expect(screen.getByText('Process Overview')).toBeInTheDocument()
  })

  it('renders pipeline visual with all stages', () => {
    renderGuide()
    // Pipeline visual labels appear alongside identical text in section titles and content
    expect(screen.getAllByText('Идея').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Планирование').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Разработка').length).toBeGreaterThanOrEqual(1)
  })

  it('renders roles section', () => {
    renderGuide()
    expect(screen.getByText('Product Owner (PO)')).toBeInTheDocument()
    expect(screen.getByText('Delivery Manager (DM)')).toBeInTheDocument()
    expect(screen.getByText('Team Lead (TL)')).toBeInTheDocument()
  })
})
