import { motion } from 'framer-motion'

const proofItems = [
  {
    value: '50%',
    label: 'быстрее прогнозирование сроков'
  },
  {
    value: '3x',
    label: 'лучше видимость работы команды'
  },
  {
    value: '2 мин',
    label: 'на настройку и подключение к Jira'
  }
]

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.15
    }
  }
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.5 }
  }
}

export function SocialProofSection() {
  return (
    <section className="landing-section">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Результаты, которые вы получите</h2>
        <p className="landing-section-subtitle">
          Команды, использующие Lead Board, работают эффективнее
        </p>
      </motion.div>

      <motion.div
        className="landing-social-proof"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {proofItems.map((item, index) => (
          <motion.div
            key={index}
            className="landing-proof-item"
            variants={itemVariants}
          >
            <div className="landing-proof-value">{item.value}</div>
            <div className="landing-proof-label">{item.label}</div>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
