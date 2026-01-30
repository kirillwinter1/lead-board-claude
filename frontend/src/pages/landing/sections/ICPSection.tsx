import { motion } from 'framer-motion'

const icpLeft = [
  '3+ команд разработки',
  'Эпики с внешними дедлайнами',
  'Jira — основной трекер'
]

const icpRight = [
  'Регулярные вопросы «когда будет готово?»',
  'Нужна видимость загрузки по ролям',
  'Много заказчиков — сложно расставить приоритеты'
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
        className="landing-icp-grid"
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5, delay: 0.2 }}
      >
        <div className="landing-icp-column">
          {icpLeft.map((item, index) => (
            <div key={index} className="landing-icp-item">
              <span className="landing-icp-check">✓</span>
              <span>{item}</span>
            </div>
          ))}
        </div>
        <div className="landing-icp-column">
          {icpRight.map((item, index) => (
            <div key={index} className="landing-icp-item">
              <span className="landing-icp-check">✓</span>
              <span>{item}</span>
            </div>
          ))}
        </div>
      </motion.div>
    </section>
  )
}
