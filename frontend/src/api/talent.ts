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

/** 一页主播。total 用于筛选后的计数展示，hasMore 用于下拉加载判断。 */
export interface TalentFeed {
  items: TalentCard[]
  total: number
  hasMore: boolean
}

export interface TalentFeedQuery {
  category?: string | null
  gender?: string | null
  level?: number | null
  page?: number
  size?: number
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
  list: (q: TalentFeedQuery = {}, options?: Parameters<typeof http.get>[1]) => {
    const params = new URLSearchParams()
    if (q.category) params.set('category', q.category)
    if (q.gender) params.set('gender', q.gender)
    if (q.level != null) params.set('level', String(q.level))
    params.set('page', String(q.page ?? 0))
    params.set('size', String(q.size ?? 20))
    return http.get<TalentFeed>(`/api/talents?${params.toString()}`, options)
  },
  detail: (id: number) => http.get<TalentDetail>(`/api/talents/${id}`),
}
