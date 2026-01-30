import { motion } from 'framer-motion'

interface HeroSectionProps {
  onRequestAudit: () => void
}

export function HeroSection({ onRequestAudit }: HeroSectionProps) {
  return (
    <section className="landing-section landing-hero">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="landing-hero-centered"
      >
        <h1 className="landing-hero-title">
          Сделайте сроки поставки
          <br />
          <span>предсказуемыми</span>
        </h1>
        <p className="landing-hero-subtitle">
          Lead Board превращает Jira-данные в управляемую приоритезацию, прогноз сроков,
          видимость «бутылочных горлышек» по ролям.
        </p>
        <div className="landing-hero-box">
          <span className="landing-hero-box-icon">⚡</span>
          <div>
            <strong>Простое внедрение за 1 день</strong>, без изменения процессов.
            <br />
            Работает как SaaS, так и на внутренних серверах.
          </div>
        </div>
        <button
          onClick={onRequestAudit}
          className="landing-btn landing-btn-primary landing-btn-large"
        >
          Запросить разбор
        </button>
      </motion.div>
    </section>
  )
}
