import { useState, useEffect } from 'react'
import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { getMonthlyDsr, MonthlyDsrPoint } from '../../api/metrics'
import {
  DSR_GREEN,
  DSR_YELLOW,
  getDsrColor,
  CHART_GRID,
  CHART_AXIS,
  CHART_TICK,
  CHART_TOOLTIP_BG,
  TEXT_MUTED
} from '../../constants/colors'

interface DsrTrendChartProps {
  teamId: number
}

function formatMonth(month: string): string {
  const [year, m] = month.split('-')
  const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const monthIndex = parseInt(m, 10) - 1
  const label = monthNames[monthIndex] || m
  const currentYear = new Date().getFullYear().toString()
  return year !== currentYear ? `${label} '${year.slice(2)}` : label
}

export function DsrTrendChart({ teamId }: DsrTrendChartProps) {
  const [data, setData] = useState<MonthlyDsrPoint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getMonthlyDsr(teamId)
      .then(res => setData(res.months))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [teamId])

  if (loading) {
    return (
      <div className="velocity-section">
        <h3>DSR Trend</h3>
        <div className="velocity-loading">Loading DSR trend...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="velocity-section">
        <h3>DSR Trend</h3>
        <div className="velocity-empty">Failed to load: {error}</div>
      </div>
    )
  }

  const hasData = data.some(d => d.totalEpics > 0)
  if (!hasData) {
    return (
      <div className="velocity-section">
        <h3>DSR Trend</h3>
        <div className="velocity-empty">No epic data available for DSR trend</div>
      </div>
    )
  }

  const chartData = data.map(d => ({
    ...d,
    monthLabel: formatMonth(d.month),
  }))

  const maxEpics = Math.max(...data.map(d => d.totalEpics), 1)
  const maxDsr = Math.max(
    ...data.filter(d => d.avgDsrActual !== null).map(d => d.avgDsrActual!),
    ...data.filter(d => d.avgDsrForecast !== null).map(d => d.avgDsrForecast!),
    1.5
  )

  return (
    <div className="velocity-section">
      <h3>DSR Trend</h3>
      <p className="velocity-description">
        Monthly delivery speed ratio. Line shows avg DSR per month (green ≤ 1.1, yellow ≤ 1.5, red &gt; 1.5). Bars show epic count.
      </p>
      <ResponsiveContainer width="100%" height={320}>
        <ComposedChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
          <XAxis
            dataKey="monthLabel"
            tick={{ fontSize: 12, fill: CHART_TICK }}
            axisLine={{ stroke: CHART_AXIS }}
          />
          <YAxis
            yAxisId="dsr"
            domain={[0, Math.ceil(maxDsr * 10) / 10]}
            tick={{ fontSize: 12, fill: CHART_TICK }}
            axisLine={{ stroke: CHART_AXIS }}
            label={{ value: 'DSR', angle: -90, position: 'insideLeft', style: { fontSize: 12, fill: CHART_TICK } }}
          />
          <YAxis
            yAxisId="epics"
            orientation="right"
            domain={[0, Math.ceil(maxEpics * 1.2)]}
            tick={{ fontSize: 12, fill: CHART_TICK }}
            axisLine={{ stroke: CHART_AXIS }}
            label={{ value: 'Epics', angle: 90, position: 'insideRight', style: { fontSize: 12, fill: CHART_TICK } }}
            allowDecimals={false}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: CHART_TOOLTIP_BG,
              border: 'none',
              borderRadius: 6,
              color: '#fff',
              fontSize: 13,
            }}
            formatter={(value: unknown, name?: string) => {
              if (value === null || value === undefined) return ['—', name || '']
              const num = Number(value)
              if (name === 'Epics') return [num, name]
              return [num.toFixed(2), name || '']
            }}
            labelFormatter={(label) => String(label)}
          />
          <Legend
            wrapperStyle={{ fontSize: 12, color: CHART_TICK }}
          />
          <ReferenceLine
            yAxisId="dsr"
            y={1.0}
            stroke={DSR_GREEN}
            strokeDasharray="4 4"
            label={{ value: '1.0 target', position: 'right', fill: DSR_GREEN, fontSize: 11 }}
          />
          <ReferenceLine
            yAxisId="dsr"
            y={1.1}
            stroke={DSR_YELLOW}
            strokeDasharray="3 3"
            label={{ value: '1.1', position: 'right', fill: DSR_YELLOW, fontSize: 11 }}
          />
          <Bar
            yAxisId="epics"
            dataKey="totalEpics"
            name="Epics"
            fill={CHART_GRID}
            opacity={0.5}
            radius={[3, 3, 0, 0]}
            barSize={28}
          />
          <Line
            yAxisId="dsr"
            type="monotone"
            dataKey="avgDsrActual"
            name="DSR Actual"
            stroke={TEXT_MUTED}
            strokeWidth={2}
            dot={(props: Record<string, unknown>) => {
              const cx = props.cx as number | undefined
              const cy = props.cy as number | undefined
              const payload = props.payload as (MonthlyDsrPoint & { monthLabel: string }) | undefined
              if (!cx || !cy || !payload || payload.avgDsrActual === null) return <></>
              return (
                <circle
                  key={payload.month}
                  cx={cx}
                  cy={cy}
                  r={5}
                  fill={getDsrColor(payload.avgDsrActual)}
                  stroke="#fff"
                  strokeWidth={2}
                />
              )
            }}
            connectNulls={false}
          />
          <Line
            yAxisId="dsr"
            type="monotone"
            dataKey="avgDsrForecast"
            name="DSR Forecast"
            stroke={TEXT_MUTED}
            strokeWidth={1.5}
            strokeDasharray="5 5"
            dot={false}
            connectNulls={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
