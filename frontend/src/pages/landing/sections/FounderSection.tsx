import { motion } from 'framer-motion'

export function FounderSection() {
  return (
    <section className="landing-section landing-founder">
      <motion.div
        className="landing-founder-content"
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-founder-title">
          Инструмент создан практиком delivery
        </h2>
        <p className="landing-founder-text">
          Lead Board вырос из реальной задачи управления поставкой в крупной IT-организации:
          прогнозы сроков, узкие места по ролям, точность оценки и управляемость эпиков.
          Сначала — метод и практика. Потом — инструмент.
        </p>
      </motion.div>
    </section>
  )
}
