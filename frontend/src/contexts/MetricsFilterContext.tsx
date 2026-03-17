import { createContext, useContext, useCallback, useMemo, ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'

type Preset = '30d' | '90d' | '180d' | '365d'

interface MetricsFilterState {
  teamId: number | null
  from: string
  to: string
  preset: Preset
  setTeamId: (id: number | null) => void
  setPreset: (preset: Preset) => void
}

const MetricsFilterContext = createContext<MetricsFilterState | null>(null)

function computeDateRange(preset: Preset): { from: string; to: string } {
  const to = new Date()
  const from = new Date()
  const days = preset === '30d' ? 30 : preset === '90d' ? 90 : preset === '180d' ? 180 : 365
  from.setDate(from.getDate() - days)
  return {
    from: from.toISOString().split('T')[0],
    to: to.toISOString().split('T')[0],
  }
}

export function MetricsFilterProvider({ children }: { children: ReactNode }) {
  const [searchParams, setSearchParams] = useSearchParams()

  const rawTeamId = searchParams.get('teamId')
  const teamId = rawTeamId && !isNaN(Number(rawTeamId)) ? Number(rawTeamId) : null

  const rawPreset = searchParams.get('preset')
  const preset: Preset = rawPreset && ['30d', '90d', '180d', '365d'].includes(rawPreset)
    ? (rawPreset as Preset) : '90d'

  const { from, to } = useMemo(() => computeDateRange(preset), [preset])

  const setTeamId = useCallback((id: number | null) => {
    const params: Record<string, string> = {}
    if (id) params.teamId = String(id)
    if (preset !== '90d') params.preset = preset
    setSearchParams(params)
  }, [setSearchParams, preset])

  const setPreset = useCallback((p: Preset) => {
    const params: Record<string, string> = {}
    if (teamId) params.teamId = String(teamId)
    if (p !== '90d') params.preset = p
    setSearchParams(params)
  }, [setSearchParams, teamId])

  const value: MetricsFilterState = useMemo(() => ({
    teamId, from, to, preset, setTeamId, setPreset,
  }), [teamId, from, to, preset, setTeamId, setPreset])

  return (
    <MetricsFilterContext.Provider value={value}>
      {children}
    </MetricsFilterContext.Provider>
  )
}

export function useMetricsFilter(): MetricsFilterState {
  const ctx = useContext(MetricsFilterContext)
  if (!ctx) {
    throw new Error('useMetricsFilter must be used within MetricsFilterProvider')
  }
  return ctx
}
