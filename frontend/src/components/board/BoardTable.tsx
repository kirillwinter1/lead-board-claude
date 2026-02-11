import { useState, useEffect, useCallback, useMemo, Fragment } from 'react'
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
  DragOverEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { playDropSound } from './helpers'
import { BoardRow } from './BoardRow'
import { SortableEpicRow } from './SortableEpicRow'
import { SortableStoryRow } from './SortableStoryRow'
import type { BoardNode, BoardTableProps } from './types'

export function BoardTable({ items, roughEstimateConfig, onRoughEstimateUpdate, forecastMap, storyPlanningMap, canReorder, onReorder, onStoryReorder }: BoardTableProps) {
  // Load expanded keys from localStorage
  const loadExpandedKeys = (): Set<string> => {
    try {
      const saved = localStorage.getItem('boardExpandedEpics')
      if (saved) {
        const parsed = JSON.parse(saved)
        return new Set(parsed)
      }
    } catch (err) {
      console.error('Failed to load expanded epics from localStorage:', err)
    }
    return new Set()
  }

  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(loadExpandedKeys)
  const [showInfoTooltip, setShowInfoTooltip] = useState(false)

  // Live preview positions during drag - shows where items would end up
  const [dragPreviewPositions, setDragPreviewPositions] = useState<Map<string, number> | null>(null)
  const [storyDragPreviewPositions, setStoryDragPreviewPositions] = useState<Map<string, number> | null>(null)

  // @dnd-kit sensors for keyboard and pointer
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  )

  // Save expanded keys to localStorage whenever they change
  useEffect(() => {
    try {
      localStorage.setItem('boardExpandedEpics', JSON.stringify(Array.from(expandedKeys)))
    } catch (err) {
      console.error('Failed to save expanded epics to localStorage:', err)
    }
  }, [expandedKeys])

  const toggleExpand = (key: string) => {
    setExpandedKeys(prev => {
      const next = new Set(prev)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  // Calculate preview positions during drag - live feedback
  const handleEpicDragOver = useCallback((event: DragOverEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) {
      setDragPreviewPositions(null)
      return
    }

    const oldIndex = items.findIndex(e => e.issueKey === active.id)
    const newIndex = items.findIndex(e => e.issueKey === over.id)

    if (oldIndex === -1 || newIndex === -1) {
      setDragPreviewPositions(null)
      return
    }

    const reordered = [...items]
    const [movedItem] = reordered.splice(oldIndex, 1)
    reordered.splice(newIndex, 0, movedItem)

    const positions = new Map<string, number>()
    reordered.forEach((epic, idx) => {
      positions.set(epic.issueKey, idx + 1)
    })
    setDragPreviewPositions(positions)
  }, [items])

  // Handle epic drag end - call API directly
  const handleEpicDragEnd = useCallback(async (event: DragEndEvent) => {
    setDragPreviewPositions(null)

    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = items.findIndex(e => e.issueKey === active.id)
    const newIndex = items.findIndex(e => e.issueKey === over.id)

    if (oldIndex !== -1 && newIndex !== -1 && oldIndex !== newIndex) {
      playDropSound()
      await onReorder(active.id as string, newIndex)
    }
  }, [items, onReorder])

  // Calculate preview positions for stories during drag
  const handleStoryDragOver = useCallback((event: DragOverEvent, stories: BoardNode[]) => {
    const { active, over } = event
    if (!over || active.id === over.id) {
      setStoryDragPreviewPositions(null)
      return
    }

    const oldIndex = stories.findIndex(s => s.issueKey === active.id)
    const newIndex = stories.findIndex(s => s.issueKey === over.id)

    if (oldIndex === -1 || newIndex === -1) {
      setStoryDragPreviewPositions(null)
      return
    }

    const reordered = [...stories]
    const [movedItem] = reordered.splice(oldIndex, 1)
    reordered.splice(newIndex, 0, movedItem)

    const positions = new Map<string, number>()
    reordered.forEach((story, idx) => {
      positions.set(story.issueKey, idx + 1)
    })
    setStoryDragPreviewPositions(positions)
  }, [])

  // Handle story drag end - call API directly
  const handleStoryDragEnd = useCallback(async (event: DragEndEvent, parentKey: string, stories: BoardNode[]) => {
    setStoryDragPreviewPositions(null)

    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = stories.findIndex(s => s.issueKey === active.id)
    const newIndex = stories.findIndex(s => s.issueKey === over.id)

    if (oldIndex !== -1 && newIndex !== -1 && oldIndex !== newIndex) {
      playDropSound()
      await onStoryReorder(active.id as string, parentKey, newIndex)
    }
  }, [onStoryReorder])

  // Calculate recommended positions based on autoScore (descending)
  const epicRecommendations = useMemo(() => {
    if (!canReorder) {
      return new Map<string, number>()
    }

    const sorted = [...items]
      .filter(e => e.autoScore !== null)
      .sort((a, b) => (b.autoScore || 0) - (a.autoScore || 0))

    const recommendations = new Map<string, number>()
    sorted.forEach((epic, idx) => {
      recommendations.set(epic.issueKey, idx + 1)
    })
    return recommendations
  }, [items, canReorder])

  // Calculate recommended positions for stories within each epic
  const getStoryRecommendations = useCallback((children: BoardNode[]): Map<string, number> => {
    const stories = children.filter(c =>
      c.issueType === 'Story' || c.issueType === 'История' ||
      c.issueType === 'Bug' || c.issueType === 'Баг'
    )
    const sorted = [...stories]
      .filter(s => s.autoScore !== null)
      .sort((a, b) => (b.autoScore || 0) - (a.autoScore || 0))

    const recommendations = new Map<string, number>()
    sorted.forEach((story, idx) => {
      recommendations.set(story.issueKey, idx + 1)
    })
    return recommendations
  }, [])

  // Render children (stories/subtasks)
  const renderChildren = (children: BoardNode[], parentKey: string, level: number, isExpanded: boolean): JSX.Element => {
    const storyRecommendations = level === 1 ? getStoryRecommendations(children) : new Map<string, number>()

    const stories = children.filter(c =>
      c.issueType === 'Story' || c.issueType === 'История' ||
      c.issueType === 'Bug' || c.issueType === 'Баг'
    )
    const subtasks = children.filter(c =>
      c.issueType !== 'Story' && c.issueType !== 'История' &&
      c.issueType !== 'Bug' && c.issueType !== 'Баг'
    )

    return (
      <div className={`children-wrapper ${isExpanded ? 'expanded' : ''}`}>
        <div className="children-container">
          {level === 1 && canReorder && stories.length > 0 ? (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragOver={(event) => handleStoryDragOver(event, stories)}
              onDragEnd={(event) => handleStoryDragEnd(event, parentKey, stories)}
            >
              <SortableContext
                items={stories.map(s => s.issueKey)}
                strategy={verticalListSortingStrategy}
              >
                {stories.map((story, storyIndex) => {
                  const storyIsExpanded = expandedKeys.has(story.issueKey)
                  const storyHasChildren = story.children.length > 0
                  const storyForecast = forecastMap.get(story.issueKey) || null
                  const actualPosition = storyDragPreviewPositions?.get(story.issueKey) ?? (storyIndex + 1)
                  const recommendedPosition = storyRecommendations.get(story.issueKey)

                  return (
                    <SortableStoryRow
                      key={story.issueKey}
                      story={story}
                      isExpanded={storyIsExpanded}
                      onToggle={() => toggleExpand(story.issueKey)}
                      hasChildren={storyHasChildren}
                      roughEstimateConfig={roughEstimateConfig}
                      onRoughEstimateUpdate={onRoughEstimateUpdate}
                      forecast={storyForecast}
                      canReorder={canReorder}
                      actualPosition={actualPosition}
                      recommendedPosition={recommendedPosition}
                      storyPlanning={storyPlanningMap.get(story.issueKey)}
                    >
                      {storyHasChildren && renderChildren(story.children, story.issueKey, level + 1, storyIsExpanded)}
                    </SortableStoryRow>
                  )
                })}
              </SortableContext>
            </DndContext>
          ) : (
            stories.map((story, storyIndex) => {
              const storyIsExpanded = expandedKeys.has(story.issueKey)
              const storyHasChildren = story.children.length > 0
              const storyForecast = forecastMap.get(story.issueKey) || null
              const actualPosition = storyIndex + 1
              const recommendedPosition = storyRecommendations.get(story.issueKey)

              return (
                <Fragment key={story.issueKey}>
                  <BoardRow
                    node={story}
                    level={1}
                    expanded={storyIsExpanded}
                    onToggle={() => toggleExpand(story.issueKey)}
                    hasChildren={storyHasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={storyForecast}
                    canReorder={false}
                    isJustDropped={false}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                    storyPlanning={storyPlanningMap.get(story.issueKey)}
                  />
                  {storyHasChildren && renderChildren(story.children, story.issueKey, level + 1, storyIsExpanded)}
                </Fragment>
              )
            })
          )}
          {/* Subtasks (level 2) are not reorderable */}
          {subtasks.map((subtask) => {
            const subtaskIsExpanded = expandedKeys.has(subtask.issueKey)
            const subtaskHasChildren = subtask.children.length > 0
            const subtaskForecast = forecastMap.get(subtask.issueKey) || null

            return (
              <Fragment key={subtask.issueKey}>
                <BoardRow
                  node={subtask}
                  level={level}
                  expanded={subtaskIsExpanded}
                  onToggle={() => toggleExpand(subtask.issueKey)}
                  hasChildren={subtaskHasChildren}
                  roughEstimateConfig={roughEstimateConfig}
                  onRoughEstimateUpdate={onRoughEstimateUpdate}
                  forecast={subtaskForecast}
                  canReorder={false}
                  isJustDropped={false}
                />
                {subtaskHasChildren && renderChildren(subtask.children, subtask.issueKey, level + 1, subtaskIsExpanded)}
              </Fragment>
            )
          })}
        </div>
      </div>
    )
  }

  return (
    <div className="board-table-container">
      <div className="board-grid">
        <div className="board-header">
          <div className="cell th-expander"></div>
          <div className="cell th-name">NAME</div>
          <div className="cell th-team">TEAM</div>
          <div className="cell th-priority">PRIORITY</div>
          <div className="cell th-expected-done">
            <span className="th-with-info">
              EXPECTED DONE
              <span
                className="info-icon"
                onMouseEnter={() => setShowInfoTooltip(true)}
                onMouseLeave={() => setShowInfoTooltip(false)}
              >
                i
                {showInfoTooltip && (
                  <div className="info-tooltip">
                    <div className="info-tooltip-title">Прогноз завершения</div>
                    <p>Дата рассчитывается на основе:</p>
                    <ul>
                      <li>Остатка работы по ролям (SA → DEV → QA)</li>
                      <li>Производительности команды</li>
                      <li>Производственного календаря</li>
                    </ul>
                    <div className="info-tooltip-section">
                      <strong>Уверенность:</strong>
                      <div className="confidence-legend">
                        <span><span className="confidence-dot high"></span> Высокая — есть оценки</span>
                        <span><span className="confidence-dot medium"></span> Средняя — частичные оценки</span>
                        <span><span className="confidence-dot low"></span> Низкая — нет оценок</span>
                      </div>
                    </div>
                    <div className="info-tooltip-section">
                      <strong>Порядок эпиков:</strong>
                      <p>Перетащите эпик для изменения приоритета. Стрелки показывают рекомендации AutoScore.</p>
                    </div>
                  </div>
                )}
              </span>
            </span>
          </div>
          <div className="cell th-progress">PROGRESS</div>
          <div className="cell th-roles">ROLE-BASED PROGRESS</div>
          <div className="cell th-status">STATUS</div>
          <div className="cell th-alerts">ALERTS</div>
        </div>
        <div className="board-body">
          {canReorder ? (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragOver={handleEpicDragOver}
              onDragEnd={handleEpicDragEnd}
            >
              <SortableContext
                items={items.map(e => e.issueKey)}
                strategy={verticalListSortingStrategy}
              >
                {items.map((epic, epicIndex) => {
                  const isExpanded = expandedKeys.has(epic.issueKey)
                  const hasChildren = epic.children.length > 0
                  const forecast = forecastMap.get(epic.issueKey) || null
                  const actualPosition = dragPreviewPositions?.get(epic.issueKey) ?? (epicIndex + 1)
                  const recommendedPosition = epicRecommendations.get(epic.issueKey)

                  return (
                    <SortableEpicRow
                      key={epic.issueKey}
                      epic={epic}
                      isExpanded={isExpanded}
                      onToggle={() => toggleExpand(epic.issueKey)}
                      hasChildren={hasChildren}
                      roughEstimateConfig={roughEstimateConfig}
                      onRoughEstimateUpdate={onRoughEstimateUpdate}
                      forecast={forecast}
                      canReorder={canReorder}
                      actualPosition={actualPosition}
                      recommendedPosition={recommendedPosition}
                    >
                      {hasChildren && renderChildren(epic.children, epic.issueKey, 1, isExpanded)}
                    </SortableEpicRow>
                  )
                })}
              </SortableContext>
            </DndContext>
          ) : (
            items.map((epic, epicIndex) => {
              const isExpanded = expandedKeys.has(epic.issueKey)
              const hasChildren = epic.children.length > 0
              const forecast = forecastMap.get(epic.issueKey) || null
              const actualPosition = epicIndex + 1
              const recommendedPosition = epicRecommendations.get(epic.issueKey)

              return (
                <Fragment key={epic.issueKey}>
                  <BoardRow
                    node={epic}
                    level={0}
                    expanded={isExpanded}
                    onToggle={() => toggleExpand(epic.issueKey)}
                    hasChildren={hasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={forecast}
                    canReorder={false}
                    isJustDropped={false}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                  />
                  {hasChildren && renderChildren(epic.children, epic.issueKey, 1, isExpanded)}
                </Fragment>
              )
            })
          )}
        </div>
      </div>
    </div>
  )
}
