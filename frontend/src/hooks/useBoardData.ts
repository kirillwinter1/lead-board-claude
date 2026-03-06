import { useState, useCallback, useEffect } from 'react'
import axios from 'axios'
import { getRoughEstimateConfig, updateRoughEstimate } from '../api/epics'
import type { BoardNode, BoardResponse, SyncStatus, RoughEstimateConfig } from '../components/board/types'

export function useBoardData() {
  const [board, setBoard] = useState<BoardNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [roughEstimateConfig, setRoughEstimateConfig] = useState<RoughEstimateConfig | null>(null)

  const fetchBoard = useCallback(async (silent = false) => {
    if (!silent) setLoading(true)
    try {
      const response = await axios.get<BoardResponse>('/api/board', { params: { includeDQ: true } })
      setBoard(response.data.items)
    } catch (err: unknown) {
      if (!silent) setError(err instanceof Error ? err.message : 'Failed to load board')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [])

  const fetchRoughEstimateConfig = useCallback(() => {
    getRoughEstimateConfig()
      .then(setRoughEstimateConfig)
      .catch(() => {})
  }, [])

  const fetchSyncStatus = useCallback(() => {
    axios.get<SyncStatus>('/api/sync/status')
      .then(response => {
        setSyncStatus(response.data)
        setSyncing(response.data.syncInProgress)
      })
      .catch(() => {})
  }, [])

  const triggerSync = (onComplete?: () => void) => {
    setSyncing(true)
    axios.post<SyncStatus>('/api/sync/trigger')
      .then(() => {
        const pollInterval = setInterval(() => {
          axios.get<SyncStatus>('/api/sync/status')
            .then(response => {
              setSyncStatus(response.data)
              if (!response.data.syncInProgress) {
                setSyncing(false)
                clearInterval(pollInterval)
                fetchBoard()
                onComplete?.()
              }
            })
        }, 2000)
      })
      .catch(err => {
        setSyncing(false)
        alert('Sync failed: ' + err.message)
      })
  }

  useEffect(() => {
    fetchBoard()
    fetchSyncStatus()
    fetchRoughEstimateConfig()
  }, [fetchBoard, fetchSyncStatus, fetchRoughEstimateConfig])

  const handleRoughEstimateUpdate = useCallback(async (epicKey: string, role: string, days: number | null) => {
    const response = await updateRoughEstimate(epicKey, role, { days })
    // Optimistic update: patch the specific epic node in the tree instead of reloading entire board
    const cleaned: Record<string, number> = {}
    for (const [k, v] of Object.entries(response.roughEstimates)) {
      if (v !== null) cleaned[k] = v
    }
    const patchNode = (nodes: BoardNode[]): BoardNode[] =>
      nodes.map(node => {
        if (node.issueKey === epicKey) {
          // Patch both roughEstimates and roleProgress so the UI updates immediately
          const patchedRoleProgress = node.roleProgress ? { ...node.roleProgress } : null
          if (patchedRoleProgress && patchedRoleProgress[role]) {
            patchedRoleProgress[role] = { ...patchedRoleProgress[role], roughEstimateDays: days }
          }
          return { ...node, roughEstimates: cleaned, ...(patchedRoleProgress && { roleProgress: patchedRoleProgress }) }
        }
        if (node.children?.length) {
          const patched = patchNode(node.children)
          return patched !== node.children ? { ...node, children: patched } : node
        }
        return node
      })
    setBoard(patchNode)
  }, [])

  return {
    board,
    setBoard,
    loading,
    error,
    syncStatus,
    syncing,
    roughEstimateConfig,
    fetchBoard,
    triggerSync,
    handleRoughEstimateUpdate,
  }
}
