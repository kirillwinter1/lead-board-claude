import './FilterChips.css'

export interface FilterChip {
  category: string
  value: string
  color?: string
  onRemove: () => void
}

interface FilterChipsProps {
  chips: FilterChip[]
  onClearAll?: () => void
}

export function FilterChips({ chips, onClearAll }: FilterChipsProps) {
  if (chips.length === 0) return null

  return (
    <div className="filter-chips">
      {chips.map((chip, i) => (
        <span
          key={`${chip.category}-${chip.value}-${i}`}
          className="filter-chip"
          style={chip.color ? {
            backgroundColor: chip.color + '20',
            borderColor: chip.color + '50',
          } : undefined}
        >
          {chip.color && (
            <span
              className="filter-chip-dot"
              style={{ backgroundColor: chip.color }}
            />
          )}
          <span className="filter-chip-text">{chip.value}</span>
          <button
            className="filter-chip-remove"
            onClick={chip.onRemove}
          >
            &times;
          </button>
        </span>
      ))}
      {onClearAll && chips.length > 1 && (
        <button className="filter-chips-clear" onClick={onClearAll}>
          Clear all
        </button>
      )}
    </div>
  )
}
