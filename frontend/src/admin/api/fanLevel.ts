import { http } from '@/api/http'

export interface FanLevelTier {
  id: number
  level: number
  name: string
  thresholdCents: number
}

export const fanLevelAdminApi = {
  list: () => http.get<FanLevelTier[]>('/api/admin/fan-levels'),

  update: (level: number, thresholdCents: number, name?: string) =>
    http.put<FanLevelTier>(`/api/admin/fan-levels/${level}`, { thresholdCents, name }),
}
