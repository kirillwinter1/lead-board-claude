import { useState, useRef, useEffect } from 'react'
import './MultiSelectDropdown.css'

interface SingleSelectOption {
  value: string
  label: string
  color?: string
}

interface SingleSelectDropdownProps {
  label: string
  options: SingleSelectOption[]
  selected: string | null
  onChange: (value: string | null) => void
  placeholder?: string
  allowClear?: boolean
}

export function SingleSelectDropdown({
  label,
  options,
  selected,
  onChange,
  placeholder = 'All',
  allowClear = true,
}: SingleSelectDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const selectedOption = options.find(o => o.value === selected)
  const displayText = selectedOption ? label : placeholder

  return (
    <div className="filter-dropdown" ref={dropdownRef}>
      <button
        type="button"
        className={`filter-dropdown-trigger ${isOpen ? 'active' : ''} ${selected !== null ? 'has-selection' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
      >
        {selectedOption?.color && (
          <span
            style={{
              display: 'inline-block',
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: selectedOption.color,
              flexShrink: 0,
            }}
          />
        )}
        <span className="filter-dropdown-label">
          {selectedOption ? selectedOption.label : displayText}
        </span>
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
          {allowClear && (
            <div
              className={`filter-dropdown-item ${selected === null ? 'filter-dropdown-item-selected' : ''}`}
              onClick={() => { onChange(null); setIsOpen(false) }}
            >
              <span className="filter-dropdown-item-label">{placeholder}</span>
              {selected === null && (
                <svg className="filter-dropdown-checkmark" width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path d="M3.5 8.5L6.5 11.5L12.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </div>
          )}
          {options.length === 0 ? (
            <div className="filter-dropdown-empty">No options</div>
          ) : (
            options.map(option => (
              <div
                key={option.value}
                className={`filter-dropdown-item ${selected === option.value ? 'filter-dropdown-item-selected' : ''}`}
                style={selected === option.value && option.color ? {
                  backgroundColor: option.color + '25',
                } : undefined}
                onClick={() => { onChange(option.value); setIsOpen(false) }}
              >
                {option.color && (
                  <span
                    style={{
                      display: 'inline-block',
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      backgroundColor: option.color,
                      flexShrink: 0,
                    }}
                  />
                )}
                <span className="filter-dropdown-item-label">{option.label}</span>
                {selected === option.value && (
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
