import { motion } from 'framer-motion'

interface ResultMetric {
  before: string | null
  after: string
  label: string
  detail: string
}

const metrics: ResultMetric[] = [
  {
    before: '8.0x',
    after: '1.1x',
    label: 'Delivery Speed Ratio',
    detail: 'в среднем по 6 командам'
  },
  {
    before: '0.5',
    after: '4',
    label: 'Throughput',
    detail: 'эпиков в месяц'
  },
  {
    before: '6 мес',
    after: '2 мес',
    label: 'Lead Time',
    detail: 'среднее по эпикам'
  },
  {
    before: '50%',
    after: '15%',
    label: 'Буфер по задачам',
    detail: 'снижение перестраховки'
  },
  {
    before: null,
    after: '✓',
    label: 'Обратная связь',
    detail: 'от заказчиков и стейкхолдеров'
  }
]

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.3 }
  }
}

const itemVariants = {
  hidden: { opacity: 0, x: 20 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.3 } }
}

export function ExecutionSnapshot() {
  return (
    <motion.div
      className="results-snapshot"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.2 }}
    >
      <div className="results-snapshot-header">
        <h3 className="results-snapshot-title">Как Lead Board помог</h3>
        <p className="results-snapshot-subtitle">Результаты внедрения в production-командах</p>
      </div>

      <motion.div
        className="results-snapshot-grid"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {metrics.map((metric, index) => (
          <motion.div
            key={index}
            className="results-snapshot-card"
            variants={itemVariants}
          >
            <div className="results-snapshot-card-label">{metric.label}</div>
            <div className="results-snapshot-card-value">
              {metric.before && (
                <>
                  <span className="results-snapshot-before">{metric.before}</span>
                  <span className="results-snapshot-arrow">→</span>
                </>
              )}
              <span className="results-snapshot-after">{metric.after}</span>
            </div>
            <div className="results-snapshot-card-detail">{metric.detail}</div>
          </motion.div>
        ))}
      </motion.div>
    </motion.div>
  )
}
