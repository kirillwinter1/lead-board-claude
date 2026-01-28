import { useState, FormEvent } from 'react'
import { motion } from 'framer-motion'

export function CTASection() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email || !email.includes('@')) return

    setLoading(true)
    // Имитация отправки
    await new Promise(resolve => setTimeout(resolve, 1000))
    setLoading(false)
    setSubmitted(true)
  }

  return (
    <section id="waitlist" className="landing-section landing-cta">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-cta-title">Готовы доставлять быстрее?</h2>
        <p className="landing-cta-subtitle">
          Оставьте email и мы пригласим вас в бета-версию Lead Board
        </p>

        {submitted ? (
          <motion.div
            className="landing-waitlist-success"
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
          >
            Отлично! Мы свяжемся с вами в ближайшее время.
          </motion.div>
        ) : (
          <form className="landing-waitlist-form" onSubmit={handleSubmit}>
            <input
              type="email"
              className="landing-waitlist-input"
              placeholder="Ваш email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
            />
            <button
              type="submit"
              className="landing-btn landing-btn-primary landing-btn-large"
              disabled={loading}
            >
              {loading ? 'Отправка...' : 'Оставить заявку'}
            </button>
          </form>
        )}

        <p className="landing-waitlist-note">
          Бесплатный период для первых 100 пользователей
        </p>
      </motion.div>
    </section>
  )
}
