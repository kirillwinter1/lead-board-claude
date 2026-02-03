import { motion } from 'framer-motion'
import { useState } from 'react'

function HintTooltip({ children }: { children: React.ReactNode }) {
  const [isVisible, setIsVisible] = useState(false)
  const [position, setPosition] = useState<{ top: number; left: number | 'auto'; right: number | 'auto' }>({
    top: 0,
    left: 0,
    right: 'auto'
  })

  const handleMouseEnter = (e: React.MouseEvent<HTMLSpanElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const tooltipWidth = 420
    const spaceOnRight = window.innerWidth - rect.left

    if (spaceOnRight < tooltipWidth) {
      // Не хватает места справа — рендерим слева
      setPosition({
        top: rect.bottom + 8,
        left: 'auto',
        right: window.innerWidth - rect.right
      })
    } else {
      setPosition({
        top: rect.bottom + 8,
        left: rect.left,
        right: 'auto'
      })
    }
    setIsVisible(true)
  }

  return (
    <>
      <span
        className="results-snapshot-hint"
        onMouseEnter={handleMouseEnter}
        onMouseLeave={() => setIsVisible(false)}
      >
        ?
      </span>
      {isVisible && (
        <div
          className="results-snapshot-tooltip"
          style={{
            top: position.top,
            left: position.left,
            right: position.right
          }}
        >
          {children}
        </div>
      )}
    </>
  )
}

const DSRTooltip = () => (
  <>
    <div className="tooltip-title">Delivery Speed Ratio</div>
    <p className="tooltip-desc">относительная скорость выполнения эпика с учётом его объёма.</p>
    <div className="tooltip-section">Формула:</div>
    <div className="tooltip-fraction">
      <span className="tooltip-fraction-label">DSR =</span>
      <div className="tooltip-fraction-content">
        <span className="tooltip-fraction-top">фактически затраченные на работу человеко-дни</span>
        <span className="tooltip-fraction-bottom">оценка в человеко-днях</span>
      </div>
    </div>
    <div className="tooltip-section">Как читать:</div>
    <ul className="tooltip-list">
      <li><span className="tooltip-value">1.0</span> — базовая скорость выполнения</li>
      <li><span className="tooltip-value green">&lt; 1.0</span> — выполнен быстрее</li>
      <li><span className="tooltip-value red">&gt; 1.0</span> — выполнен медленнее</li>
    </ul>
  </>
)

interface ResultMetric {
  before: string | null
  after: string
  label: React.ReactNode
  tooltip?: React.ReactNode
  detail: string
}

const metrics: ResultMetric[] = [
  {
    before: '3,8x',
    after: '1.1x',
    label: 'Сократили DSR',
    tooltip: <DSRTooltip />,
    detail: 'Поставка стала точной и предсказуемой'
  },
  {
    before: '1,5',
    after: '4',
    label: 'Увеличили Throughput',
    tooltip: 'Throughput (пропускная способность) — сколько эпиков завершает команда за период.',
    detail: 'эпиков в месяц'
  },
  {
    before: '6 мес',
    after: '2 мес',
    label: 'Сократили Lead Time',
    tooltip: 'Lead Time — время от начала работы над эпиком до его завершения.',
    detail: 'Доставка бизнес ценности стала быстрее'
  },
  {
    before: '50%',
    after: '15%',
    label: 'Сократили Buffer time',
    tooltip: 'Buffer time — время, которое задача проводит в ожидании. Высокий буфер — признак непредсказуемости.',
    detail: 'Задачи меньше времени висят в ожидании работы'
  },
  {
    before: null,
    after: '✓',
    label: <>Получили <strong>исключительную</strong> обратную связь</>,
    detail: 'от заказчиков и стейкхолдеров'
  }
]

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.3 }
  }
}

const itemVariants = {
  hidden: { opacity: 0, x: 20 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.3 } }
}

export function ExecutionSnapshot() {
  return (
    <motion.div
      className="results-snapshot"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.2 }}
    >
      <div className="results-snapshot-header">
        <h3 className="results-snapshot-title">Как OneLane помог</h3>
        <p className="results-snapshot-subtitle">Результаты внедрения в production-командах</p>
      </div>

      <motion.div
        className="results-snapshot-grid"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {metrics.map((metric, index) => (
          <motion.div
            key={index}
            className="results-snapshot-card"
            variants={itemVariants}
          >
            <div className="results-snapshot-card-label">
              {metric.label}
              {metric.tooltip && <HintTooltip>{metric.tooltip}</HintTooltip>}
            </div>
            <div className="results-snapshot-card-value">
              {metric.before && (
                <>
                  <span className="results-snapshot-before">{metric.before}</span>
                  <span className="results-snapshot-arrow">→</span>
                </>
              )}
              <span className="results-snapshot-after">{metric.after}</span>
            </div>
            <div className="results-snapshot-card-detail">{metric.detail}</div>
          </motion.div>
        ))}
      </motion.div>
    </motion.div>
  )
}
