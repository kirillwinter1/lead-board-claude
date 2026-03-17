import { useState, useMemo } from 'react'
import { TimeInStatusResponse } from '../../api/metrics'
import { StatusBadge } from '../board/StatusBadge'
import { lightenColor } from '../../constants/colors'
import './TimeInStatusTable.css'

interface TimeInStatusTableProps {
  data: TimeInStatusResponse[]
}

type SortKey = 'status' | 'medianHours' | 'p85Hours' | 'p99Hours' | 'avgHours' | 'transitionsCount'

function num(v: number | null | undefined): number {
  return typeof v === 'number' && !isNaN(v) ? v : 0
}

export function TimeInStatusTable({ data }: TimeInStatusTableProps) {
  const [sortKey, setSortKey] = useState<SortKey>('medianHours')
  const [sortAsc, setSortAsc] = useState(false)

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortAsc(!sortAsc)
    } else {
      setSortKey(key)
      setSortAsc(key === 'status') // alpha ascending by default
    }
  }

  const sorted = useMemo(() => {
    if (!data || data.length === 0) return []
    return [...data].sort((a, b) => {
      let cmp = 0
      if (sortKey === 'status') {
        cmp = a.status.localeCompare(b.status)
      } else {
        cmp = num(a[sortKey]) - num(b[sortKey])
      }
      return sortAsc ? cmp : -cmp
    })
  }, [data, sortKey, sortAsc])

  if (!data || data.length === 0) {
    return (
      <div className="chart-section">
        <h3>Time in Status</h3>
        <div className="chart-empty">No status transition data available</div>
      </div>
    )
  }

  // Compute column max for heatmap
  const maxMedian = Math.max(...data.map(d => num(d.medianHours)), 1)
  const maxP85 = Math.max(...data.map(d => num(d.p85Hours)), 1)
  const maxP99 = Math.max(...data.map(d => num(d.p99Hours)), 1)
  const maxAvg = Math.max(...data.map(d => num(d.avgHours)), 1)

  const heatColor = (value: number, max: number): string => {
    if (max === 0) return 'transparent'
    const intensity = Math.min(value / max, 1)
    return lightenColor('#FF5630', 1 - intensity * 0.4) // light red heatmap
  }

  const sortIndicator = (key: SortKey) => {
    if (sortKey !== key) return ''
    return sortAsc ? ' ▲' : ' ▼'
  }

  return (
    <div className="chart-section">
      <h3>Time in Status</h3>
      <table className="tis-table">
        <thead>
          <tr>
            <th className="tis-th-status" onClick={() => handleSort('status')}>
              Status{sortIndicator('status')}
            </th>
            <th className="tis-th-num" onClick={() => handleSort('medianHours')}>
              Median (h){sortIndicator('medianHours')}
            </th>
            <th className="tis-th-num" onClick={() => handleSort('p85Hours')}>
              P85 (h){sortIndicator('p85Hours')}
            </th>
            <th className="tis-th-num" onClick={() => handleSort('p99Hours')}>
              P99 (h){sortIndicator('p99Hours')}
            </th>
            <th className="tis-th-num" onClick={() => handleSort('avgHours')}>
              Avg (h){sortIndicator('avgHours')}
            </th>
            <th className="tis-th-num" onClick={() => handleSort('transitionsCount')}>
              Transitions{sortIndicator('transitionsCount')}
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(item => (
            <tr key={item.status}>
              <td><StatusBadge status={item.status} /></td>
              <td className="tis-td-num" style={{ background: heatColor(num(item.medianHours), maxMedian) }}>
                {num(item.medianHours).toFixed(1)}
              </td>
              <td className="tis-td-num" style={{ background: heatColor(num(item.p85Hours), maxP85) }}>
                {num(item.p85Hours).toFixed(1)}
              </td>
              <td className="tis-td-num" style={{ background: heatColor(num(item.p99Hours), maxP99) }}>
                {num(item.p99Hours).toFixed(1)}
              </td>
              <td className="tis-td-num" style={{ background: heatColor(num(item.avgHours), maxAvg) }}>
                {num(item.avgHours).toFixed(1)}
              </td>
              <td className="tis-td-num">{num(item.transitionsCount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
