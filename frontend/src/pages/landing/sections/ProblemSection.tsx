import { motion } from 'framer-motion'

const problems = [
  {
    icon: '🌀',
    iconClass: 'red',
    title: 'Хаос в Jira',
    description: 'Задачи теряются, статусы не актуальны, эпики живут своей жизнью. Никто не понимает реальное состояние проекта.'
  },
  {
    icon: '👁️',
    iconClass: 'orange',
    title: 'Нет видимости',
    description: 'Непонятно кто чем занят, где застряли задачи, и почему разработка буксует. Менеджеры в тумане.'
  },
  {
    icon: '⏰',
    iconClass: 'yellow',
    title: 'Сорванные сроки',
    description: 'Заказчики ждут, команда не успевает. Прогнозы не работают, потому что основаны на интуиции, а не данных.'
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

export function ProblemSection() {
  return (
    <section className="landing-section">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Знакомая ситуация?</h2>
        <p className="landing-section-subtitle">
          Большинство команд сталкиваются с этими проблемами каждый день
        </p>
      </motion.div>

      <motion.div
        className="landing-problems"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {problems.map((problem, index) => (
          <motion.div
            key={index}
            className="landing-problem-card"
            variants={itemVariants}
          >
            <div className={`landing-problem-icon ${problem.iconClass}`}>
              {problem.icon}
            </div>
            <h3 className="landing-problem-title">{problem.title}</h3>
            <p className="landing-problem-text">{problem.description}</p>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
