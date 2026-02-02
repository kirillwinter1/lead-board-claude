import { useState, Fragment, useEffect, useRef } from 'react'
import { mockEpics, DemoEpic, DemoStory } from '../mockData'
import epicIcon from '../../../icons/epic.png'
import storyIcon from '../../../icons/story.png'

const ROLE_NAMES = {
  SA: 'Системный анализ',
  DEV: 'Разработка',
  QA: 'Тестирование'
}

function RecommendationIndicator({ type }: { type: 'up' | 'down' | 'match' }) {
  const config = {
    up: { icon: '↑', className: 'suggest-up' },
    down: { icon: '↓', className: 'suggest-down' },
    match: { icon: '●', className: 'match' }
  }
  const { icon, className } = config[type]

  return (
    <span className={`demo-recommendation-indicator ${className}`}>
      {icon}
    </span>
  )
}

function SuccessMessage({ show }: { show: boolean }) {
  if (!show) return null

  return (
    <div className="demo-success-overlay">
      <div className="demo-success-message">
        <span className="demo-success-icon">👍</span>
        <span className="demo-success-text">Красавчик, завершим всё вовремя!</span>
      </div>
    </div>
  )
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
  currentPosition,
  expectedDone,
  variance,
  isExpanded,
  onToggle,
  isDragging,
  isDragOver,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
  onHighlight,
  recommendation
}: {
  epic: DemoEpic
  currentPosition: number
  expectedDone: string
  variance: number
  isExpanded: boolean
  onToggle: () => void
  isDragging: boolean
  isDragOver: boolean
  onDragStart: (e: React.DragEvent) => void
  onDragOver: (e: React.DragEvent) => void
  onDrop: (e: React.DragEvent) => void
  onDragEnd: () => void
  onHighlight?: (index: number | null) => void
  recommendation: 'up' | 'down' | 'match'
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
      <div
        className="demo-epic-cell demo-epic-priority"
        data-tooltip={`Перетащите для изменения приоритета\nРекомендуемая позиция ${epic.optimalPosition}, сейчас ${currentPosition}`}
      >
        <RecommendationIndicator type={recommendation} />
      </div>
      <div className="demo-epic-cell demo-epic-date">
        <span className="demo-expected-done-date">{expectedDone}</span>
        {variance !== 0 && (
          <span
            className={`demo-expected-done-delta ${variance > 0 ? 'delta-late' : 'delta-early'}`}
            data-tooltip={variance > 0 ? `Опоздание на ${variance} дней` : `Раньше срока на ${Math.abs(variance)} дней`}
          >
            {variance > 0 ? `+${variance}d` : `${variance}d`}
          </span>
        )}
      </div>
      <div className="demo-epic-cell demo-epic-progress">
        <div className="demo-progress-bar-container">
          <div className="demo-progress-bar-fill" style={{ width: `${epic.totalProgress}%` }} />
        </div>
        <span className="demo-progress-percent">{epic.totalProgress}%</span>
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

// Форматирование дня в дату (день 0 = 1 февраля)
const formatDay = (day: number): string => {
  const months = ['января', 'февраля', 'марта', 'апреля']
  const baseDate = new Date(2026, 1, 1) // 1 февраля 2026
  baseDate.setDate(baseDate.getDate() + day)
  return `${baseDate.getDate()} ${months[baseDate.getMonth()]}`
}

// Расчёт прогнозируемого дня завершения на основе позиции
const calculateExpectedDay = (epic: DemoEpic, currentPosition: number): number => {
  const positionDiff = Math.abs(currentPosition - epic.optimalPosition)
  // Каждая позиция от оптимума добавляет +4 дня
  return epic.baseExpectedDay + positionDiff * 4
}

// Расчёт variance = прогноз - целевая дата
const calculateVariance = (epic: DemoEpic, currentPosition: number): number => {
  const expectedDay = calculateExpectedDay(epic, currentPosition)
  return expectedDay - epic.targetDay
}

export function DemoBoard({ onHighlight }: DemoBoardProps) {
  // Храним только порядок эпиков, variance рассчитывается динамически
  const [epics, setEpics] = useState<DemoEpic[]>(() => [...mockEpics])
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [draggingKey, setDraggingKey] = useState<string | null>(null)
  const [dragOverKey, setDragOverKey] = useState<string | null>(null)
  const [showSuccess, setShowSuccess] = useState(false)
  const [hasAnimated, setHasAnimated] = useState(false)
  const boardRef = useRef<HTMLDivElement>(null)

  // Авто-анимация: раскрываем первый эпик когда блок появляется на экране
  useEffect(() => {
    if (hasAnimated) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !hasAnimated) {
          // Блок появился на экране — раскрываем эпик через небольшую задержку
          setTimeout(() => {
            setExpanded(new Set(['DEMO-001']))
            setHasAnimated(true)
          }, 500)
          observer.disconnect()
        }
      },
      { threshold: 0.3 }
    )

    if (boardRef.current) {
      observer.observe(boardRef.current)
    }

    return () => observer.disconnect()
  }, [hasAnimated])

  // Проверяем все ли эпики вовремя (variance рассчитывается на основе текущей позиции)
  const allOnTime = epics.every((epic, index) => calculateVariance(epic, index + 1) <= 0)

  useEffect(() => {
    if (allOnTime && !showSuccess) {
      setShowSuccess(true)
      // Скрываем через 3 секунды
      const timer = setTimeout(() => setShowSuccess(false), 4000)
      return () => clearTimeout(timer)
    }
  }, [allOnTime])

  // Определяем рекомендации для каждого эпика на основе текущей и оптимальной позиции
  const getRecommendation = (epic: DemoEpic, currentPosition: number): 'up' | 'down' | 'match' => {
    const diff = currentPosition - epic.optimalPosition
    if (diff > 0) {
      // Эпик стоит ниже оптимальной позиции — рекомендуем поднять
      return 'up'
    } else if (diff < 0) {
      // Эпик стоит выше оптимальной позиции — можно опустить
      return 'down'
    }
    return 'match'
  }

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
        const newEpics = [...epics]
        const [draggedEpic] = newEpics.splice(dragIndex, 1)
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
    <div className="demo-board" ref={boardRef}>
      <SuccessMessage show={showSuccess} />
      <div
        className="demo-board-header"
        onMouseMove={(e) => {
          const rect = e.currentTarget.getBoundingClientRect()
          const x = e.clientX - rect.left
          const percent = x / rect.width
          if (percent < 0.35) onHighlight?.(0)
          else if (percent < 0.50) onHighlight?.(1)
          else onHighlight?.(2)
        }}
        onMouseLeave={() => onHighlight?.(null)}
      >
        <span className="demo-header-name">NAME</span>
        <span className="demo-header-priority">
          PRIORITY
          <span className="results-snapshot-hint" data-tooltip="Рассчитываем приоритет, рекомендуем последовательность выполнения">?</span>
        </span>
        <span className="demo-header-date">
          EXPECTED DONE
          <span className="results-snapshot-hint" data-tooltip="Рассчитываем дату завершения эпика, показываем укладываемся ли в сроки">?</span>
        </span>
        <span className="demo-header-progress">PROGRESS</span>
        <span className="demo-header-roles">ROLE PROGRESS</span>
        <span className="demo-header-status">STATUS</span>
      </div>
      <div className="demo-board-body">
        {epics.map((epic, index) => (
          <Fragment key={epic.key}>
            <DemoEpicRow
              epic={epic}
              currentPosition={index + 1}
              expectedDone={formatDay(calculateExpectedDay(epic, index + 1))}
              variance={calculateVariance(epic, index + 1)}
              isExpanded={expanded.has(epic.key)}
              onToggle={() => toggleExpand(epic.key)}
              isDragging={draggingKey === epic.key}
              isDragOver={dragOverKey === epic.key}
              onDragStart={(e) => handleDragStart(e, epic.key)}
              onDragOver={(e) => handleDragOver(e, epic.key)}
              onDrop={(e) => handleDrop(e, epic.key)}
              onDragEnd={handleDragEnd}
              onHighlight={onHighlight}
              recommendation={getRecommendation(epic, index + 1)}
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
