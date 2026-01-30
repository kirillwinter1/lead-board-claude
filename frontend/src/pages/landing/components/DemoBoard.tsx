import { useState, Fragment } from 'react'
import { mockEpics, DemoEpic, DemoStory } from '../mockData'
import epicIcon from '../../../icons/epic.png'
import storyIcon from '../../../icons/story.png'

const ROLE_NAMES = {
  SA: 'Системный анализ',
  DEV: 'Разработка',
  QA: 'Тестирование'
}

function RoleChip({ role, progress }: { role: 'SA' | 'DEV' | 'QA'; progress: number }) {
  return (
    <div className={`demo-role-chip ${role.toLowerCase()}`} data-tooltip={`${ROLE_NAMES[role]}: ${progress}%`}>
      <span className="demo-role-label">{role}</span>
      <div className="demo-role-progress-bar">
        <div
          className="demo-role-progress-fill"
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  )
}

function DemoStoryRow({ story, onHighlight }: { story: DemoStory; onHighlight?: (index: number | null) => void }) {
  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left
    const width = rect.width
    const percent = x / width

    if (percent < 0.5) {
      onHighlight?.(0) // NAME
    } else {
      onHighlight?.(2) // ROLE / STATUS
    }
  }

  return (
    <div
      className="demo-story-row"
      onMouseMove={handleMouseMove}
      onMouseLeave={() => onHighlight?.(null)}
    >
      <div className="demo-story-info">
        <img src={storyIcon} alt="Story" className="demo-issue-icon" />
        <span className="demo-story-key">{story.key}</span>
        <span className="demo-story-title">{story.title}</span>
      </div>
      <div className="demo-story-assignee">
        {story.assignee}
      </div>
      <div className={`demo-role-badge ${story.role.toLowerCase()}`}>
        {story.role}
      </div>
      <div
        className="demo-story-status"
        style={{ color: story.statusColor }}
      >
        <span
          className="demo-status-dot"
          style={{ background: story.statusColor }}
        />
        {story.status}
      </div>
    </div>
  )
}

function DemoEpicRow({
  epic,
  isExpanded,
  onToggle,
  isDragging,
  isDragOver,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
  onHighlight
}: {
  epic: DemoEpic
  isExpanded: boolean
  onToggle: () => void
  isDragging: boolean
  isDragOver: boolean
  onDragStart: (e: React.DragEvent) => void
  onDragOver: (e: React.DragEvent) => void
  onDrop: (e: React.DragEvent) => void
  onDragEnd: () => void
  onHighlight?: (index: number | null) => void
}) {
  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left
    const width = rect.width
    const percent = x / width

    // Определяем колонку по позиции курсора (примерно по grid)
    if (percent < 0.45) {
      onHighlight?.(0) // NAME
    } else if (percent < 0.58) {
      onHighlight?.(1) // EXPECTED DONE
    } else {
      onHighlight?.(2) // ROLE PROGRESS / STATUS
    }
  }

  return (
    <div
      className={`demo-epic-row ${isDragging ? 'dragging' : ''} ${isDragOver ? 'drag-over' : ''}`}
      draggable
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
      onMouseMove={handleMouseMove}
      onMouseLeave={() => onHighlight?.(null)}
    >
      <div className="demo-epic-cell demo-epic-name">
        <button
          className={`demo-expander ${isExpanded ? 'expanded' : ''}`}
          onClick={onToggle}
          aria-label={isExpanded ? 'Свернуть' : 'Развернуть'}
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M6 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <img src={epicIcon} alt="Epic" className="demo-issue-icon" />
        <span className="demo-epic-key">{epic.key}</span>
        <span className="demo-epic-title" data-tooltip={epic.title}>{epic.title}</span>
      </div>
      <div className="demo-epic-cell demo-epic-date">
        <span className="demo-expected-done-date">{epic.expectedDone}</span>
        {epic.variance !== 0 && (
          <span
            className={`demo-expected-done-delta ${epic.variance > 0 ? 'delta-late' : 'delta-early'}`}
            data-tooltip={epic.variance > 0 ? `Опоздание на ${epic.variance} дней` : `Раньше срока на ${Math.abs(epic.variance)} дней`}
          >
            {epic.variance > 0 ? `+${epic.variance}d` : `${epic.variance}d`}
          </span>
        )}
      </div>
      <div className="demo-epic-cell demo-epic-roles">
        <RoleChip role="SA" progress={epic.progress.sa} />
        <RoleChip role="DEV" progress={epic.progress.dev} />
        <RoleChip role="QA" progress={epic.progress.qa} />
      </div>
      <div className="demo-epic-cell demo-epic-status">
        <span
          className="demo-status-badge"
          data-tooltip={`Статус: ${epic.status}`}
          style={{
            background: `${epic.statusColor}15`,
            color: epic.statusColor,
            borderColor: epic.statusColor
          }}
        >
          {epic.status}
        </span>
      </div>
    </div>
  )
}

