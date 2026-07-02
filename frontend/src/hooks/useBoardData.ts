import { useState, useCallback, useEffect, useRef } from 'react'
import axios from 'axios'
import { getRoughEstimateConfig, updateRoughEstimate } from '../api/epics'
import type { BoardNode, BoardResponse, SyncStatus, RoughEstimateConfig } from '../components/board/types'
import { getApiCache, setApiCache } from './useApiCache'

const buildBoardCacheKey = (includeArchived: boolean) => `board-data-${includeArchived ? 'archived' : 'active'}`

export function useBoardData(includeArchived: boolean = false) {
  const cached = getApiCache<BoardNode[]>(buildBoardCacheKey(includeArchived))
  const [board, setBoard] = useState<BoardNode[]>(cached ?? [])
  const [loading, setLoading] = useState(!cached)
  const [error, setError] = useState<string | null>(null)
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [roughEstimateConfig, setRoughEstimateConfig] = useState<RoughEstimateConfig | null>(null)
  // fetchBoard can be triggered concurrently (mount effect, includeArchived toggle, manual
  // refresh after drag/estimate updates, post-sync refresh) — abort the previous in-flight
  // request so an out-of-order response can't overwrite fresher data.
  const abortRef = useRef<AbortController | null>(null)

  const fetchBoard = useCallback(async (silent = false) => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    if (!silent) setLoading(true)
    try {
      const response = await axios.get<BoardResponse>('/api/board', {
        params: { includeDQ: true, includeArchived },
        signal: controller.signal,
      })
      if (controller.signal.aborted) return
      setBoard(response.data.items)
      setApiCache(buildBoardCacheKey(includeArchived), response.data.items)
    } catch (err: unknown) {
      if (controller.signal.aborted) return
      if (!silent) setError(err instanceof Error ? err.message : 'Failed to load board')
    } finally {
      if (!controller.signal.aborted && !silent) setLoading(false)
    }
  }, [includeArchived])

  useEffect(() => () => abortRef.current?.abort(), [])

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
    fetchBoard(!!cached)
    fetchSyncStatus()
    fetchRoughEstimateConfig()
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
