import { motion } from 'framer-motion'
import { mockTimelineData } from '../mockData'

export function DemoTimeline() {
  const weeks = ['Нед 1', 'Нед 2', 'Нед 3', 'Нед 4', 'Нед 5', 'Нед 6', 'Нед 7', 'Нед 8']
  const totalDays = 56

  return (
    <div className="demo-timeline">
      <div className="demo-timeline-header">
        {weeks.map((week, i) => (
          <div key={i} className="demo-timeline-week">{week}</div>
        ))}
      </div>

      {mockTimelineData.map((row, index) => (
        <motion.div
          key={index}
          className="demo-timeline-row"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: index * 0.15 }}
        >
          <div className="demo-timeline-label">{row.epic}</div>
          <div className="demo-timeline-bars">
            {row.phases.map((phase, phaseIndex) => (
              <motion.div
                key={phaseIndex}
                className="demo-timeline-bar"
                style={{
                  left: `${(phase.start / totalDays) * 100}%`,
                  width: `${(phase.duration / totalDays) * 100}%`,
                  background: phase.color
                }}
                initial={{ scaleX: 0, originX: 0 }}
                animate={{ scaleX: 1 }}
                transition={{ delay: 0.3 + index * 0.15 + phaseIndex * 0.1, duration: 0.4 }}
              >
                {phase.phase}
              </motion.div>
            ))}
          </div>
        </motion.div>
      ))}

      <div style={{
        display: 'flex',
        justifyContent: 'center',
        gap: '24px',
        marginTop: '24px',
        fontSize: '0.875rem'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '16px', height: '16px', background: '#0052cc', borderRadius: '4px' }} />
          <span style={{ color: 'var(--landing-text-secondary)' }}>SA (Анализ)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '16px', height: '16px', background: '#36b37e', borderRadius: '4px' }} />
          <span style={{ color: 'var(--landing-text-secondary)' }}>DEV (Разработка)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '16px', height: '16px', background: '#ff991f', borderRadius: '4px' }} />
          <span style={{ color: 'var(--landing-text-secondary)' }}>QA (Тестирование)</span>
        </div>
      </div>
    </div>
  )
}
