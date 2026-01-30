import { motion } from 'framer-motion'

const resultsMetrics = [
  {
    before: '8.0x',
    after: '1.1x',
    label: 'Сократил LTC',
    detail: 'в среднем по 6 командам'
  },
  {
    before: '0.5',
    after: '4',
    label: 'Увеличил Throughput',
    detail: 'эпиков в месяц'
  },
  {
    before: '6 мес',
    after: '2 мес',
    label: 'Сократил Lead Time',
    detail: 'среднее по эпикам'
  },
  {
    before: '50%',
    after: '15%',
    label: 'Уменьшил буфер',
    detail: 'по задачам'
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
    transition: { staggerChildren: 0.1 }
  }
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4 } }
}

export function ResultsSection() {
  return (
    <section className="landing-section landing-section-compact landing-results">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">
          Как Lead Board помог
        </h2>
        <p className="landing-section-subtitle">
          Результаты внедрения в production-командах
        </p>
      </motion.div>

      <motion.div
        className="landing-results-grid"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {resultsMetrics.map((metric, index) => (
          <motion.div
            key={index}
            className="landing-results-card"
            variants={itemVariants}
          >
            <div className="results-card-status" />
            <div className="results-card-value">
              {metric.before && (
                <>
                  <span className="results-before">{metric.before}</span>
                  <span className="results-arrow">→</span>
                </>
              )}
              <span className="results-after">{metric.after}</span>
            </div>
            <div className="results-card-label">{metric.label}</div>
            <div className="results-card-detail">{metric.detail}</div>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
