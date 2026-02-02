import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { DemoBoard } from '../components/DemoBoard'
import { DemoTimeline } from '../components/DemoTimeline'
import { DemoMetrics } from '../components/DemoMetrics'

type TabType = 'board' | 'timeline' | 'metrics'

interface MethodSectionProps {
  onRequestDemo?: () => void
}

const methods = [
  {
    id: 'board' as TabType,
    title: 'Board',
    values: [
      'Расчёт приоритета и автосортировка',
      'Прогноз даты завершения и риски',
      'Прогресс и статусы по ролям',
      'Ручная приоритизация drag-and-drop'
    ]
  },
  {
    id: 'timeline' as TabType,
    title: 'Timeline',
    values: [
      'Gantt по фазам и ролям',
      'Видимость bottlenecks',
      'Прогноз завершения эпиков',
      'Перекос загрузки команды'
    ]
  },
  {
    id: 'metrics' as TabType,
    title: 'Metrics',
    values: [
      'Все полезные метрики на одном экране',
      'Эффективность команды',
      'Эффективность сотрудника',
      'Точность оценок'
    ]
  }
]

export function MethodSection({ onRequestDemo }: MethodSectionProps) {
  const [activeTab, setActiveTab] = useState<TabType>('board')
  const [highlightedIndex, setHighlightedIndex] = useState<number | null>(null)
  const activeMethod = methods.find(m => m.id === activeTab)

  return (
    <section id="method" className="landing-section landing-section-compact landing-method">
      <div className="landing-method-header">
        <div className="landing-method-header-left">
          <motion.h2
            className="landing-section-title landing-section-title-left"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            Как Lead Board делает поставку управляемой
          </motion.h2>

          <div className="landing-method-tabs landing-method-tabs-left">
            {methods.map((method) => (
              <button
                key={method.id}
                className={`landing-method-tab ${activeTab === method.id ? 'active' : ''}`}
                onClick={() => setActiveTab(method.id)}
              >
                {method.title}
              </button>
            ))}
          </div>
        </div>

        <motion.div
          className="landing-method-values-box"
          key={activeTab}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
        >
          <ul className="landing-method-values-list">
            {activeMethod?.values.map((value, index) => (
              <li
                key={index}
                className={`landing-method-value-item ${highlightedIndex === index ? 'highlighted' : ''}`}
              >
                <span className="landing-method-value-bullet">→</span>
                {value}
                {activeTab === 'board' && index === 3 && (
                  <span className="landing-method-hint" data-tooltip="Перетащите эпик вверх или вниз, чтобы изменить приоритет выполнения">?</span>
                )}
              </li>
            ))}
          </ul>
        </motion.div>
      </div>

      <div className="landing-method-content-full">
        <div className="landing-method-screen">
          <AnimatePresence mode="wait">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="landing-method-demo"
            >
              {activeTab === 'board' && <DemoBoard onHighlight={setHighlightedIndex} />}
              {activeTab === 'timeline' && <DemoTimeline onHighlight={setHighlightedIndex} />}
              {activeTab === 'metrics' && <DemoMetrics />}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>

      <motion.div
        className="landing-method-cta"
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.5 }}
      >
        <p className="landing-method-cta-text">
          Хотите увидеть больше возможностей? Оставьте заявку — мы свяжемся с вами
        </p>
        <button className="landing-btn landing-btn-primary" onClick={onRequestDemo}>
          Оставить заявку
        </button>
      </motion.div>
    </section>
  )
}
