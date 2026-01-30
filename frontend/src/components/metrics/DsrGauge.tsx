import GaugeComponent from 'react-gauge-component'
import './MetricCard.css'

interface DsrGaugeProps {
  value: number | null
  title: string
  subtitle: string
  tooltip?: string
}

function getValueColor(v: number): string {
  if (v <= 1.1) return '#00875a'
  if (v <= 1.5) return '#ff991f'
  return '#de350b'
}

const DEFAULT_TOOLTIP = `Delivery Speed Ratio — относительная скорость выполнения эпика с учётом объёма.
1.0 — норма, меньше — быстрее, больше — медленнее.`

export function DsrGauge({ value, title, subtitle, tooltip = DEFAULT_TOOLTIP }: DsrGaugeProps) {
  return (
    <div className="metric-card dsr-gauge-card">
      <div className="metric-card-header">
        <div className="metric-card-title">{title}</div>
        <div className="metric-tooltip-wrapper">
          <span className="metric-tooltip-icon">?</span>
          <div className="metric-tooltip-text">{tooltip}</div>
        </div>
      </div>
      {value !== null ? (
        <>
          <div className="dsr-gauge-container">
            <GaugeComponent
              type="semicircle"
              arc={{
                colorArray: ['#00875a', '#ff991f', '#de350b'],
                subArcs: [
                  { limit: 1.1 },
                  { limit: 1.5 },
                  { limit: 2.5 },
                ],
                padding: 0.02,
                width: 0.25,
              }}
              pointer={{
                type: 'needle',
                elastic: true,
                animationDelay: 0,
              }}
              value={value}
              minValue={0}
              maxValue={2.5}
              labels={{
                valueLabel: { hide: true },
                tickLabels: { hideMinMax: true },
              }}
            />
          </div>
          <div className="dsr-gauge-value" style={{ color: getValueColor(value) }}>
            {value.toFixed(2)}
          </div>
          <div className="metric-card-subtitle">{subtitle}</div>
        </>
      ) : (
        <>
          <div className="metric-card-value">—</div>
          <div className="metric-card-subtitle">нет данных</div>
        </>
      )}
    </div>
  )
}
