import { useState } from 'react'
import { useGuideLang } from './GuideLanguageContext'
import { GuideSidebarItem } from '../../guide/types'
import './GuideSidebar.css'

interface Props {
  items: GuideSidebarItem[]
  activeId: string
  onNavigate: (id: string) => void
}

export function GuideSidebar({ items, activeId, onNavigate }: Props) {
  return (
    <nav className="guide-sidebar-nav">
      {items.map(item => (
        <SidebarGroup key={item.id} item={item} activeId={activeId} onNavigate={onNavigate} />
      ))}
    </nav>
  )
}

function SidebarGroup({ item, activeId, onNavigate }: {
  item: GuideSidebarItem
  activeId: string
  onNavigate: (id: string) => void
}) {
  const { t } = useGuideLang()
  const [expanded, setExpanded] = useState(true)
  const hasChildren = item.children && item.children.length > 0
  const isActive = item.id === activeId
  const isChildActive = item.children?.some(c => c.id === activeId) ?? false

  return (
    <div className="sidebar-group">
      <button
        className={`sidebar-item${isActive ? ' active' : ''}${isChildActive ? ' child-active' : ''}${hasChildren ? ' has-children' : ''}`}
        onClick={() => {
          if (hasChildren) setExpanded(!expanded)
          onNavigate(item.id)
        }}
      >
        {hasChildren && (
          <span className={`sidebar-arrow${expanded ? ' expanded' : ''}`}>&#9656;</span>
        )}
        <span className="sidebar-item-text">{t(item.titleRu, item.titleEn)}</span>
      </button>
      {hasChildren && expanded && (
        <div className="sidebar-children">
          {item.children!.map(child => (
            <button
              key={child.id}
              className={`sidebar-item sidebar-child${child.id === activeId ? ' active' : ''}`}
              onClick={() => onNavigate(child.id)}
            >
              <span className="sidebar-item-text">{t(child.titleRu, child.titleEn)}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
