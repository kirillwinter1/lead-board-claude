import { motion } from 'framer-motion'
import { mockMetrics } from '../mockData'

export function DemoMetrics() {
  return (
    <div className="demo-metrics">
      <motion.div
        className="demo-metric-card"
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0 }}
      >
        <LtcGaugeSimple value={mockMetrics.ltc} />
        <div className="demo-metric-label">Lead Time to Change</div>
      </motion.div>

      <motion.div
        className="demo-metric-card"
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.1 }}
      >
        <div className="demo-metric-value">{mockMetrics.throughput}</div>
        <div className="demo-metric-label">Stories / Sprint</div>
      </motion.div>

      <motion.div
        className="demo-metric-card"
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.2 }}
      >
        <div className="demo-metric-value">{mockMetrics.forecastAccuracy}%</div>
        <div className="demo-metric-label">Forecast Accuracy</div>
      </motion.div>

      <motion.div
        className="demo-metric-card"
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.3 }}
      >
        <div className="demo-metric-value">{mockMetrics.velocity}</div>
        <div className="demo-metric-label">Story Points / Sprint</div>
      </motion.div>
    </div>
  )
}

function LtcGaugeSimple({ value }: { value: number }) {
  const getColor = (val: number) => {
    if (val <= 1.0) return '#16a34a'
    if (val <= 1.5) return '#eab308'
    return '#dc2626'
  }

  const percentage = Math.min(value / 2, 1) * 100
  const color = getColor(value)

  return (
    <div className="demo-gauge-container">
      <svg viewBox="0 0 200 120" style={{ width: '100%', height: '100%' }}>
        {/* Background arc */}
        <path
          d="M 20 100 A 80 80 0 0 1 180 100"
          fill="none"
          stroke="#e5e7eb"
          strokeWidth="16"
          strokeLinecap="round"
        />
        {/* Value arc */}
        <motion.path
          d="M 20 100 A 80 80 0 0 1 180 100"
          fill="none"
          stroke={color}
          strokeWidth="16"
          strokeLinecap="round"
          strokeDasharray={`${percentage * 2.51} 251`}
          initial={{ strokeDasharray: '0 251' }}
          animate={{ strokeDasharray: `${percentage * 2.51} 251` }}
          transition={{ duration: 1, delay: 0.5 }}
        />
        {/* Value text */}
        <text
          x="100"
          y="90"
          textAnchor="middle"
          style={{
            fontSize: '2.5rem',
            fontWeight: 700,
            fill: color
          }}
        >
          {value.toFixed(2)}
        </text>
      </svg>
    </div>
  )
}
