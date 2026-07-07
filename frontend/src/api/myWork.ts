import axios from 'axios'

export interface MyTeamRef { teamId: number; teamName: string; teamColor: string | null }
export interface MyMemberInfo {
  displayName: string; avatarUrl: string | null; role: string; grade: string
  hoursPerDay: number; teams: MyTeamRef[]
}
export interface MyTask {
  key: string; summary: string; issueType: string; status: string
  parentKey: string | null; parentSummary: string | null
  epicKey: string | null; epicSummary: string | null
  teamId: number; teamName: string; teamColor: string | null
  estimateH: number | null; spentH: number | null; jiraUrl: string
}
export interface QueueStory {
  key: string; summary: string; issueType: string; status: string
  teamId: number; teamName: string; teamColor: string | null
  epicKey: string | null; epicSummary: string | null
  myPhaseSubtasks: number; myPhaseEstimateH: number | null; jiraUrl: string
}
export interface CalendarDay {
  date: string; dayType: 'WORKDAY' | 'WEEKEND' | 'HOLIDAY'
  loggedH: number; normH: number; absenceType: string | null
  byIssue: { issueKey: string; hours: number }[]
}
export interface MySummary {
  completedCount: number; avgDsr: number | null; avgCycleTimeDays: number | null
  utilization: number | null; totalSpentH: number; totalEstimateH: number
}
export interface MyWeeklyTrend { week: string; weekStart: string; dsr: number | null; tasksCompleted: number; hoursLogged: number }
export interface MyCompletedTask {
  key: string; summary: string; epicKey: string | null; epicSummary: string | null
  teamId: number; teamName: string; teamColor: string | null
  estimateH: number | null; spentH: number | null; dsr: number | null
  doneDate: string; jiraUrl: string
}
export interface DsrBreakdown { key: string; label: string; taskCount: number; estimateH: number | null; spentH: number | null; dsr: number | null }
export interface MyAnalytics {
  summary: MySummary; weeklyTrend: MyWeeklyTrend[]; completedTasks: MyCompletedTask[]
  dsrByParentType: DsrBreakdown[]; dsrByEpic: DsrBreakdown[]
}
export interface UpcomingAbsence { id: number; memberId: number; absenceType: string; startDate: string; endDate: string; comment: string | null }
export interface MyWorkResponse {
  hasMembership: boolean; member: MyMemberInfo | null; upcomingAbsences: UpcomingAbsence[]
  activeTasks: MyTask[]; upcomingAssigned: MyTask[]; teamQueue: QueueStory[]
  worklogCalendar: CalendarDay[]; analytics: MyAnalytics | null
}

export const myWorkApi = {
  getMyWork: (from: string, to: string, teamId?: number) =>
    axios.get<MyWorkResponse>('/api/me/work', { params: { from, to, ...(teamId ? { teamId } : {}) } })
      .then(r => r.data),
}
