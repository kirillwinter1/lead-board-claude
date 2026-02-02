import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Modal } from './Modal'

describe('Modal', () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    title: 'Test Modal',
    children: <div>Modal content</div>,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    document.body.style.overflow = ''
  })

  describe('Rendering', () => {
    it('should render when isOpen is true', () => {
      render(<Modal {...defaultProps} />)

      expect(screen.getByText('Test Modal')).toBeInTheDocument()
      expect(screen.getByText('Modal content')).toBeInTheDocument()
    })

    it('should not render when isOpen is false', () => {
      render(<Modal {...defaultProps} isOpen={false} />)

      expect(screen.queryByText('Test Modal')).not.toBeInTheDocument()
      expect(screen.queryByText('Modal content')).not.toBeInTheDocument()
    })

    it('should render title in header', () => {
      render(<Modal {...defaultProps} title="Custom Title" />)

      expect(screen.getByText('Custom Title')).toBeInTheDocument()
    })

    it('should render children in body', () => {
      render(
        <Modal {...defaultProps}>
          <span data-testid="custom-child">Custom Child</span>
        </Modal>
      )

      expect(screen.getByTestId('custom-child')).toBeInTheDocument()
    })

    it('should render close button', () => {
      render(<Modal {...defaultProps} />)

      expect(screen.getByText('×')).toBeInTheDocument()
    })
  })

  describe('Close behavior', () => {
    it('should call onClose when close button is clicked', () => {
      const onClose = vi.fn()
      render(<Modal {...defaultProps} onClose={onClose} />)

      fireEvent.click(screen.getByText('×'))

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should call onClose when overlay is clicked', () => {
      const onClose = vi.fn()
      const { container } = render(<Modal {...defaultProps} onClose={onClose} />)

      const overlay = container.querySelector('.modal-overlay')
      fireEvent.click(overlay!)

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should not call onClose when modal content is clicked', () => {
      const onClose = vi.fn()
      const { container } = render(<Modal {...defaultProps} onClose={onClose} />)

      const content = container.querySelector('.modal-content')
      fireEvent.click(content!)

      expect(onClose).not.toHaveBeenCalled()
    })

    it('should call onClose when Escape key is pressed', () => {
      const onClose = vi.fn()
      render(<Modal {...defaultProps} onClose={onClose} />)

      fireEvent.keyDown(document, { key: 'Escape' })

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should not call onClose when other keys are pressed', () => {
      const onClose = vi.fn()
      render(<Modal {...defaultProps} onClose={onClose} />)

      fireEvent.keyDown(document, { key: 'Enter' })

      expect(onClose).not.toHaveBeenCalled()
    })
  })

  describe('Body scroll lock', () => {
    it('should set body overflow to hidden when open', () => {
      render(<Modal {...defaultProps} />)

      expect(document.body.style.overflow).toBe('hidden')
    })

    it('should restore body overflow when closed', () => {
      const { rerender } = render(<Modal {...defaultProps} />)

      rerender(<Modal {...defaultProps} isOpen={false} />)

      expect(document.body.style.overflow).toBe('')
    })

    it('should restore body overflow on unmount', () => {
      const { unmount } = render(<Modal {...defaultProps} />)

      unmount()

      expect(document.body.style.overflow).toBe('')
    })
  })

  describe('Structure', () => {
    it('should have correct CSS classes', () => {
      const { container } = render(<Modal {...defaultProps} />)

      expect(container.querySelector('.modal-overlay')).toBeInTheDocument()
      expect(container.querySelector('.modal-content')).toBeInTheDocument()
      expect(container.querySelector('.modal-header')).toBeInTheDocument()
      expect(container.querySelector('.modal-title')).toBeInTheDocument()
      expect(container.querySelector('.modal-close')).toBeInTheDocument()
      expect(container.querySelector('.modal-body')).toBeInTheDocument()
    })
  })
})
