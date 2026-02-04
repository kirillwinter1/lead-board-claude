import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts'
import { PeriodThroughput } from '../../api/metrics'
import './ThroughputChart.css'

interface ThroughputChartProps {
  data: PeriodThroughput[]
  movingAverage?: number[]
}

export function ThroughputChart({ data, movingAverage }: ThroughputChartProps) {
  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Throughput by Period</h3>
        <div className="chart-empty">No data available for this period</div>
      </div>
    )
  }

  // Combine data with moving average
  const chartData = data.map((period, i) => ({
    name: new Date(period.periodStart).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    epics: period.epics,
    stories: period.stories,
    subtasks: period.subtasks,
    total: period.total,
    ma: movingAverage?.[i] ?? null
  }))

  return (
    <div className="chart-section">
      <h3>Throughput by Period</h3>
      <div className="throughput-chart-container">
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={chartData} margin={{ top: 20, right: 20, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#ebecf0" />
            <XAxis
              dataKey="name"
              tick={{ fontSize: 11, fill: '#6b778c' }}
              tickLine={false}
              axisLine={{ stroke: '#dfe1e6' }}
            />
            <YAxis
              tick={{ fontSize: 11, fill: '#6b778c' }}
              tickLine={false}
              axisLine={{ stroke: '#dfe1e6' }}
              allowDecimals={false}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#172b4d',
                border: 'none',
                borderRadius: 4,
                color: 'white',
                fontSize: 12
              }}
              labelStyle={{ color: 'white', fontWeight: 600 }}
              itemStyle={{ color: 'white' }}
            />
            <Legend
              wrapperStyle={{ fontSize: 12, paddingTop: 10 }}
            />
            <Bar dataKey="subtasks" stackId="a" fill="#00b8d9" name="Subtasks" />
            <Bar dataKey="stories" stackId="a" fill="#0065ff" name="Stories" />
            <Bar dataKey="epics" stackId="a" fill="#6554c0" name="Epics" radius={[4, 4, 0, 0]} />
            {movingAverage && movingAverage.length > 0 && (
              <Line
                type="monotone"
                dataKey="ma"
                stroke="#ff5630"
                strokeWidth={2}
                dot={false}
                name="4-Week MA"
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
