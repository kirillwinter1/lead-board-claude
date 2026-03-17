import { useMetricsFilter } from '../../contexts/MetricsFilterContext'
import { LINK_COLOR, TEXT_SECONDARY, BORDER_DEFAULT, PRIMARY_LIGHT_BG } from '../../constants/colors'

const PRESETS = [
  { key: '30d' as const, label: '30d' },
  { key: '90d' as const, label: '90d' },
  { key: '180d' as const, label: '180d' },
  { key: '365d' as const, label: '1y' },
]

export function DateRangePicker() {
  const { preset, setPreset } = useMetricsFilter()

  return (
    <div style={{ display: 'flex', gap: 4 }}>
      {PRESETS.map(p => (
        <button
          key={p.key}
          className={`filter-pill ${preset === p.key ? 'filter-pill-active' : ''}`}
          onClick={() => setPreset(p.key)}
          style={{
            padding: '4px 12px',
            borderRadius: 16,
            border: preset === p.key ? `1px solid ${LINK_COLOR}` : `1px solid ${BORDER_DEFAULT}`,
            background: preset === p.key ? PRIMARY_LIGHT_BG : '#fff',
            color: preset === p.key ? LINK_COLOR : TEXT_SECONDARY,
            fontSize: 13,
            fontWeight: preset === p.key ? 600 : 400,
            cursor: 'pointer',
          }}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}
