import React from 'react'
import './EmptyState.css'

interface EmptyStateProps {
  /** Primary message. */
  message: string
  /** Optional secondary line — a hint, or where an action/children can be placed. */
  hint?: React.ReactNode
  /** Optional leading icon / illustration. */
  icon?: React.ReactNode
  /**
   * page: large centred block for whole-screen empties (mirrors the .empty rule).
   * inline: compact placeholder for cards, charts and table sections.
   * Default 'page'.
   */
  variant?: 'page' | 'inline'
  /** Extra inline styles merged onto the wrapper. */
  style?: React.CSSProperties
  /** Optional action node (e.g. a button), rendered below the hint. */
  children?: React.ReactNode
}

/**
 * F91 — shared empty state. Consolidates the many one-off `.empty`,
 * `.empty-state`, `.chart-empty`, `.*-empty` placeholders into one component.
 */
export function EmptyState({ message, hint, icon, variant = 'page', style, children }: EmptyStateProps) {
  return (
    <div className={`empty-state empty-state--${variant}`} style={style}>
      {icon && <div className="empty-state-icon" aria-hidden="true">{icon}</div>}
      <div className="empty-state-message">{message}</div>
      {hint && <div className="empty-state-hint">{hint}</div>}
      {children && <div className="empty-state-action">{children}</div>}
    </div>
  )
}
