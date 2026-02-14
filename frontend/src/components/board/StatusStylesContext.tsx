import { createContext, useContext } from 'react'
import type { StatusStyle } from '../../api/board'

const StatusStylesContext = createContext<Record<string, StatusStyle>>({})

export const StatusStylesProvider = StatusStylesContext.Provider

export function useStatusStyles() {
  return useContext(StatusStylesContext)
}
