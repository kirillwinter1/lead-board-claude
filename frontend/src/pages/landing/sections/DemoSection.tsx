import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { DemoBoard } from '../components/DemoBoard'
import { DemoTimeline } from '../components/DemoTimeline'
import { DemoMetrics } from '../components/DemoMetrics'

type TabType = 'board' | 'timeline' | 'metrics'

const tabs: { id: TabType; label: string }[] = [
  { id: 'board', label: 'Доска' },
  { id: 'timeline', label: 'Timeline' },
  { id: 'metrics', label: 'Метрики' }
]

export function DemoSection() {
  const [activeTab, setActiveTab] = useState<TabType>('board')

  return (
    <section id="demo" className="landing-section landing-demo">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <h2 className="landing-section-title">Посмотрите в действии</h2>
        <p className="landing-section-subtitle">
          Интерактивное демо основных возможностей Lead Board
        </p>
      </motion.div>

      <div className="landing-demo-tabs">
        {tabs.map(tab => (
          <button
            key={tab.id}
            className={`landing-demo-tab ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="landing-demo-content">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
          >
            {activeTab === 'board' && <DemoBoard />}
            {activeTab === 'timeline' && <DemoTimeline />}
            {activeTab === 'metrics' && <DemoMetrics />}
          </motion.div>
        </AnimatePresence>
      </div>
    </section>
  )
}
