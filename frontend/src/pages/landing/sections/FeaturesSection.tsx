import { motion } from 'framer-motion'

const features = [
  {
    icon: '📋',
    title: 'Единая доска',
    description: 'Epic → Story → Subtask в одном месте. Видите всю иерархию задач и понимаете, где затор.'
  },
  {
    icon: '📊',
    title: 'Timeline',
    description: 'Gantt-диаграмма с фазами SA/DEV/QA. Видите, когда каждый эпик будет готов.'
  },
  {
    icon: '⚡',
    title: 'Автоприоритизация',
    description: 'AutoScore расставляет приоритеты на основе бизнес-ценности, срочности и зависимостей.'
  },
  {
    icon: '🎯',
    title: 'Прогноз сроков',
    description: 'Когда эпик будет готов? Forecast на основе реальной скорости команды, а не wishful thinking.'
  },
  {
    icon: '📈',
    title: 'Метрики команды',
    description: 'Lead Time to Change, throughput, точность прогнозов. Данные вместо ощущений.'
  },
  {
    icon: '🃏',
    title: 'Planning Poker',
    description: 'Оценка задач в реальном времени. Никаких Excel-таблиц и переписок в Slack.'
  }
]

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1
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

export function FeaturesSection() {
  return (
    <section className="landing-section">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Всё для управления доставкой</h2>
        <p className="landing-section-subtitle">
          Инструменты, которые нужны техлиду и менеджеру каждый день
        </p>
      </motion.div>

      <motion.div
        className="landing-features"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {features.map((feature, index) => (
          <motion.div
            key={index}
            className="landing-feature-card"
            variants={itemVariants}
          >
            <div className="landing-feature-icon">{feature.icon}</div>
            <h3 className="landing-feature-title">{feature.title}</h3>
            <p className="landing-feature-text">{feature.description}</p>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
