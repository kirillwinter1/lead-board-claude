import axios from 'axios'

export interface CompetencyLevel {
  componentName: string
  level: number
}

export interface MemberCompetencyDto {
  memberId: number
  displayName: string
  competencies: CompetencyLevel[]
}

export interface TeamCompetencyMatrix {
  components: string[]
  members: MemberCompetencyDto[]
}

export interface BusFactorAlert {
  componentName: string
  severity: 'CRITICAL' | 'WARNING' | 'OK'
  expertCount: number
  experts: string[]
}

export const competencyApi = {
  getMember: (memberId: number) =>
    axios.get<CompetencyLevel[]>(`/api/competencies/member/${memberId}`).then(r => r.data),

  updateMember: (memberId: number, competencies: CompetencyLevel[]) =>
    axios.put<CompetencyLevel[]>(`/api/competencies/member/${memberId}`, competencies).then(r => r.data),

  getTeamMatrix: (teamId: number) =>
    axios.get<TeamCompetencyMatrix>(`/api/competencies/team/${teamId}`).then(r => r.data),

  getBusFactor: (teamId: number) =>
    axios.get<BusFactorAlert[]>(`/api/competencies/team/${teamId}/bus-factor`).then(r => r.data),

  getComponents: () =>
    axios.get<string[]>('/api/competencies/components').then(r => r.data),
}
