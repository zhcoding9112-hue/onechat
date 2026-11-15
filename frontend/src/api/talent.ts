import { http } from './http'

export type Gender = 'FEMALE' | 'MALE'

export interface TalentCard {
  id: number
  nickname: string
  coverUrl: string | null
  tags: string[]
  online: boolean
  audioUrl: string | null
  audioSeconds: number | null
  /** 以下为扩展资料，未填写时为 null，前端整项不渲染。 */
  gender: Gender | null
  age: number | null
  region: string | null
  /** 宣传语标签，0 到 2 条。 */
  promoTags: string[]
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
  gender: Gender | null
  age: number | null
  region: string | null
  promoTags: string[]
}

export const talentApi = {
  list: () => http.get<TalentCard[]>('/api/talents'),
  detail: (id: number) => http.get<TalentDetail>(`/api/talents/${id}`),
}
