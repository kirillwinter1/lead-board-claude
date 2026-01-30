import { motion } from 'framer-motion'

const problems = [
  {
    text: 'Сроки эпиков регулярно выходят за план',
    metric: '+15–30%',
    metricLabel: 'типичная variance'
  },
  {
    text: 'Forecast accuracy ниже целевого уровня',
    metric: '60–70%',
    metricLabel: 'vs target 85%'
  },
  {
    text: 'Bottleneck по ролям обнаруживается слишком поздно',
    metric: 'SA/QA',
    metricLabel: 'чаще всего'
  },
  {
    text: 'Статус «в работе» без фазовой видимости',
    metric: '2–4 нед',
    metricLabel: 'avg cycle time'
  },
  {
    text: 'Руководство опирается на ощущения, не на данные',
    metric: '0',
    metricLabel: 'автометрик'
  }
]

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08 }
  }
}

const itemVariants = {
  hidden: { opacity: 0, x: -20 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.3 } }
}

export function ProblemSection() {
  return (
    <section id="problem" className="landing-section landing-section-compact">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Типичные проблемы execution control</h2>
        <p className="landing-section-subtitle">
          Цифры из реальных аудитов команд 10–50 человек
        </p>
      </motion.div>

      <motion.div
        className="landing-problem-grid"
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
      >
        {problems.map((problem, index) => (
          <motion.div
            key={index}
            className="landing-problem-card-v2"
            variants={itemVariants}
          >
            <div className="problem-card-metric">
              <span className="problem-card-metric-value">{problem.metric}</span>
              <span className="problem-card-metric-label">{problem.metricLabel}</span>
            </div>
            <p className="problem-card-text">{problem.text}</p>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
