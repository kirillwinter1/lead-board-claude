import { motion } from 'framer-motion'

interface CaseSectionProps {
  onRequestAudit: () => void
}

const screenshots = [
  {
    title: 'Board: прогресс по эпикам',
    placeholder: 'Board'
  },
  {
    title: 'Timeline: прогноз сроков',
    placeholder: 'Timeline'
  },
  {
    title: 'Metrics: метрики команды',
    placeholder: 'Metrics'
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

export function CaseSection({ onRequestAudit }: CaseSectionProps) {
  return (
    <section id="case" className="landing-section landing-case">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Lead Board в деле</h2>
        <p className="landing-section-subtitle">
          Как выглядит работа с инструментом
        </p>
      </motion.div>

      <motion.div
        className="landing-case-screenshots"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {screenshots.map((screenshot, index) => (
          <motion.div
            key={index}
            className="landing-case-screenshot"
            variants={itemVariants}
          >
            <div className="landing-case-screenshot-placeholder">
              {screenshot.placeholder}
            </div>
            <p className="landing-case-screenshot-title">{screenshot.title}</p>
          </motion.div>
        ))}
      </motion.div>

      <motion.div
        className="landing-case-fit"
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5, delay: 0.3 }}
      >
        <div className="landing-case-fit-content">
          <h3 className="landing-case-fit-title">Подходит, если:</h3>
          <ul className="landing-case-fit-list">
            <li>3+ команды разработки</li>
            <li>Есть эпики с внешними дедлайнами</li>
            <li>Jira — основной источник задач</li>
            <li>Руководство требует прогнозируемости сроков</li>
          </ul>
        </div>
        <div className="landing-case-fit-cta">
          <button
            onClick={onRequestAudit}
            className="landing-btn landing-btn-primary landing-btn-large"
          >
            Запросить разбор (30 минут)
          </button>
          <p className="landing-case-fit-micro">
            Без договора. Без внедрения. Только разбор и цифры.
          </p>
        </div>
      </motion.div>
    </section>
  )
}
