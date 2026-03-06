interface ViewToggleOption {
  value: string
  label: string
}

interface ViewToggleProps {
  value: string
  onChange: (value: string) => void
  options?: ViewToggleOption[]
}

const DEFAULT_OPTIONS: ViewToggleOption[] = [
  { value: 'list', label: 'List' },
  { value: 'gantt', label: 'Gantt' },
]

export function ViewToggle({ value, onChange, options = DEFAULT_OPTIONS }: ViewToggleProps) {
  return (
    <div style={{
      display: 'inline-flex',
      gap: 2,
      padding: 3,
      background: '#EBECF0',
      borderRadius: 8,
      border: '1px solid #DFE1E6',
    }}>
      {options.map(option => {
        const isActive = option.value === value
        return (
          <button
            key={option.value}
            onClick={() => onChange(option.value)}
            style={{
              padding: '6px 16px',
              fontSize: 13,
              fontWeight: 600,
              borderRadius: 6,
              border: 'none',
              cursor: 'pointer',
              background: isActive ? '#0052CC' : 'transparent',
              color: isActive ? '#fff' : '#42526E',
              transition: 'background 0.15s, color 0.15s',
            }}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
