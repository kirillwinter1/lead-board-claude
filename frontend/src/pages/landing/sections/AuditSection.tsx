import { motion } from 'framer-motion'

interface AuditSectionProps {
  onRequestAudit: () => void
}

const steps = [
  {
    number: '01',
    title: 'Разбор текущей ситуации',
    description: '30-минутный созвон: обсуждаем ваши процессы, боли и цели. Понимаем контекст.'
  },
  {
    number: '02',
    title: 'Интеграция с вашей Jira',
    description: 'Безопасное подключение через OAuth или на ваших внутренних серверах.'
  },
  {
    number: '03',
    title: 'Запуск пилота',
    description: 'Пилотный проект 4-6 недель с командой, обучение инструменту, аудит.'
  },
  {
    number: '04',
    title: 'Разбор результатов',
    description: 'Презентуем находки и обсуждаем план действий. Решаете — переходить к масштабированию или нет.'
  }
]

const results = [
  'Полный доступ к Lead Board для одной команды',
  'Настройка под ваши workflow и поля Jira',
  'Еженедельные созвоны для разбора метрик',
  'Оценка эффективности работы и качества процессов',
  'Карта узких мест по ролям и фазам'
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

export function AuditSection({ onRequestAudit }: AuditSectionProps) {
  return (
    <section id="audit" className="landing-section landing-audit">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Бесплатный аудит и пилот</h2>
        <p className="landing-section-subtitle">
          Покажем качество ваших процессов, после вы сами примите решение о сотрудничестве
        </p>
      </motion.div>

      <div className="landing-audit-content">
        <motion.div
          className="landing-audit-steps"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
        >
          {steps.map((step, index) => (
            <motion.div
              key={index}
              className="landing-audit-step"
              variants={itemVariants}
            >
              <div className="landing-audit-step-number">{step.number}</div>
              <div className="landing-audit-step-content">
                <h3 className="landing-audit-step-title">{step.title}</h3>
                <p className="landing-audit-step-text">{step.description}</p>
              </div>
            </motion.div>
          ))}
        </motion.div>

        <motion.div
          className="landing-audit-results"
          initial={{ opacity: 0, x: 20 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.3 }}
        >
          <h3 className="landing-audit-results-title">Что вы получите</h3>
          <ul className="landing-audit-results-list">
            {results.map((result, index) => (
              <li key={index} className="landing-audit-results-item">
                <span className="landing-audit-check">✓</span>
                {result}
              </li>
            ))}
          </ul>
          <button
            onClick={onRequestAudit}
            className="landing-btn landing-btn-primary landing-btn-large"
          >
            Запросить разбор (30 минут)
          </button>
        </motion.div>
      </div>
    </section>
  )
}
