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
            Lead Board интегрируется с вашей Jira по API и превращает данные в наглядный дашборд:
            прогресс по эпикам, прогноз сроков, загрузка по ролям и рекомендации для принятия решений.
          </p>
          <ul className="landing-hero-features">
            <li>Подключение за один день</li>
            <li>Не требует изменения ваших Jira-процессов</li>
            <li>Работает как SaaS, так и на ваших серверах, без выхода в сеть</li>
            <li>Соответствует требованиям импортозамещения</li>
          </ul>
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
