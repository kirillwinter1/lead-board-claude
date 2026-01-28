import { useEffect, useRef, useState, useCallback } from 'react'
import { SessionState, ParticipantInfo, PokerVote, PokerMessage } from '../api/poker'

interface UsePokerWebSocketOptions {
  roomCode: string
  accountId: string
  displayName: string
  role: 'SA' | 'DEV' | 'QA'
  isFacilitator: boolean
  onStateUpdate?: (state: SessionState) => void
  onParticipantJoined?: (participant: ParticipantInfo) => void
  onParticipantLeft?: (accountId: string) => void
  onVoteCast?: (storyId: number, voterAccountId: string, role: string) => void
  onVotesRevealed?: (storyId: number, votes: PokerVote[]) => void
  onStoryCompleted?: (storyId: number, saHours: number, devHours: number, qaHours: number) => void
  onCurrentStoryChanged?: (storyId: number) => void
  onSessionCompleted?: () => void
  onError?: (message: string) => void
}

export function usePokerWebSocket(options: UsePokerWebSocketOptions) {
  const {
    roomCode,
    accountId,
    displayName,
    role,
    isFacilitator,
    onStateUpdate,
    onParticipantJoined,
    onParticipantLeft,
    onVoteCast,
    onVotesRevealed,
    onStoryCompleted,
    onCurrentStoryChanged,
    onSessionCompleted,
    onError,
  } = options

  const wsRef = useRef<WebSocket | null>(null)
  const [connected, setConnected] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/poker/${roomCode}`

    const ws = new WebSocket(wsUrl)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      setError(null)

      // Send JOIN message
      ws.send(JSON.stringify({
        type: 'JOIN',
        accountId,
        displayName,
        role,
        isFacilitator,
      }))
    }

    ws.onclose = () => {
      setConnected(false)
      // Try to reconnect after 3 seconds
      reconnectTimeoutRef.current = setTimeout(() => {
        connect()
      }, 3000)
    }

    ws.onerror = () => {
      setError('WebSocket connection error')
    }

    ws.onmessage = (event) => {
      try {
        const message: PokerMessage = JSON.parse(event.data)
        handleMessage(message)
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e)
      }
    }
  }, [roomCode, accountId, displayName, role, isFacilitator])

  const handleMessage = useCallback((message: PokerMessage) => {
    switch (message.type) {
      case 'STATE':
        onStateUpdate?.(message.payload.session as SessionState)
        break
      case 'PARTICIPANT_JOINED':
        onParticipantJoined?.(message.payload.participant as ParticipantInfo)
        break
      case 'PARTICIPANT_LEFT':
        onParticipantLeft?.(message.payload.accountId as string)
        break
      case 'VOTE_CAST':
        onVoteCast?.(
          message.payload.storyId as number,
          message.payload.voterAccountId as string,
          message.payload.role as string
        )
        break
      case 'VOTES_REVEALED':
        onVotesRevealed?.(
          message.payload.storyId as number,
          message.payload.votes as PokerVote[]
        )
        break
      case 'STORY_COMPLETED':
        onStoryCompleted?.(
          message.payload.storyId as number,
          message.payload.finalSaHours as number,
          message.payload.finalDevHours as number,
          message.payload.finalQaHours as number
        )
        break
      case 'CURRENT_STORY_CHANGED':
        onCurrentStoryChanged?.(message.payload.storyId as number)
        break
      case 'SESSION_COMPLETED':
        onSessionCompleted?.()
        break
      case 'ERROR':
        setError(message.payload.message as string)
        onError?.(message.payload.message as string)
        break
    }
  }, [onStateUpdate, onParticipantJoined, onParticipantLeft, onVoteCast, onVotesRevealed, onStoryCompleted, onCurrentStoryChanged, onSessionCompleted, onError])

  useEffect(() => {
    connect()

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
      wsRef.current?.close()
    }
  }, [connect])

  // Send methods
  const sendVote = useCallback((storyId: number, hours: number | null) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'VOTE',
        storyId,
        hours,
      }))
    }
  }, [])

  const sendReveal = useCallback((storyId: number) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'REVEAL',
        storyId,
      }))
    }
  }, [])

  const sendSetFinal = useCallback((storyId: number, saHours: number | null, devHours: number | null, qaHours: number | null) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'SET_FINAL',
        storyId,
        saHours,
        devHours,
        qaHours,
      }))
    }
  }, [])

  const sendNextStory = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'NEXT_STORY',
      }))
    }
  }, [])

  const sendStartSession = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'START_SESSION',
      }))
    }
  }, [])

  const sendCompleteSession = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'COMPLETE_SESSION',
      }))
    }
  }, [])

  return {
    connected,
    error,
    sendVote,
    sendReveal,
    sendSetFinal,
    sendNextStory,
    sendStartSession,
    sendCompleteSession,
  }
}
