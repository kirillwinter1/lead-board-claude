import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { BoardRow } from './BoardRow'
import type { BoardNode, EpicForecast, RoughEstimateConfig, RoughEstimateUpdateFn, PlannedStory } from './types'

interface SortableStoryRowProps {
  story: BoardNode
  isExpanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: RoughEstimateUpdateFn
  forecast: EpicForecast | null
  canReorder: boolean
  actualPosition: number | undefined
  recommendedPosition: number | undefined
  storyPlanning?: PlannedStory | null
  children?: React.ReactNode
}

export function SortableStoryRow({
  story,
  isExpanded,
  onToggle,
  hasChildren,
  roughEstimateConfig,
  onRoughEstimateUpdate,
  forecast,
  canReorder,
  actualPosition,
  recommendedPosition,
  storyPlanning,
  children
}: SortableStoryRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: story.issueKey })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.8 : 1,
    zIndex: isDragging ? 100 : 'auto',
    position: 'relative' as const,
  }

  if (!canReorder) {
    return (
      <div>
        <BoardRow
          node={story}
          level={1}
          expanded={isExpanded}
          onToggle={onToggle}
          hasChildren={hasChildren}
          roughEstimateConfig={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
          forecast={forecast}
          canReorder={false}
          isJustDropped={false}
          actualPosition={actualPosition}
          recommendedPosition={recommendedPosition}
          storyPlanning={storyPlanning}
        />
        {children}
      </div>
    )
  }

  return (
    <div ref={setNodeRef} style={style}>
      <BoardRow
        node={story}
        level={1}
        expanded={isExpanded}
        onToggle={onToggle}
        hasChildren={hasChildren}
        roughEstimateConfig={roughEstimateConfig}
        onRoughEstimateUpdate={onRoughEstimateUpdate}
        forecast={forecast}
        canReorder={canReorder}
        isJustDropped={false}
        actualPosition={actualPosition}
        storyPlanning={storyPlanning}
        recommendedPosition={recommendedPosition}
        dragHandleProps={{ ...attributes, ...listeners }}
      />
      {children}
    </div>
  )
}
