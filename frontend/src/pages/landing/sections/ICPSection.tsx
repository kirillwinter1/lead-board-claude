import { motion } from 'framer-motion'

const roleRows = [
  ['CTO', 'Head of Development'],
  ['Team Lead', 'Product Owner'],
  ['Project Manager', 'Delivery Manager']
]

const criteriaRows = [
  ['3+ команд разработки', 'Регулярные вопросы «когда будет готово?»'],
  ['Jira — основной трекер', 'Много заказчиков — сложно расставить приоритеты'],
  ['Эпики с внешними дедлайнами', 'Нужна видимость загрузки по ролям']
]

export function ICPSection() {
  return (
    <section className="landing-section landing-section-compact landing-icp">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">
          Подходит командам, где поставка уже стала управленческой задачей
        </h2>
      </motion.div>

      <motion.div
        className="landing-icp-content"
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5, delay: 0.2 }}
      >
        <div className="landing-icp-card">
          <h3 className="landing-icp-card-title">Для кого</h3>
          <div className="landing-icp-roles">
            {roleRows.map((row, rowIndex) => (
              <div key={rowIndex} className="landing-icp-role-row">
                {row.map((role, index) => (
                  <span key={index} className="landing-icp-role-badge">{role}</span>
                ))}
              </div>
            ))}
          </div>
        </div>

        <div className="landing-icp-card">
          <h3 className="landing-icp-card-title">Ваш случай, если</h3>
          <div className="landing-icp-tags">
            {criteriaRows.map((row, rowIndex) => (
              <div key={rowIndex} className="landing-icp-tag-row">
                {row.map((item, index) => (
                  <span key={index} className="landing-icp-tag">{item}</span>
                ))}
              </div>
            ))}
          </div>
        </div>
      </motion.div>
    </section>
  )
}
