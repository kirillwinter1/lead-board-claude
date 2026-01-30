import { motion } from 'framer-motion'

const differences = [
  {
    jira: 'Отчёты по задачам',
    leadboard: 'Прогноз сроков эпиков'
  },
  {
    jira: 'Статус «в работе»',
    leadboard: 'Фазы SA → DEV → QA'
  },
  {
    jira: 'Velocity в story points',
    leadboard: 'Forecast accuracy %'
  },
  {
    jira: 'Ручные дашборды',
    leadboard: 'Автоматические метрики'
  },
  {
    jira: 'Нет валидации данных',
    leadboard: 'Data quality alerts'
  },
  {
    jira: 'Загрузка не видна',
    leadboard: 'Role bottleneck detection'
  }
]

export function DifferentiationSection() {
  return (
    <section className="landing-section landing-section-compact landing-diff">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Чем отличается от Jira-дашбордов</h2>
      </motion.div>

      <motion.div
        className="landing-diff-table"
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5, delay: 0.2 }}
      >
        <div className="landing-diff-header">
          <div className="landing-diff-col landing-diff-col-jira">Jira Dashboards</div>
          <div className="landing-diff-col landing-diff-col-lb">Lead Board</div>
        </div>
        {differences.map((diff, index) => (
          <motion.div
            key={index}
            className="landing-diff-row"
            initial={{ opacity: 0, x: -10 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.3 + index * 0.05 }}
          >
            <div className="landing-diff-col landing-diff-col-jira">
              <span className="diff-icon diff-icon-minus">−</span>
              {diff.jira}
            </div>
            <div className="landing-diff-col landing-diff-col-lb">
              <span className="diff-icon diff-icon-plus">+</span>
              {diff.leadboard}
            </div>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
