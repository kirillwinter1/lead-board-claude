import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts'
import { SingleSelectDropdown } from '../SingleSelectDropdown'
import { THROUGHPUT_MA } from '../../constants/colors'
import './ThroughputChart.css'

/** One throughput line: a labelled, coloured series of per-period values. */
export interface ThroughputSeries {
  key: string
  name: string
  color: string
  points: { periodStart: string; value: number }[]
}

export interface ThroughputModeOption {
  value: string
  label: string
}

interface ThroughputChartProps {
  series: ThroughputSeries[]
  movingAverage?: number[]
  mode: string
  modeOptions: ThroughputModeOption[]
  onModeChange: (mode: string) => void
  loading?: boolean
}

export function ThroughputChart({
  series,
  movingAverage,
  mode,
  modeOptions,
  onModeChange,
  loading = false
}: ThroughputChartProps) {
  // All series share the same periods (same team/range), so the first one
  // drives the x-axis. Each row carries one value per series key + optional MA.
  const periods = series[0]?.points ?? []
  const hasData = periods.length > 0
  const showMovingAverage = !!movingAverage && movingAverage.length > 0

  const chartData = periods.map((period, i) => {
    const row: Record<string, string | number | null> = {
      name: new Date(period.periodStart).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    }
    series.forEach(s => {
      row[s.key] = s.points[i]?.value ?? 0
    })
    if (showMovingAverage) {
      row.ma = movingAverage?.[i] ?? null
    }
    return row
  })

  return (
    <div className="chart-section">
      <div className="throughput-chart-header">
        <h3>Throughput by Period</h3>
        <SingleSelectDropdown
          label="Type"
          options={modeOptions}
          selected={mode}
          onChange={value => onModeChange(value ?? 'epics-stories')}
          placeholder="Epics & Stories"
          allowClear={false}
        />
      </div>

      {!hasData ? (
        <div className="chart-empty">No data available for this period</div>
      ) : (
        <div className="throughput-chart-container" style={{ opacity: loading ? 0.5 : 1 }}>
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
              <Legend wrapperStyle={{ fontSize: 12, paddingTop: 10 }} />
              {series.map(s => (
                <Line
                  key={s.key}
                  type="monotone"
                  dataKey={s.key}
                  stroke={s.color}
                  strokeWidth={2}
                  dot={false}
                  name={s.name}
                />
              ))}
              {showMovingAverage && (
                <Line
                  type="monotone"
                  dataKey="ma"
                  stroke={THROUGHPUT_MA}
                  strokeWidth={2}
                  strokeDasharray="4 4"
                  dot={false}
                  name="4-Week MA"
                />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
