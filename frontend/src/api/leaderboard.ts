import { http } from './http'

/**
 * 打赏榜单。
 *
 * 返回体里没有任何打赏人字段——后端在 SQL 层就没把 fan_user_id 查出来，
 * 不是前端拿到后再过滤的。
 */
export type PeriodType = 'DAY' | 'WEEK' | 'MONTH'

export interface BoardRow {
  rank: number
  talentId: number
  nickname: string
  coverUrl: string | null
  amountCents: number
  tipCount: number
}

export interface BoardView {
  periodType: PeriodType
  periodTypeLabel: string
  periodKey: string
  /** 是否为当前周期。历史周期读的是固化后的快照。 */
  current: boolean
  rows: BoardRow[]
}

export const leaderboardApi = {
  board: (type: PeriodType, limit = 20) =>
    http.get<BoardView>(`/api/leaderboard?type=${type}&limit=${limit}`),
}
