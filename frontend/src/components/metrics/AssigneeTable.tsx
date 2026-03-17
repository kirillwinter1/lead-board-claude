import { useState, useMemo } from 'react'
import { AssigneeMetrics } from '../../api/metrics'
import { getDsrColor, DSR_GREEN, DSR_YELLOW, DSR_RED } from '../../constants/colors'
import './AssigneeTable.css'

interface AssigneeTableProps {
  data: AssigneeMetrics[]
}

type SortCol = 'displayName' | 'issuesClosed' | 'avgLeadTimeDays' | 'avgCycleTimeDays' | 'personalDsr' | 'trend'

export function AssigneeTable({ data }: AssigneeTableProps) {
  const [sortCol, setSortCol] = useState<SortCol>('issuesClosed')
  const [sortAsc, setSortAsc] = useState(false)
  const [search, setSearch] = useState('')

  const handleSort = (col: SortCol) => {
    if (sortCol === col) {
      setSortAsc(!sortAsc)
    } else {
      setSortCol(col)
      setSortAsc(col === 'displayName')
    }
  }

  const filtered = useMemo(() => {
    if (!search.trim()) return data
    const q = search.toLowerCase()
    return data.filter(a => a.displayName.toLowerCase().includes(q))
  }, [data, search])

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      let cmp = 0
      if (sortCol === 'displayName') {
        cmp = a.displayName.localeCompare(b.displayName)
      } else if (sortCol === 'trend') {
        const order = { UP: 0, STABLE: 1, DOWN: 2 }
        cmp = (order[a.trend || 'STABLE'] ?? 1) - (order[b.trend || 'STABLE'] ?? 1)
      } else {
        const av = a[sortCol] as number | null ?? -Infinity
        const bv = b[sortCol] as number | null ?? -Infinity
        cmp = (av as number) - (bv as number)
      }
      return sortAsc ? cmp : -cmp
    })
  }, [filtered, sortCol, sortAsc])

  if (data.length === 0) {
    return (
      <div className="chart-section">
        <h3>By Team Member</h3>
        <div className="chart-empty">No assignee data available</div>
      </div>
    )
  }

  const sortIndicator = (col: SortCol) => {
    if (sortCol !== col) return ''
    return sortAsc ? ' ▲' : ' ▼'
  }

  const getTrendIcon = (trend: string | null) => {
    switch (trend) {
      case 'UP':
        return <span className="trend-icon trend-up" title="Improving">+</span>
      case 'DOWN':
        return <span className="trend-icon trend-down" title="Declining">-</span>
      case 'STABLE':
      default:
        return <span className="trend-icon trend-stable" title="Stable">=</span>
    }
  }

  return (
    <div className="chart-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <h3 style={{ margin: 0 }}>By Team Member</h3>
        <input
          type="text"
          placeholder="Search member..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="assignee-search"
        />
      </div>
      <table className="metrics-table">
        <thead>
          <tr>
            <th className="sortable" onClick={() => handleSort('displayName')}>
              Name{sortIndicator('displayName')}
            </th>
            <th className="sortable" title="Number of issues completed" onClick={() => handleSort('issuesClosed')}>
              Closed{sortIndicator('issuesClosed')}
            </th>
            <th className="sortable" title="Average lead time" onClick={() => handleSort('avgLeadTimeDays')}>
              Lead Time{sortIndicator('avgLeadTimeDays')}
            </th>
            <th className="sortable" title="Average cycle time" onClick={() => handleSort('avgCycleTimeDays')}>
              Cycle Time{sortIndicator('avgCycleTimeDays')}
            </th>
            <th className="sortable" title="Personal DSR" onClick={() => handleSort('personalDsr')}>
              DSR{sortIndicator('personalDsr')}
            </th>
            <th className="sortable" onClick={() => handleSort('trend')}>
              Trend{sortIndicator('trend')}
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(a => {
            const issueDelta = a.issuesClosed - a.issuesClosedPrev

            return (
              <tr key={a.accountId} className={a.isOutlier ? 'outlier-row' : ''}>
                <td>
                  {a.displayName}
                  {a.isOutlier && <span className="outlier-badge" title="Outlier: high DSR or slow cycle time">!</span>}
                </td>
                <td className="metrics-table-number">
                  {a.issuesClosed}
                  {a.issuesClosedPrev > 0 && (
                    <span className={`delta-badge ${issueDelta > 0 ? 'delta-up' : issueDelta < 0 ? 'delta-down' : ''}`}>
                      {issueDelta > 0 ? '+' : ''}{issueDelta}
                    </span>
                  )}
                </td>
                <td className="metrics-table-number">
                  {a.avgLeadTimeDays != null ? `${a.avgLeadTimeDays.toFixed(1)}d` : '—'}
                </td>
                <td className="metrics-table-number">
                  {a.avgCycleTimeDays != null ? `${a.avgCycleTimeDays.toFixed(1)}d` : '—'}
                  {a.avgCycleTimePrev != null && a.avgCycleTimeDays != null && (
                    <span className={`delta-badge ${a.avgCycleTimeDays <= a.avgCycleTimePrev ? 'delta-up' : 'delta-down'}`}>
                      {a.avgCycleTimeDays <= a.avgCycleTimePrev ? '-' : '+'}{Math.abs(a.avgCycleTimeDays - a.avgCycleTimePrev).toFixed(1)}d
                    </span>
                  )}
                </td>
                <td className="metrics-table-number">
                  {a.personalDsr !== null ? (
                    <span style={{ color: getDsrColor(a.personalDsr) }}>
                      {a.personalDsr.toFixed(2)}
                    </span>
                  ) : (
                    <span className="metrics-na">-</span>
                  )}
                </td>
                <td className="metrics-table-center">
                  {getTrendIcon(a.trend)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <div className="assignee-table-legend">
        <span className="assignee-legend-item">
          <strong>DSR</strong>: ≤1.1 <span style={{ color: DSR_GREEN }}>good</span>,
          ≤1.5 <span style={{ color: DSR_YELLOW }}>ok</span>,
          &gt;1.5 <span style={{ color: DSR_RED }}>slow</span>
        </span>
        <span className="assignee-legend-item">
          <strong>Trend</strong>:
          <span className="trend-icon trend-up">+</span> improving,
          <span className="trend-icon trend-stable">=</span> stable,
          <span className="trend-icon trend-down">-</span> declining
        </span>
      </div>
    </div>
  )
}
