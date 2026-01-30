import { motion } from 'framer-motion'
import { ExecutionSnapshot } from '../components/ExecutionSnapshot'

interface HeroSectionProps {
  onRequestAudit: () => void
}

export function HeroSection({ onRequestAudit }: HeroSectionProps) {
  return (
    <section className="landing-section landing-hero">
      <div className="landing-hero-split">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="landing-hero-content"
        >
          <h1 className="landing-hero-title landing-hero-title-left">
            Сделайте сроки поставки
            <br />
            <span>предсказуемыми</span>
          </h1>
          <p className="landing-hero-subtitle landing-hero-subtitle-left">
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
          <div className="landing-hero-actions landing-hero-actions-left">
            <button
              onClick={onRequestAudit}
              className="landing-btn landing-btn-primary landing-btn-large"
            >
              Запросить разбор
            </button>
          </div>
        </motion.div>

        <div className="landing-hero-visual">
          <ExecutionSnapshot />
        </div>
      </div>
    </section>
  )
}