interface DemoBoardProps {
  onHighlight?: (index: number | null) => void
}

export function DemoBoard({ onHighlight }: DemoBoardProps) {
  const [epics, setEpics] = useState<DemoEpic[]>(mockEpics)
  const [expanded, setExpanded] = useState<Set<string>>(new Set(['DEMO-001']))
  const [draggingKey, setDraggingKey] = useState<string | null>(null)
  const [dragOverKey, setDragOverKey] = useState<string | null>(null)

  const toggleExpand = (key: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  const handleDragStart = (e: React.DragEvent, epicKey: string) => {
    setDraggingKey(epicKey)
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', epicKey)
  }

  const handleDragOver = (e: React.DragEvent, epicKey: string) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
    if (epicKey !== draggingKey) {
      setDragOverKey(epicKey)
    }
  }

  const handleDrop = (e: React.DragEvent, targetKey: string) => {
    e.preventDefault()
    if (draggingKey && targetKey !== draggingKey) {
      const dragIndex = epics.findIndex(ep => ep.key === draggingKey)
      const dropIndex = epics.findIndex(ep => ep.key === targetKey)

      if (dragIndex !== -1 && dropIndex !== -1) {
        const newEpics = [...epics].map(ep => ({ ...ep }))
        const [draggedEpic] = newEpics.splice(dragIndex, 1)

        // При перетаскивании вверх — улучшаем сроки обоих эпиков
        if (dragIndex > dropIndex) {
          // Перетащили вверх — приоритет выше, сроки лучше
          draggedEpic.variance = Math.min(draggedEpic.variance - 15, -3)
          // Эпик на который бросили тоже улучшается (синергия)
          const targetEpic = newEpics[dropIndex]
          if (targetEpic) {
            targetEpic.variance = Math.min(targetEpic.variance - 5, -2)
          }
        } else {
          // Перетащили вниз — приоритет ниже, сроки хуже
          draggedEpic.variance = draggedEpic.variance + 8
        }

        newEpics.splice(dropIndex, 0, draggedEpic)
        setEpics(newEpics)
      }
    }
    setDraggingKey(null)
    setDragOverKey(null)
  }

  const handleDragEnd = () => {
    setDraggingKey(null)
    setDragOverKey(null)
  }

  return (
    <div className="demo-board">
      <div
        className="demo-board-header"
        onMouseMove={(e) => {
          const rect = e.currentTarget.getBoundingClientRect()
          const x = e.clientX - rect.left
          const percent = x / rect.width
          if (percent < 0.45) onHighlight?.(0)
          else if (percent < 0.58) onHighlight?.(1)
          else onHighlight?.(2)
        }}
        onMouseLeave={() => onHighlight?.(null)}
      >
        <span className="demo-header-name">NAME</span>
        <span className="demo-header-date">EXPECTED DONE</span>
        <span className="demo-header-roles">ROLE PROGRESS</span>
        <span className="demo-header-status">STATUS</span>
      </div>
      <div className="demo-board-body">
        {epics.map(epic => (
          <Fragment key={epic.key}>
            <DemoEpicRow
              epic={epic}
              isExpanded={expanded.has(epic.key)}
              onToggle={() => toggleExpand(epic.key)}
              isDragging={draggingKey === epic.key}
              isDragOver={dragOverKey === epic.key}
              onDragStart={(e) => handleDragStart(e, epic.key)}
              onDragOver={(e) => handleDragOver(e, epic.key)}
              onDrop={(e) => handleDrop(e, epic.key)}
              onDragEnd={handleDragEnd}
              onHighlight={onHighlight}
            />
            <div className={`demo-stories-wrapper ${expanded.has(epic.key) ? 'expanded' : ''}`}>
              <div className="demo-stories-container">
                {epic.stories.map(story => (
                  <DemoStoryRow key={story.key} story={story} onHighlight={onHighlight} />
                ))}
              </div>
            </div>
          </Fragment>
        ))}
      </div>
    </div>
  )
}
