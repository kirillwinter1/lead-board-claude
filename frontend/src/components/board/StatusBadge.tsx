export function StatusBadge({ status }: { status: string }) {
  const statusClass = status.toLowerCase().replace(/\s+/g, '-')
  return <span className={`status-badge ${statusClass}`}>{status}</span>
}
