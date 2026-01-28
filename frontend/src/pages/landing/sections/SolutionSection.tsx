import { motion } from 'framer-motion'

export function SolutionSection() {
  return (
    <section className="landing-section landing-solution">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Как это работает</h2>
        <p className="landing-section-subtitle">
          Lead Board подключается к вашей Jira и превращает данные в actionable insights
        </p>
      </motion.div>

      <motion.div
        className="landing-solution-grid"
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.6, delay: 0.2 }}
      >
        <div className="landing-solution-before">
          <div className="landing-solution-label">Было</div>
          <BeforeVisualization />
        </div>

        <div className="landing-solution-arrow">→</div>

        <div className="landing-solution-after">
          <div className="landing-solution-label">Стало</div>
          <AfterVisualization />
        </div>
      </motion.div>
    </section>
  )
}

function BeforeVisualization() {
  const chaosItems = [
    { text: 'PROJ-123', x: 10, y: 5, rotate: -5 },
    { text: 'PROJ-456', x: 60, y: 15, rotate: 8 },
    { text: 'PROJ-789', x: 25, y: 45, rotate: -12 },
    { text: 'PROJ-101', x: 70, y: 55, rotate: 5 },
    { text: 'PROJ-202', x: 15, y: 75, rotate: -8 },
    { text: 'PROJ-303', x: 55, y: 80, rotate: 15 }
  ]

  return (
    <div style={{
      position: 'relative',
      height: '200px',
      overflow: 'hidden'
    }}>
      {chaosItems.map((item, i) => (
        <motion.div
          key={i}
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ delay: i * 0.1 }}
          style={{
            position: 'absolute',
            left: `${item.x}%`,
            top: `${item.y}%`,
            transform: `rotate(${item.rotate}deg)`,
            padding: '8px 12px',
            background: 'var(--landing-bg-alt)',
            border: '1px solid var(--landing-border)',
            borderRadius: '4px',
            fontSize: '0.75rem',
            color: 'var(--landing-muted)',
            whiteSpace: 'nowrap'
          }}
        >
          {item.text}
        </motion.div>
      ))}
      <div style={{
        position: 'absolute',
        bottom: '10px',
        left: '50%',
        transform: 'translateX(-50%)',
        color: '#dc2626',
        fontSize: '0.875rem',
        fontWeight: 500
      }}>
        Где мои задачи?!
      </div>
    </div>
  )
}

function AfterVisualization() {
  const items = [
    { title: 'Авторизация', progress: 75, status: 'В работе' },
    { title: 'Платежи', progress: 30, status: 'Анализ' },
    { title: 'Уведомления', progress: 100, status: 'SA готов' }
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {items.map((item, i) => (
        <motion.div
          key={i}
          initial={{ opacity: 0, x: 20 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.3 + i * 0.1 }}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            padding: '12px',
            background: 'var(--landing-bg-alt)',
            borderRadius: '6px',
            border: '1px solid var(--landing-border)'
          }}
        >
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '0.875rem', fontWeight: 500, marginBottom: '4px' }}>
              {item.title}
            </div>
            <div style={{
              height: '4px',
              background: 'var(--landing-border)',
              borderRadius: '2px',
              overflow: 'hidden'
            }}>
              <div style={{
                width: `${item.progress}%`,
                height: '100%',
                background: '#16a34a',
                borderRadius: '2px'
              }} />
            </div>
          </div>
          <div style={{
            fontSize: '0.75rem',
            color: 'var(--landing-text-secondary)',
            whiteSpace: 'nowrap'
          }}>
            {item.status}
          </div>
        </motion.div>
      ))}
      <div style={{
        textAlign: 'center',
        color: '#16a34a',
        fontSize: '0.875rem',
        fontWeight: 500,
        marginTop: '8px'
      }}>
        Всё под контролем
      </div>
    </div>
  )
}
