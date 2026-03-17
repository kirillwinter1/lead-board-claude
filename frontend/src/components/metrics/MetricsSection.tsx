import { useState, ReactNode } from 'react'
import './MetricsSection.css'

interface MetricsSectionProps {
  id: string
  title: string
  defaultExpanded?: boolean
  children: ReactNode
}

export function MetricsSection({ id, title, defaultExpanded = true, children }: MetricsSectionProps) {
  const storageKey = `metrics-section-${id}`
  const [expanded, setExpanded] = useState(() => {
    const stored = localStorage.getItem(storageKey)
    return stored !== null ? stored === 'true' : defaultExpanded
  })

  const toggle = () => {
    const next = !expanded
    setExpanded(next)
    localStorage.setItem(storageKey, String(next))
  }

  return (
    <div className="metrics-section">
      <button className="metrics-section-header" onClick={toggle}>
        <span className={`metrics-section-chevron ${expanded ? 'expanded' : ''}`}>
          &#9654;
        </span>
        <span className="metrics-section-title">{title}</span>
      </button>
      {expanded && (
        <div className="metrics-section-content">
          {children}
        </div>
      )}
    </div>
  )
}
