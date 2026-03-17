import { useMemo, ReactElement } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell,
  ResponsiveContainer,
  LabelProps
} from 'recharts'
import { EpicDsr } from '../../api/metrics'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../board/helpers'
import { StatusBadge } from '../board/StatusBadge'
import { useStatusStyles } from '../board/StatusStylesContext'
import {
  DSR_GREEN, DSR_YELLOW, DSR_RED, ESTIMATE_BLUE, FLAGGED_GREY,
  getDsrColor, TEXT_PRIMARY, TEXT_MUTED, TEXT_DISABLED, TEXT_SECONDARY,
  CHART_AXIS, CHART_GRID, CHART_TOOLTIP_BG
} from '../../constants/colors'

interface DsrBreakdownChartProps {
  epics: EpicDsr[]
  jiraBaseUrl?: string
}

interface ChartRow {
  epicKey: string
  summary: string
  status: string
  issueType: string
  estimate: number
  effective: number
  flagged: number
  dsr: number | null
  iconUrl: string
  statusColor: string | null
}

function CustomTooltip({ active, payload }: { active?: boolean; payload?: Array<{ payload?: ChartRow }> }) {
  if (!active || !payload || payload.length === 0) return null
  const row = payload[0]?.payload
  if (!row) return null
  return (
    <div style={{
      background: CHART_TOOLTIP_BG,
      border: 'none',
      borderRadius: 6,
      padding: '10px 14px',
      color: 'white',
      fontSize: 12,
      lineHeight: 1.8,
      maxWidth: 340,
      minWidth: 220
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <img src={row.iconUrl} alt="" style={{ width: 14, height: 14 }} />
        <span style={{ fontWeight: 600 }}>{row.epicKey}</span>
      </div>
      <div style={{ color: TEXT_DISABLED, marginBottom: 6 }}>{row.summary}</div>
      <div style={{ borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ color: TEXT_DISABLED }}>Status:</span> <StatusBadge status={row.status} color={row.statusColor} />
        </div>
        <div style={{ marginTop: 4, display: 'flex', gap: 16 }}>
          <div><span style={{ color: ESTIMATE_BLUE }}>Est:</span> {row.estimate}d</div>
          <div><span style={{ color: getDsrColor(row.dsr) }}>Eff:</span> {row.effective}d</div>
          {row.flagged > 0 && <div><span style={{ color: FLAGGED_GREY }}>Pause:</span> {row.flagged}d</div>}
        </div>
      </div>
      {row.dsr !== null && (
        <div style={{ marginTop: 6, paddingTop: 6, borderTop: '1px solid rgba(255,255,255,0.15)', fontWeight: 700, fontSize: 14, color: getDsrColor(row.dsr) }}>
          DSR {row.dsr.toFixed(2)}
        </div>
      )}
    </div>
  )
}

function getContrastColor(hex: string): string {
  const c = hex.replace('#', '')
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return luminance > 0.6 ? TEXT_PRIMARY : '#ffffff'
}

interface CustomYTickProps {
  x: string | number
  y: string | number
  payload: { index: number }
  chartData: ChartRow[]
  jiraBaseUrl: string | undefined
}

function CustomYTick({ x, y, payload, chartData, jiraBaseUrl }: CustomYTickProps) {
  const index = payload?.index ?? 0
  const row = chartData[index]
  if (!row) return <g />

  const badgeBg = row.statusColor || CHART_AXIS
  const badgeText = row.statusColor ? getContrastColor(row.statusColor) : TEXT_SECONDARY

  const maxSummary = 38
  const shortSummary = row.summary.length > maxSummary
    ? row.summary.substring(0, maxSummary) + '...'
    : row.summary

  const labelWidth = 320

  return (
    <g transform={`translate(${x},${y})`}>
      <foreignObject x={-labelWidth} y={-22} width={labelWidth - 10} height={44}>
        <div
          style={{ display: 'flex', flexDirection: 'column', gap: 2, cursor: jiraBaseUrl ? 'pointer' : 'default' }}
          onClick={() => jiraBaseUrl && window.open(`${jiraBaseUrl}${row.epicKey}`, '_blank', 'noopener,noreferrer')}
        >
          {/* Row 1: Icon + Key + indicator | Status badge */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <img src={row.iconUrl} alt="" style={{ width: 16, height: 16 }} />
              <span style={{ fontWeight: 600, fontSize: 12, color: TEXT_PRIMARY }}>{row.epicKey}</span>
            </div>
            <span style={{
              backgroundColor: badgeBg,
              color: badgeText,
              padding: '1px 5px',
              borderRadius: 3,
              fontSize: 9,
              fontWeight: 500,
              whiteSpace: 'nowrap',
            }}>
              {row.status}
            </span>
          </div>
          {/* Row 2: Summary */}
          <div style={{ fontSize: 11, color: TEXT_MUTED, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {shortSummary}
          </div>
        </div>
      </foreignObject>
    </g>
  )
}

export function DsrBreakdownChart({ epics, jiraBaseUrl }: DsrBreakdownChartProps) {
  const { getIssueTypeIconUrl } = useWorkflowConfig()
  const statusStyles = useStatusStyles()

  const chartData = useMemo<ChartRow[]>(() => {
    return epics
      .filter(e => e.estimateDays !== null && e.estimateDays > 0)
      .map(e => ({
        epicKey: e.epicKey,
        summary: e.summary,
        status: e.status,
        issueType: e.issueType,
        estimate: e.estimateDays as number,
        effective: e.effectiveWorkingDays,
        flagged: e.flaggedDays,
        dsr: e.dsrActual,
        iconUrl: getIssueIcon(e.issueType, getIssueTypeIconUrl(e.issueType)),
        statusColor: statusStyles[e.status]?.color || null
      }))
  }, [epics, getIssueTypeIconUrl, statusStyles])

  if (chartData.length === 0) {
    return (
      <div style={{ background: 'white', borderRadius: 8, border: `1px solid ${CHART_AXIS}`, padding: 20 }}>
        <h3 style={{ margin: '0 0 8px', fontSize: 16, fontWeight: 600, color: TEXT_PRIMARY }}>
          DSR: Estimate vs Actual
        </h3>
        <div style={{ color: TEXT_MUTED, textAlign: 'center', padding: '24px 0' }}>
          No epics with estimates for the selected period.
        </div>
      </div>
    )
  }

  const rowHeight = 64
  const chartHeight = Math.max(chartData.length * rowHeight + 80, 180)
  const maxValue = Math.max(...chartData.map(d => Math.max(d.estimate, d.effective + d.flagged)))

  const renderDsrLabel = (props: LabelProps & { index?: number }): ReactElement | null => {
    const x = Number(props.x) || 0
    const width = Number(props.width) || 0
    const y = Number(props.y) || 0
    const height = Number(props.height) || 0
    const index = Number(props.index) || 0
    const row = chartData[index]
    if (!row || row.dsr === null) return null
    return (
      <text
        x={x + width + 6}
        y={y + height / 2}
        dy={4}
        fill={getDsrColor(row.dsr)}
        fontSize={12}
        fontWeight={700}
      >
        {row.dsr.toFixed(2)}
      </text>
    )
  }

  const handleBarClick = (data: { payload?: ChartRow; epicKey?: string }) => {
    const epicKey = data?.payload?.epicKey || data?.epicKey
    if (jiraBaseUrl && epicKey) {
      window.open(`${jiraBaseUrl}${epicKey}`, '_blank', 'noopener,noreferrer')
    }
  }

  return (
    <div style={{ background: 'white', borderRadius: 8, border: `1px solid ${CHART_AXIS}`, padding: 20 }}>
      <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600, color: TEXT_PRIMARY }}>
        DSR: Estimate vs Actual
      </h3>
      <p style={{ margin: '0 0 16px', fontSize: 12, color: TEXT_MUTED }}>
        DSR = effective days / estimate.{' '}
        <span style={{ color: DSR_GREEN, fontWeight: 600 }}>&#9632;</span> &le; 1.1 (on time),{' '}
        <span style={{ color: DSR_YELLOW, fontWeight: 600 }}>&#9632;</span> &le; 1.5,{' '}
        <span style={{ color: DSR_RED, fontWeight: 600 }}>&#9632;</span> &gt; 1.5.
      </p>

      <ResponsiveContainer width="100%" height={chartHeight}>
        <BarChart
          data={chartData}
          layout="vertical"
          margin={{ top: 8, right: 80, left: 10, bottom: 8 }}
          barGap={2}
          barCategoryGap="25%"
        >
          <XAxis
            type="number"
            tick={{ fontSize: 11, fill: TEXT_MUTED }}
            tickLine={false}
            axisLine={{ stroke: CHART_AXIS }}
            domain={[0, Math.ceil(maxValue * 1.15)]}
            label={{ value: 'Days', position: 'insideBottomRight', offset: -5, fontSize: 11, fill: TEXT_MUTED }}
          />
          <YAxis
            type="category"
            dataKey="epicKey"
            tick={(props) => (
              <CustomYTick {...props} chartData={chartData} jiraBaseUrl={jiraBaseUrl} />
            )}
            tickLine={false}
            axisLine={false}
            width={320}
          />
          <Tooltip
            content={<CustomTooltip />}
            cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          />

          {/* Estimate bar (blue) */}
          <Bar
            dataKey="estimate"
            name="Estimate"
            fill={ESTIMATE_BLUE}
            radius={[0, 3, 3, 0]}
            barSize={12}
            style={{ cursor: jiraBaseUrl ? 'pointer' : 'default' }}
            onClick={handleBarClick}
          />

          {/* Effective working days bar (colored by DSR) */}
          <Bar
            dataKey="effective"
            name="Actual"
            radius={[0, 3, 3, 0]}
            barSize={12}
            label={renderDsrLabel}
            style={{ cursor: jiraBaseUrl ? 'pointer' : 'default' }}
            onClick={handleBarClick}
          >
            {chartData.map((row, idx) => (
              <Cell key={idx} fill={getDsrColor(row.dsr)} />
            ))}
          </Bar>

          {/* Flagged/pause days bar */}
          <Bar
            dataKey="flagged"
            name="Pause"
            fill={FLAGGED_GREY}
            radius={[0, 3, 3, 0]}
            barSize={12}
            style={{ opacity: 0.6 }}
          />
        </BarChart>
      </ResponsiveContainer>

      {/* Legend */}
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        gap: 20,
        marginTop: 4,
        paddingTop: 8,
        borderTop: `1px solid ${CHART_GRID}`,
        fontSize: 12,
        color: TEXT_SECONDARY
      }}>
        {[
          { color: ESTIMATE_BLUE, label: 'Estimate' },
          { color: DSR_GREEN, label: 'Actual (on time)' },
          { color: DSR_YELLOW, label: 'Actual (moderate)' },
          { color: DSR_RED, label: 'Actual (slow)' },
          { color: FLAGGED_GREY, label: 'Pause', opacity: 0.6 },
        ].map(item => (
          <span key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <span style={{ display: 'inline-block', width: 12, height: 12, borderRadius: 2, background: item.color, opacity: item.opacity ?? 1 }} />
            {item.label}
          </span>
        ))}
      </div>
    </div>
  )
}
