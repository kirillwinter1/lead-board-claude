import { describe, it, expect } from 'vitest'

describe('Test setup', () => {
  it('should work with vitest', () => {
    expect(1 + 1).toBe(2)
  })

  it('should have jest-dom matchers', () => {
    const div = document.createElement('div')
    div.textContent = 'Hello'
    document.body.appendChild(div)

    expect(div).toBeInTheDocument()
    expect(div).toHaveTextContent('Hello')

    document.body.removeChild(div)
  })
})
