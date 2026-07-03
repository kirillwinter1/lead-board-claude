import { useState, useCallback, useEffect, useMemo, useRef } from 'react'
import { getForecast, getUnifiedPlanning, ForecastResponse, EpicForecast, PlannedStory } from '../api/forecast'

export function useBoardForecasts(allTeamIds: number[]) {
  const [allForecasts, setAllForecasts] = useState<Map<number, ForecastResponse>>(new Map())
  const [storyPlanningMap, setStoryPlanningMap] = useState<Map<string, PlannedStory>>(new Map())
  // Monotonic request ids guard against out-of-order responses: loadForecasts/loadStoryPlanning
  // can be triggered both by the allTeamIds effect and by external callers (e.g. after sync),
  // so a plain effect-cleanup ignore flag wouldn't cover every race — only the latest call's
  // result is applied.
  const forecastsRequestRef = useRef(0)
  const storyPlanningRequestRef = useRef(0)

  const loadForecasts = useCallback(() => {
    if (allTeamIds.length === 0) return
    const requestId = ++forecastsRequestRef.current

    Promise.all(
      allTeamIds.map(teamId =>
        getForecast(teamId)
          .then(data => ({ teamId, data }))
          .catch(() => null)
      )
    ).then(results => {
      if (requestId !== forecastsRequestRef.current) return
      const newForecasts = new Map<number, ForecastResponse>()
      results.forEach(result => {
        if (result) {
          newForecasts.set(result.teamId, result.data)
        }
      })
      setAllForecasts(newForecasts)
    })
  }, [allTeamIds])

  useEffect(() => {
    loadForecasts()
  }, [loadForecasts])

  // Load story planning data for tooltips
  const loadStoryPlanning = useCallback(() => {
    if (allTeamIds.length === 0) return
    const requestId = ++storyPlanningRequestRef.current

    Promise.all(
      allTeamIds.map(teamId =>
        getUnifiedPlanning(teamId)
          .then(data => ({ teamId, data }))
          .catch(() => null)
      )
    ).then(results => {
      if (requestId !== storyPlanningRequestRef.current) return
      const newMap = new Map<string, PlannedStory>()
      results.forEach(result => {
        if (result) {
          result.data.epics.forEach(epic => {
            epic.stories.forEach(story => {
              newMap.set(story.storyKey, story)
            })
          })
        }
      })
      setStoryPlanningMap(newMap)
    })
  }, [allTeamIds])

  useEffect(() => {
    loadStoryPlanning()
  }, [loadStoryPlanning])

  // Create forecast map for quick lookup (merged from all teams)
  const forecastMap = useMemo(() => {
    const map = new Map<string, EpicForecast>()
    allForecasts.forEach(forecast => {
      forecast.epics.forEach(f => map.set(f.epicKey, f))
    })
    return map
  }, [allForecasts])

  return {
    forecastMap,
    storyPlanningMap,
    loadForecasts,
  }
}
