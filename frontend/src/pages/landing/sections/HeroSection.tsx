import { motion } from 'framer-motion'
import { mockEpics } from '../mockData'

export function HeroSection() {
  const scrollToWaitlist = () => {
    const ctaSection = document.getElementById('waitlist')
    if (ctaSection) {
      ctaSection.scrollIntoView({ behavior: 'smooth' })
    }
  }

  const scrollToDemo = () => {
    const demoSection = document.getElementById('demo')
    if (demoSection) {
      demoSection.scrollIntoView({ behavior: 'smooth' })
    }
  }

  return (
    <section className="landing-section landing-hero">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
      >
        <h1 className="landing-hero-title">
          Перестаньте управлять Jira.
          <br />
          <span>Начните доставлять.</span>
        </h1>
        <p className="landing-hero-subtitle">
          Lead Board превращает хаос Jira в понятные прогнозы,
          прозрачные метрики и реальные сроки доставки для вашей команды.
        </p>
        <div className="landing-hero-actions">
          <button
            onClick={scrollToWaitlist}
            className="landing-btn landing-btn-primary landing-btn-large"
          >
            Попробовать бесплатно
          </button>
          <button
            onClick={scrollToDemo}
            className="landing-btn landing-btn-secondary landing-btn-large"
          >
            Смотреть демо
          </button>
        </div>
      </motion.div>

      <motion.div
        className="landing-hero-visual"
        initial={{ opacity: 0, y: 40 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.2 }}
      >
        <div className="landing-hero-mockup">
          <HeroMockup />
        </div>
      </motion.div>
    </section>
  )
}

function HeroMockup() {
  return (
    <div style={{ padding: '24px' }}>
      <div style={{
        display: 'flex',
        gap: '12px',
        marginBottom: '20px',
        borderBottom: '1px solid var(--landing-border)',
        paddingBottom: '16px'
      }}>
        <div style={{
          padding: '8px 16px',
          background: 'var(--landing-accent)',
          color: 'white',
          borderRadius: '6px',
          fontSize: '0.875rem',
          fontWeight: 500
        }}>
          Board
        </div>
        <div style={{
          padding: '8px 16px',
          color: 'var(--landing-text-secondary)',
          fontSize: '0.875rem'
        }}>
          Timeline
        </div>
        <div style={{
          padding: '8px 16px',
          color: 'var(--landing-text-secondary)',
          fontSize: '0.875rem'
        }}>
          Metrics
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {mockEpics.map((epic, index) => (
          <motion.div
            key={epic.key}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.4, delay: 0.4 + index * 0.1 }}
            style={{
              background: 'var(--landing-bg)',
              border: '1px solid var(--landing-border)',
              borderRadius: '8px',
              padding: '16px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}
          >
            <div>
              <div style={{
                fontSize: '0.75rem',
                color: 'var(--landing-muted)',
                marginBottom: '4px'
              }}>
                {epic.key}
              </div>
              <div style={{ fontWeight: 500 }}>{epic.title}</div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '0.875rem',
                color: 'var(--landing-text-secondary)'
              }}>
                <span style={{
                  width: '8px',
                  height: '8px',
                  borderRadius: '50%',
                  background: epic.statusColor
                }} />
                {epic.status}
              </div>
              <div style={{
                width: '100px',
                height: '6px',
                background: 'var(--landing-border)',
                borderRadius: '3px',
                overflow: 'hidden',
                display: 'flex'
              }}>
                <div style={{
                  width: `${epic.progress.sa * 0.33}%`,
                  height: '100%',
                  background: '#0052cc'
                }} />
                <div style={{
                  width: `${epic.progress.dev * 0.33}%`,
                  height: '100%',
                  background: '#36b37e'
                }} />
                <div style={{
                  width: `${epic.progress.qa * 0.33}%`,
                  height: '100%',
                  background: '#ff991f'
                }} />
              </div>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}
