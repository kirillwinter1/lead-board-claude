import './Skeleton.css'

interface SkeletonProps {
  width?: number | string
  height?: number | string
  borderRadius?: number | string
  delay?: number
  style?: React.CSSProperties
}

export function Skeleton({ width, height = 14, borderRadius = 4, delay, style }: SkeletonProps) {
  return (
    <span
      className="skeleton"
      aria-hidden="true"
      style={{
        width,
        height,
        borderRadius,
        ...(delay ? { animationDelay: `${delay}ms` } : undefined),
        ...style,
      }}
    />
  )
}
