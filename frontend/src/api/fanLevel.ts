import { http } from './http'

export interface FanLevel {
  level: number
  name: string
  /** 累计已确认消费，单位分。 */
  spentCents: number
  nextLevel: number | null
  nextName: string | null
  /** 距下一等级还差多少分，已满级时为 null。 */
  toNextCents: number | null
}

export const fanLevelApi = {
  mine: () => http.get<FanLevel>('/api/fan-levels/me'),
}
