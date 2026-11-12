import { http } from './http'

export interface TalentCard {
  id: number
  nickname: string
  coverUrl: string | null
  tags: string[]
  online: boolean
  audioUrl: string | null
  audioSeconds: number | null
}

export interface SpecOption {
  serviceSpecId: number
  code: string
  name: string
  durationMinutes: number | null
  unitPriceCents: number
}

export interface ItemGroup {
  serviceItemId: number
  code: string
  name: string
  specs: SpecOption[]
}

export interface TalentDetail {
  id: number
  nickname: string
  coverUrl: string | null
  intro: string | null
  tags: string[]
  online: boolean
  audioUrl: string | null
  audioSeconds: number | null
  available: boolean
  unavailableReason: string | null
  services: ItemGroup[]
}

export const talentApi = {
  list: () => http.get<TalentCard[]>('/api/talents'),
  detail: (id: number) => http.get<TalentDetail>(`/api/talents/${id}`),
}
