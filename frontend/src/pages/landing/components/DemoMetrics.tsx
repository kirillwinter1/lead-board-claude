import { motion } from 'framer-motion'
import { DsrGauge } from '../../../components/metrics/DsrGauge'
import { MetricCard } from '../../../components/metrics/MetricCard'
import { mockMetrics } from '../mockData'

export function DemoMetrics() {
  return (
    <div className="demo-metrics">
      <motion.div
        className="demo-metrics-gauges"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <DsrGauge
          value={mockMetrics.ltcActual}
          title="DSR Actual"
          subtitle={`среднее по ${mockMetrics.totalEpics} эпикам`}
          tooltip="Delivery Speed Ratio — относительная скорость выполнения эпика с учётом объёма. 1.0 — норма, меньше — быстрее, больше — медленнее."
        />
        <DsrGauge
          value={mockMetrics.ltcForecast}
          title="DSR Forecast"
          subtitle="прогнозируемый DSR"
          tooltip="Прогнозируемый Delivery Speed Ratio на основе текущего прогресса и оставшихся задач."
        />
      </motion.div>

      <motion.div
        className="demo-metrics-cards"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.15 }}
      >
        <MetricCard
          title="Throughput"
          value={mockMetrics.throughput}
          subtitle={`${mockMetrics.storiesCompleted} stories, ${mockMetrics.epicsCompleted} epics за спринт`}
          tooltip="Количество завершённых задач за текущий период"
        />
        <MetricCard
          title="On-Time Rate"
          value={`${mockMetrics.onTimeRate}%`}
          subtitle={`${mockMetrics.onTimeCount} из ${mockMetrics.totalEpics} эпиков`}
          trend="up"
          tooltip="Процент эпиков, завершённых в срок или раньше"
        />
        <MetricCard
          title="Velocity"
          value="42 SP"
          subtitle="среднее за 4 спринта"
          tooltip="Средняя скорость команды в story points за спринт"
        />
        <MetricCard
          title="Cycle Time"
          value="3.2 дня"
          subtitle="от In Progress до Done"
          trend="down"
          tooltip="Среднее время выполнения одной задачи"
        />
      </motion.div>
    </div>
  )
}
