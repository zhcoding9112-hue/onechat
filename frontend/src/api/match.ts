import { http } from './http'
import type { Gender } from './talent'

export interface MatchedTalent {
  id: number
  nickname: string
  coverUrl: string | null
  online: boolean
  gender: Gender | null
  age: number | null
  region: string | null
  intro: string | null
  audioUrl: string | null
  audioSeconds: number | null
}

export interface MatchResult {
  matched: boolean
  talent: MatchedTalent | null
  /** 未匹配到时的建议文案。 */
  message: string | null
}

export interface MatchQuery {
  category?: string | null
  gender?: Gender | null
  onlineOnly?: boolean
  excludeOrdered?: boolean
  /** 「换一位」时带上已经看过的，尽量避开。 */
  exclude?: number[]
}

export const matchApi = {
  random: (q: MatchQuery = {}) => {
    const params = new URLSearchParams()
    if (q.category) params.set('category', q.category)
    if (q.gender) params.set('gender', q.gender)
    if (q.onlineOnly) params.set('onlineOnly', 'true')
    if (q.excludeOrdered) params.set('excludeOrdered', 'true')
    for (const id of q.exclude ?? []) params.append('exclude', String(id))
    return http.get<MatchResult>(`/api/match/random?${params.toString()}`)
  },
}
