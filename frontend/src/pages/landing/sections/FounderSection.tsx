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
          Инструмент создал действующий IT lead
        </h2>
        <p className="landing-founder-text">
          OneLane вырос из реальной задачи управления поставкой в крупной IT-организации:
          прогнозы сроков, узкие места по ролям, точность оценки и управляемость проектами.
          Впитал в себя весь опыт и знания накопленные годами.
          <br />
          Сначала — метод и практика. Потом — инструмент.
        </p>
      </motion.div>
    </section>
  )
}
