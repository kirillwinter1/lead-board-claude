import { motion } from 'framer-motion'
import { mockEpics } from '../mockData'

export function DemoBoard() {
  return (
    <div className="demo-board">
      {mockEpics.map((epic, index) => (
        <motion.div
          key={epic.key}
          className="demo-epic-card"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: index * 0.1 }}
        >
          <div className="demo-epic-info">
            <span className="demo-epic-key">{epic.key}</span>
            <span className="demo-epic-title">{epic.title}</span>
            <div className="demo-epic-meta">
              <span className="demo-epic-status">
                <span
                  className="demo-epic-status-dot"
                  style={{ background: epic.statusColor }}
                />
                {epic.status}
              </span>
              <span>{epic.storyCount} stories</span>
              <span>Готов: {epic.expectedDone}</span>
            </div>
          </div>
          <div className="demo-epic-progress">
            <div className="demo-progress-bar">
              <div
                className="demo-progress-segment"
                style={{
                  width: `${epic.progress.sa * 0.33}%`,
                  background: '#0052cc'
                }}
              />
              <div
                className="demo-progress-segment"
                style={{
                  width: `${epic.progress.dev * 0.33}%`,
                  background: '#36b37e'
                }}
              />
              <div
                className="demo-progress-segment"
                style={{
                  width: `${epic.progress.qa * 0.33}%`,
                  background: '#ff991f'
                }}
              />
            </div>
          </div>
        </motion.div>
      ))}
    </div>
  )
}
