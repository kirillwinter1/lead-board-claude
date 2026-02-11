import { useState, useRef, useCallback } from 'react'

interface TooltipPos {
  top: number
  left: number
  showAbove?: boolean
}

interface UseTooltipPositionOptions {
  tooltipWidth: number
  minSpaceNeeded: number
}

export function useTooltipPosition<T extends HTMLElement>({ tooltipWidth, minSpaceNeeded }: UseTooltipPositionOptions) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<TooltipPos | null>(null)
  const ref = useRef<T>(null)

  const handleMouseEnter = useCallback(() => {
    setShowTooltip(true)

    if (ref.current) {
      const rect = ref.current.getBoundingClientRect()
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      if (spaceBelow >= minSpaceNeeded) {
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        top = rect.top - 8
      } else {
        top = rect.bottom + 8
      }

      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }
  }, [tooltipWidth, minSpaceNeeded])

  const handleMouseLeave = useCallback(() => {
    setShowTooltip(false)
  }, [])

  return { ref, showTooltip, tooltipPos, handleMouseEnter, handleMouseLeave }
}
