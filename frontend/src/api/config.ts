import axios from 'axios'

export interface AppConfig {
  jiraBaseUrl: string
}

let cachedConfig: AppConfig | null = null

export async function getConfig(): Promise<AppConfig> {
  if (cachedConfig) {
    return cachedConfig
  }
  const response = await axios.get<AppConfig>('/api/config')
  cachedConfig = response.data
  return cachedConfig
}
