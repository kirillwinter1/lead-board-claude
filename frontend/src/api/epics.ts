import axios from 'axios'

export interface RoughEstimateConfig {
  enabled: boolean
  allowedEpicStatuses: string[]
  stepDays: number
  minDays: number
  maxDays: number
}

export interface RoughEstimateRequest {
  days: number | null
  updatedBy?: string
}

export interface RoughEstimateResponse {
  epicKey: string
  role: string
  updatedDays: number | null
  roughEstimates: Record<string, number | null>
  roughEstimateUpdatedAt: string | null
  roughEstimateUpdatedBy: string | null
}

export async function getRoughEstimateConfig(): Promise<RoughEstimateConfig> {
  const response = await axios.get<RoughEstimateConfig>('/api/epics/config/rough-estimate')
  return response.data
}

export async function updateRoughEstimate(
  epicKey: string,
  role: string,
  request: RoughEstimateRequest
): Promise<RoughEstimateResponse> {
  const response = await axios.patch<RoughEstimateResponse>(
    `/api/epics/${epicKey}/rough-estimate/${role}`,
    request
  )
  return response.data
}

export interface OrderRequest {
  position: number
}

export interface OrderResponse {
  issueKey: string
  manualOrder: number | null
  autoScore: number | null
}

export async function updateEpicOrder(
  epicKey: string,
  position: number
): Promise<OrderResponse> {
  const response = await axios.put<OrderResponse>(
    `/api/epics/${epicKey}/order`,
    { position }
  )
  return response.data
}

export async function updateStoryOrder(
  storyKey: string,
  position: number
): Promise<OrderResponse> {
  const response = await axios.put<OrderResponse>(
    `/api/stories/${storyKey}/order`,
    { position }
  )
  return response.data
}
