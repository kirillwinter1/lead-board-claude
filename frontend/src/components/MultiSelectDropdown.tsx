import { useState, useRef, useEffect } from 'react'

interface MultiSelectDropdownProps {
  label: string
  options: string[]
  selected: Set<string>
  onToggle: (value: string) => void
  placeholder?: string
}

export function MultiSelectDropdown({
  label,
  options,
  selected,
  onToggle,
  placeholder = 'All'
}: MultiSelectDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Click outside to close
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const displayText = selected.size === 0 ? placeholder : label

  return (
    <div className="filter-dropdown" ref={dropdownRef}>
      <button
        type="button"
        className={`filter-dropdown-trigger ${isOpen ? 'active' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
      >
        <span className="filter-dropdown-label">{displayText}</span>
        {selected.size > 0 && (
          <span className="filter-badge">{selected.size}</span>
        )}
        <svg
          className={`filter-dropdown-chevron ${isOpen ? 'open' : ''}`}
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
        >
          <path
            d="M4 6L8 10L12 6"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>

      {isOpen && (
        <div className="filter-dropdown-menu">
          {options.length === 0 ? (
            <div className="filter-dropdown-empty">No options</div>
          ) : (
            options.map(option => (
              <label key={option} className="filter-dropdown-item">
                <input
                  type="checkbox"
                  checked={selected.has(option)}
                  onChange={() => onToggle(option)}
                />
                <span>{option}</span>
              </label>
            ))
          )}
        </div>
      )}
    </div>
  )
}
