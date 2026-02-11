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
      const response = await axios.get<BoardResponse>('/api/board')
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

  const handleRoughEstimateUpdate = useCallback(async (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => {
    await updateRoughEstimate(epicKey, role, { days })
    await fetchBoard()
  }, [fetchBoard])

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
