import { useState, useRef, useEffect } from 'react'
import './MultiSelectDropdown.css'

interface MultiSelectDropdownProps {
  label: string
  options: string[]
  selected: Set<string>
  onToggle: (value: string) => void
  placeholder?: string
  colorMap?: Map<string, string>
  countMap?: Map<string, number>
  renderOption?: (option: string) => string
}

export function MultiSelectDropdown({
  label,
  options,
  selected,
  onToggle,
  placeholder = 'All',
  colorMap,
  countMap,
  renderOption,
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
        className={`filter-dropdown-trigger ${isOpen ? 'active' : ''} ${selected.size > 0 ? 'has-selection' : ''}`}
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
        <div className="filter-dropdown-menu filter-dropdown-menu-animated">
          {options.length === 0 ? (
            <div className="filter-dropdown-empty">No options</div>
          ) : (
            options.map(option => (
              <div
                key={option}
                className={`filter-dropdown-item ${selected.has(option) ? 'filter-dropdown-item-selected' : ''}`}
                style={selected.has(option) && colorMap?.get(option) ? {
                  backgroundColor: colorMap.get(option)! + '25',
                } : undefined}
                onClick={() => onToggle(option)}
              >
                {colorMap?.get(option) && (
                  <span
                    style={{
                      display: 'inline-block',
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      backgroundColor: colorMap.get(option),
                      flexShrink: 0,
                    }}
                  />
                )}
                <span className="filter-dropdown-item-label">{renderOption ? renderOption(option) : option}</span>
                {countMap?.has(option) && (
                  <span className="filter-dropdown-item-count">{countMap.get(option)}</span>
                )}
                {selected.has(option) && (
                  <svg className="filter-dropdown-checkmark" width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <path d="M3.5 8.5L6.5 11.5L12.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
