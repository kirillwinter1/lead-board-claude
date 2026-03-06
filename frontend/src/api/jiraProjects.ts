import axios from 'axios'

export interface JiraProject {
  id: number
  projectKey: string
  displayName: string
  active: boolean
  syncEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ProjectSyncStatus {
  projectKey: string
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
}

export async function getActiveProjectKeys(): Promise<string[]> {
  const res = await axios.get<{ projects: string[] }>('/api/config/projects')
  return res.data.projects
}

export async function getPerProjectSyncStatus(): Promise<ProjectSyncStatus[]> {
  const res = await axios.get<ProjectSyncStatus[]>('/api/sync/projects')
  return res.data
}

// Admin CRUD
export async function listJiraProjects(): Promise<JiraProject[]> {
  const res = await axios.get<JiraProject[]>('/api/admin/jira-projects')
  return res.data
}

export async function createJiraProject(projectKey: string, displayName?: string): Promise<JiraProject> {
  const res = await axios.post<JiraProject>('/api/admin/jira-projects', { projectKey, displayName })
  return res.data
}

export async function updateJiraProject(id: number, data: Partial<Pick<JiraProject, 'displayName' | 'active' | 'syncEnabled'>>): Promise<JiraProject> {
  const res = await axios.put<JiraProject>(`/api/admin/jira-projects/${id}`, data)
  return res.data
}

export async function deleteJiraProject(id: number): Promise<void> {
  await axios.delete(`/api/admin/jira-projects/${id}`)
}
