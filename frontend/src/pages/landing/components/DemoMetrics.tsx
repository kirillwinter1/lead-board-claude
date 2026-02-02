import { motion } from 'framer-motion'
import { DsrGauge } from '../../../components/metrics/DsrGauge'
import { MetricCard } from '../../../components/metrics/MetricCard'
import { DemoRoleLoad } from './DemoRoleLoad'
import { mockMetrics } from '../mockData'

export function DemoMetrics() {
  return (
    <div className="demo-metrics">
      <motion.div
        className="demo-metrics-row"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <div className="demo-metrics-gauge-small">
          <DsrGauge
            value={mockMetrics.ltcActual}
            title="DSR Actual"
            subtitle={`среднее по ${mockMetrics.totalEpics} эпикам`}
            tooltip="Delivery Speed Ratio — относительная скорость выполнения эпика с учётом объёма. 1.0 — норма, меньше — быстрее, больше — медленнее."
          />
        </div>
        <div className="demo-metrics-gauge-small">
          <DsrGauge
            value={mockMetrics.ltcForecast}
            title="DSR Forecast"
            subtitle="прогнозируемый DSR"
            tooltip="Прогнозируемый Delivery Speed Ratio на основе текущего прогресса и оставшихся задач."
          />
        </div>
        <MetricCard
          title="Throughput"
          value={`${mockMetrics.epicsCompleted}`}
          subtitle="epics за месяц"
          tooltip="Количество завершённых эпиков за текущий период"
        />
        <MetricCard
          title="Story Lead Time"
          value="3.2 дня"
          subtitle="среднее по stories"
          trend="down"
          tooltip="Среднее время выполнения story от создания до завершения"
        />
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
      >
        <DemoRoleLoad />
      </motion.div>
    </div>
  )
}
