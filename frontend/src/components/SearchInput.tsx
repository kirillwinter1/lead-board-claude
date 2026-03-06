import { useState } from 'react'
import './MultiSelectDropdown.css'

interface SearchInputProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  loading?: boolean
  badge?: { label: string; variant: 'ai' | 'text' }
  hints?: string[]
}

export function SearchInput({
  value,
  onChange,
  placeholder = 'Search...',
  loading,
  badge,
  hints,
}: SearchInputProps) {
  const [focused, setFocused] = useState(false)

  return (
    <div className="filter-search">
      <svg
        className="search-icon"
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
      >
        <path
          d="M7 12C9.76142 12 12 9.76142 12 7C12 4.23858 9.76142 2 7 2C4.23858 2 2 4.23858 2 7C2 9.76142 4.23858 12 7 12Z"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M14 14L10.5 10.5"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <input
        type="text"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 150)}
        className="filter-input"
      />
      {loading && (
        <span className="search-loading">...</span>
      )}
      {!loading && badge && (
        <span className={`search-mode-badge ${badge.variant === 'ai' ? 'badge-ai' : 'badge-txt'}`}>
          {badge.label}
        </span>
      )}
      {focused && !value && hints && hints.length > 0 && (
        <div className="search-hints">
          <div className="search-hints-title">Try searching by meaning:</div>
          {hints.map((hint, i) => (
            <button
              key={i}
              className="search-hint-item"
              onMouseDown={(e) => { e.preventDefault(); onChange(hint) }}
            >
              {hint}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
