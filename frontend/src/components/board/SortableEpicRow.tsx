import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { BoardRow } from './BoardRow'
import type { BoardNode, EpicForecast, RoughEstimateConfig, RoughEstimateUpdateFn } from './types'

interface SortableEpicRowProps {
  epic: BoardNode
  isExpanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: RoughEstimateUpdateFn
  forecast: EpicForecast | null
  canReorder: boolean
  actualPosition: number
  recommendedPosition: number | undefined
  children?: React.ReactNode
}

export function SortableEpicRow({
  epic,
  isExpanded,
  onToggle,
  hasChildren,
  roughEstimateConfig,
  onRoughEstimateUpdate,
  forecast,
  canReorder,
  actualPosition,
  recommendedPosition,
  children
}: SortableEpicRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: epic.issueKey })

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
          node={epic}
          level={0}
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
        />
        {children}
      </div>
    )
  }

  return (
    <div ref={setNodeRef} style={style}>
      <BoardRow
        node={epic}
        level={0}
        expanded={isExpanded}
        onToggle={onToggle}
        hasChildren={hasChildren}
        roughEstimateConfig={roughEstimateConfig}
        onRoughEstimateUpdate={onRoughEstimateUpdate}
        forecast={forecast}
        canReorder={canReorder}
        isJustDropped={false}
        actualPosition={actualPosition}
        recommendedPosition={recommendedPosition}
        dragHandleProps={{ ...attributes, ...listeners }}
      />
      {children}
    </div>
  )
}
