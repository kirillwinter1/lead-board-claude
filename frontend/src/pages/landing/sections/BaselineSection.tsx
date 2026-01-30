import { motion } from 'framer-motion'

const baselineMetrics = [
  {
    value: '65%',
    label: 'Forecast Accuracy',
    detail: 'при целевом 85%',
    status: 'warning'
  },
  {
    value: '+18%',
    label: 'Schedule Variance',
    detail: 'среднее отклонение',
    status: 'danger'
  },
  {
    value: '31',
    label: 'Data Quality Issues',
    detail: 'требуют внимания',
    status: 'danger'
  },
  {
    value: '3 из 8',
    label: 'Risk Epics',
    detail: 'под угрозой срыва',
    status: 'warning'
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

export function BaselineSection() {
  return (
    <section className="landing-section landing-section-compact landing-baseline">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">
          Что обычно показывают delivery-данные на старте
        </h2>
        <p className="landing-section-subtitle">
          Средние цифры по результатам аудитов
        </p>
      </motion.div>

      <motion.div
        className="landing-baseline-grid"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {baselineMetrics.map((metric, index) => (
          <motion.div
            key={index}
            className="landing-baseline-card"
            variants={itemVariants}
          >
            <div className={`baseline-card-status baseline-card-status-${metric.status}`} />
            <div className="baseline-card-value">{metric.value}</div>
            <div className="baseline-card-label">{metric.label}</div>
            <div className="baseline-card-detail">{metric.detail}</div>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
