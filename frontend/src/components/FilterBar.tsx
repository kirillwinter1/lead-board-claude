import { ReactNode } from 'react'
import { FilterChips, FilterChip } from './FilterChips'
import './FilterBar.css'

interface FilterBarProps {
  children: ReactNode
  chips?: FilterChip[]
  onClearAll?: () => void
  trailing?: ReactNode
}

export function FilterBar({ children, chips, onClearAll, trailing }: FilterBarProps) {
  const hasChips = chips && chips.length > 0

  return (
    <div className="filter-bar">
      <div className="filter-bar-controls">
        {children}
        {trailing && <div className="filter-bar-trailing">{trailing}</div>}
      </div>
      {hasChips && (
        <div className="filter-bar-chips">
          <FilterChips chips={chips} onClearAll={onClearAll} />
        </div>
      )}
    </div>
  )
}
