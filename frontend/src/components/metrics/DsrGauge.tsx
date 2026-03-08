import GaugeComponent from 'react-gauge-component'
import { DSR_GREEN, DSR_YELLOW, DSR_RED, getDsrColor } from '../../constants/colors'
import './MetricCard.css'

interface DsrGaugeProps {
  value: number | null
  title: string
  subtitle: string
  tooltip?: string
}

const DEFAULT_TOOLTIP = `DSR — delivery speed ratio for an epic.\nFormula: (working days − pause days) / estimate in days.\n1.0 = on target, < 1.0 = faster, > 1.0 = slower.\nPause (flag on epic) stops the DSR timer.`

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
                colorArray: [DSR_GREEN, DSR_YELLOW, DSR_RED],
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
          <div className="dsr-gauge-value" style={{ color: getDsrColor(value) }}>
            {value.toFixed(2)}
          </div>
          <div className="metric-card-subtitle">{subtitle}</div>
        </>
      ) : (
        <>
          <div className="metric-card-value">—</div>
          <div className="metric-card-subtitle">no data</div>
        </>
      )}
    </div>
  )
}
