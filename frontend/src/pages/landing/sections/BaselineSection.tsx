import { motion } from 'framer-motion'

const baselineMetrics = [
  {
    value: '7+',
    label: 'Эпиков в работе, при норме 2-3',
    detail: 'Нарушение WIP лимитов, расфокус',
    status: 'warning'
  },
  {
    value: '6 из 10',
    label: 'эпиков с нарушением сроков',
    detail: 'Бизнес не получает ценность вовремя',
    status: 'danger'
  },
  {
    value: '50%',
    label: 'времени в буфере',
    detail: 'задачи ждут работы',
    status: 'danger'
  },
  {
    value: '—',
    label: 'Delivery процессы',
    detail: 'Слабо выстроены, требуют улучшений',
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
