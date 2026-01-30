import { motion } from 'framer-motion'

const pilotGoals = [
  { metric: 'Forecast Accuracy', from: '65%', to: '80%+' },
  { metric: 'Schedule Variance', from: '+18%', to: '<10%' },
  { metric: 'Data Quality', from: '31 alert', to: '<10' },
  { metric: 'Role Visibility', from: '0%', to: '100%' }
]

const included = [
  'Полный доступ к Lead Board для одной команды',
  'Настройка под ваши workflow и поля Jira',
  'Еженедельные созвоны для разбора метрик',
  'Приоритетная поддержка в Telegram'
]

export function PilotSection() {
  return (
    <section id="pilot" className="landing-section landing-section-compact">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Пилотный проект</h2>
        <p className="landing-section-subtitle">
          4–6 недель с одной командой. Проверка эффекта на цифрах.
        </p>
      </motion.div>

      <div className="landing-pilot-layout">
        <motion.div
          className="landing-pilot-goals"
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.2 }}
        >
          <h3 className="landing-pilot-goals-title">Цели пилота в метриках</h3>
          <div className="landing-pilot-goals-grid">
            {pilotGoals.map((goal, index) => (
              <div key={index} className="pilot-goal-card">
                <div className="pilot-goal-metric">{goal.metric}</div>
                <div className="pilot-goal-change">
                  <span className="pilot-goal-from">{goal.from}</span>
                  <span className="pilot-goal-arrow">→</span>
                  <span className="pilot-goal-to">{goal.to}</span>
                </div>
              </div>
            ))}
          </div>
        </motion.div>

        <motion.div
          className="landing-pilot-included"
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.3 }}
        >
          <h3 className="landing-pilot-included-title">Что включено</h3>
          <ul className="landing-pilot-list">
            {included.map((item, index) => (
              <li key={index} className="landing-pilot-item">
                <span className="landing-pilot-check">✓</span>
                {item}
              </li>
            ))}
          </ul>
        </motion.div>
      </div>
    </section>
  )
}
