import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MultiSelectDropdown } from './MultiSelectDropdown'

describe('MultiSelectDropdown', () => {
  const defaultProps = {
    label: 'Status',
    options: ['To Do', 'In Progress', 'Done'],
    selected: new Set<string>(),
    onToggle: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('should render with placeholder when nothing selected', () => {
      render(<MultiSelectDropdown {...defaultProps} />)

      expect(screen.getByText('All')).toBeInTheDocument()
    })

    it('should render with custom placeholder', () => {
      render(<MultiSelectDropdown {...defaultProps} placeholder="Select..." />)

      expect(screen.getByText('Select...')).toBeInTheDocument()
    })

    it('should render label when items are selected', () => {
      render(
        <MultiSelectDropdown
          {...defaultProps}
          selected={new Set(['To Do'])}
        />
      )

      expect(screen.getByText('Status')).toBeInTheDocument()
    })

    it('should show badge with count when items are selected', () => {
      render(
        <MultiSelectDropdown
          {...defaultProps}
          selected={new Set(['To Do', 'In Progress'])}
        />
      )

      expect(screen.getByText('2')).toBeInTheDocument()
    })

    it('should not show badge when nothing selected', () => {
      const { container } = render(<MultiSelectDropdown {...defaultProps} />)

      expect(container.querySelector('.filter-badge')).not.toBeInTheDocument()
    })
  })

  describe('Dropdown behavior', () => {
    it('should open dropdown on button click', () => {
      render(<MultiSelectDropdown {...defaultProps} />)

      fireEvent.click(screen.getByRole('button'))

      expect(screen.getByText('To Do')).toBeInTheDocument()
      expect(screen.getByText('In Progress')).toBeInTheDocument()
      expect(screen.getByText('Done')).toBeInTheDocument()
    })

    it('should close dropdown on second click', () => {
      render(<MultiSelectDropdown {...defaultProps} />)

      const button = screen.getByRole('button')
      fireEvent.click(button)
      fireEvent.click(button)

      expect(screen.queryByText('To Do')).not.toBeInTheDocument()
    })

    it('should close dropdown when clicking outside', () => {
      render(
        <div>
          <MultiSelectDropdown {...defaultProps} />
          <div data-testid="outside">Outside</div>
        </div>
      )

      fireEvent.click(screen.getByRole('button'))
      expect(screen.getByText('To Do')).toBeInTheDocument()

      fireEvent.mouseDown(screen.getByTestId('outside'))

      expect(screen.queryByText('To Do')).not.toBeInTheDocument()
    })

    it('should add active class when open', () => {
      const { container } = render(<MultiSelectDropdown {...defaultProps} />)

      fireEvent.click(screen.getByRole('button'))

      expect(container.querySelector('.filter-dropdown-trigger.active')).toBeInTheDocument()
    })
  })

  describe('Option selection', () => {
    it('should call onToggle when option is clicked', () => {
      const onToggle = vi.fn()
      render(<MultiSelectDropdown {...defaultProps} onToggle={onToggle} />)

      fireEvent.click(screen.getByRole('button'))
      fireEvent.click(screen.getByText('To Do'))

      expect(onToggle).toHaveBeenCalledWith('To Do')
    })

    it('should show checkbox as checked for selected options', () => {
      render(
        <MultiSelectDropdown
          {...defaultProps}
          selected={new Set(['To Do', 'Done'])}
        />
      )

      fireEvent.click(screen.getByRole('button'))

      const checkboxes = screen.getAllByRole('checkbox')
      expect(checkboxes[0]).toBeChecked() // To Do
      expect(checkboxes[1]).not.toBeChecked() // In Progress
      expect(checkboxes[2]).toBeChecked() // Done
    })

    it('should toggle checkbox when clicking label', () => {
      const onToggle = vi.fn()
      render(<MultiSelectDropdown {...defaultProps} onToggle={onToggle} />)

      fireEvent.click(screen.getByRole('button'))

      const label = screen.getByText('In Progress').closest('label')!
      fireEvent.click(label)

      expect(onToggle).toHaveBeenCalledWith('In Progress')
    })
  })

  describe('Empty state', () => {
    it('should show "No options" when options array is empty', () => {
      render(<MultiSelectDropdown {...defaultProps} options={[]} />)

      fireEvent.click(screen.getByRole('button'))

      expect(screen.getByText('No options')).toBeInTheDocument()
    })
  })

  describe('Structure', () => {
    it('should have correct CSS classes', () => {
      const { container } = render(<MultiSelectDropdown {...defaultProps} />)

      expect(container.querySelector('.filter-dropdown')).toBeInTheDocument()
      expect(container.querySelector('.filter-dropdown-trigger')).toBeInTheDocument()
      expect(container.querySelector('.filter-dropdown-label')).toBeInTheDocument()
      expect(container.querySelector('.filter-dropdown-chevron')).toBeInTheDocument()
    })

    it('should rotate chevron when open', () => {
      const { container } = render(<MultiSelectDropdown {...defaultProps} />)

      fireEvent.click(screen.getByRole('button'))

      expect(container.querySelector('.filter-dropdown-chevron.open')).toBeInTheDocument()
    })
  })
})
