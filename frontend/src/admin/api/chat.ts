import { http } from '@/api/http'

export interface AdminConversation {
  id: number
  fanUserId: number
  fanNickname: string | null
  talentId: number
  talentNickname: string | null
  lastMessageAt: string
  createdAt: string
}

export interface AdminChatMessage {
  id: number
  senderRole: 'FAN' | 'TALENT'
  senderUserId: number
  senderNickname: string | null
  contentType: 'TEXT' | 'IMAGE'
  content: string
  status: 'VISIBLE' | 'REMOVED'
  /** 下架留痕三件套。原文仍在 content 里，供纠纷追溯。 */
  removedBy: number | null
  removedAt: string | null
  removeReason: string | null
  createdAt: string
}

export interface AdminTranscript {
  conversation: AdminConversation
  messages: AdminChatMessage[]
}

export const chatAdminApi = {
  /** 按订单号或用户 id 查会话。订单号是粉丝来申诉时最常给出的线索。 */
  search: (params: { orderRef?: string; userId?: number }) => {
    const q = new URLSearchParams()
    if (params.orderRef) q.set('orderRef', params.orderRef)
    if (params.userId != null) q.set('userId', String(params.userId))
    return http.get<AdminConversation[]>(`/api/admin/chat/conversations?${q.toString()}`)
  },

  /** 完整消息记录，含已下架的。 */
  transcript: (conversationId: number) =>
    http.get<AdminTranscript>(`/api/admin/chat/conversations/${conversationId}`),

  removeMessage: (messageId: number, reason: string) =>
    http.put<void>(`/api/admin/chat/messages/${messageId}/remove`, { reason }),
}
