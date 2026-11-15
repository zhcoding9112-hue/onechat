import { http } from '@/api/http'

/** 性别取值与后端 talent.gender 列保持一致；未填写为 null。 */
export type Gender = 'FEMALE' | 'MALE'

export interface TalentAdminView {
  id: number
  userId: number | null
  nickname: string
  coverUrl: string | null
  intro: string | null
  audioUrl: string | null
  audioSeconds: number | null
  online: boolean
  listed: boolean
  sortOrder: number
  tags: string[]
  gender: Gender | null
  age: number | null
  region: string | null
  /** 0 到 2 条，运营自填。 */
  promoTags: string[]
}

export interface TalentProfilePayload {
  nickname?: string | null
  intro?: string | null
  coverUrl?: string | null
  sortOrder?: number | null
  gender?: Gender | null
  age?: number | null
  region?: string | null
  promoTags?: string[] | null
}

const BASE = '/api/admin/talents'

export const talentAdminApi = {
  list: () => http.get<TalentAdminView[]>(BASE),
  detail: (id: number) => http.get<TalentAdminView>(`${BASE}/${id}`),
  create: (payload: TalentProfilePayload) => http.post<TalentAdminView>(BASE, payload),
  update: (id: number, payload: TalentProfilePayload) =>
    http.put<TalentAdminView>(`${BASE}/${id}`, payload),
  setListed: (id: number, listed: boolean) =>
    http.put<TalentAdminView>(`${BASE}/${id}/listed/${listed}`),
  setTags: (id: number, tags: string[]) =>
    http.put<TalentAdminView>(`${BASE}/${id}/tags`, { tags }),
}
