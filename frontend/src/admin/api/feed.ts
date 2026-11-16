import { http } from '@/api/http'

export interface AdminReport {
  id: number
  targetType: 'POST' | 'COMMENT'
  targetId: number
  reporterUserId: number
  reason: string
  status: string
  createdAt: string
}

export interface AdminPost {
  id: number
  talentId: number
  nickname: string | null
  content: string | null
  images: string[]
  status: 'VISIBLE' | 'REMOVED'
  /** 下架留痕三件套：软下架的全部意义就在这里。 */
  removedBy: number | null
  removedAt: string | null
  removeReason: string | null
  likeCount: number
  commentCount: number
  createdAt: string
  reports: AdminReport[]
}

export interface AdminPick {
  talentId: number
  nickname: string | null
  coverUrl: string | null
  sortOrder: number
  enabled: boolean
  listed: boolean
}

export const feedAdminApi = {
  posts: (status?: string) =>
    http.get<AdminPost[]>(`/api/admin/feed/posts${status ? `?status=${status}` : ''}`),

  removePost: (postId: number, reason: string) =>
    http.put<void>(`/api/admin/feed/posts/${postId}/remove`, { reason }),

  reports: () => http.get<AdminReport[]>('/api/admin/feed/reports'),

  handleReport: (reportId: number, reason: string) =>
    http.put<{ closedReports: number }>(`/api/admin/feed/reports/${reportId}/remove`, { reason }),

  dismissReport: (reportId: number, note?: string) =>
    http.put<{ closedReports: number }>(`/api/admin/feed/reports/${reportId}/dismiss`, { note }),

  picks: () => http.get<AdminPick[]>('/api/admin/feed/crush-wall'),

  pick: (talentId: number, sortOrder: number) =>
    http.post<void>('/api/admin/feed/crush-wall', { talentId, sortOrder }),

  unpick: (talentId: number) => http.delete<void>(`/api/admin/feed/crush-wall/${talentId}`),

  settings: () => http.get<{ maxImages: number }>('/api/admin/feed/settings'),

  updateSettings: (maxImages: number) =>
    http.put<{ maxImages: number }>('/api/admin/feed/settings', { maxImages }),
}
