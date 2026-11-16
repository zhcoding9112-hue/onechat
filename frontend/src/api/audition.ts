import { http } from './http'

export interface AuditionRequestView {
  id: number
  serviceItemId: number | null
  voiceDesc: string
  status: 'OPEN' | 'CLOSED'
  expiresAt: string
  replyCount: number
}

export interface AuditionReplyView {
  replyId: number
  talentId: number
  nickname: string
  coverUrl: string | null
  audioUrl: string | null
  audioSeconds: number | null
  message: string | null
  online: boolean
  repliedAt: string
}

export const auditionApi = {
  /** 我当前进行中的需求；没有时返回 null。 */
  mine: () => http.get<AuditionRequestView | null>('/api/auditions/mine'),
  publish: (voiceDesc: string, serviceItemId?: number | null) =>
    http.post<AuditionRequestView>('/api/auditions', { voiceDesc, serviceItemId }),
  close: (id: number) => http.delete<void>(`/api/auditions/${id}`),
  replies: (id: number) => http.get<AuditionReplyView[]>(`/api/auditions/${id}/replies`),
  /** 主播侧：可应征的开放需求。 */
  open: () => http.get<AuditionRequestView[]>('/api/auditions/open'),
  reply: (id: number, message?: string) =>
    http.post<{ replyId: number }>(`/api/auditions/${id}/replies`, { message }),
}
