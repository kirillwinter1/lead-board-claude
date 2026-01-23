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
  saDays: number | null
  devDays: number | null
  qaDays: number | null
  roughEstimateUpdatedAt: string | null
  roughEstimateUpdatedBy: string | null
}

export async function getRoughEstimateConfig(): Promise<RoughEstimateConfig> {
  const response = await axios.get<RoughEstimateConfig>('/api/epics/config/rough-estimate')
  return response.data
}

export async function updateRoughEstimate(
  epicKey: string,
  role: 'sa' | 'dev' | 'qa',
  request: RoughEstimateRequest
): Promise<RoughEstimateResponse> {
  const response = await axios.patch<RoughEstimateResponse>(
    `/api/epics/${epicKey}/rough-estimate/${role}`,
    request
  )
  return response.data
}
