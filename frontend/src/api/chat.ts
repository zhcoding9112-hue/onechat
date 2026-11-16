import { http, type RequestOptions } from './http'

export interface ChatConversationView {
  id: number
  talentId: number
  nickname: string | null
  coverUrl: string | null
  /** 列表摘要。图片消息显示为「[图片]」；最后一条被下架时为 null。 */
  lastMessage: string | null
  lastMessageType: string | null
  lastMessageAt: string
  unread: number
  /** 主播已下架时为 false，此时发送入口禁用。 */
  canSend: boolean
  disabledReason: string | null
}

export interface ChatMessageView {
  id: number
  senderRole: 'FAN' | 'TALENT'
  contentType: 'TEXT' | 'IMAGE'
  content: string
  /** 是不是我发的，由服务端判定，前端不自己比对身份。 */
  mine: boolean
  createdAt: string
}

export interface ChatView {
  conversation: ChatConversationView
  messages: ChatMessageView[]
}

/** 聊天配图上传。multipart 的 boundary 必须由浏览器生成，所以不走 http.post。 */
async function uploadImage(url: string, file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(url, { method: 'POST', credentials: 'include', body: form })
  const payload = await res.json()
  if (!res.ok || !payload?.success) {
    throw new Error(payload?.message ?? '图片上传失败')
  }
  return (payload.data as { url: string }).url
}

export const chatApi = {
  conversations: (limit = 50) =>
    http.get<ChatConversationView[]>(`/api/chat/conversations?limit=${limit}`),

  /** 未读总数，供底部导航的红点使用。 */
  unread: (options?: RequestOptions) =>
    http.get<{ total: number }>('/api/chat/unread', options),

  /** 从主播详情页进聊天。重复调用拿到的是同一个会话。 */
  openWithTalent: (talentId: number) =>
    http.post<{ conversationId: number }>(`/api/chat/talents/${talentId}/conversation`),

  /** 打开会话：取消息并把未读清零，一次往返完成。 */
  open: (conversationId: number, limit = 200) =>
    http.get<ChatView>(`/api/chat/conversations/${conversationId}?limit=${limit}`),

  sendText: (conversationId: number, content: string) =>
    http.post<ChatMessageView>(`/api/chat/conversations/${conversationId}/messages`, {
      contentType: 'TEXT',
      content,
    }),

  sendImage: (conversationId: number, url: string) =>
    http.post<ChatMessageView>(`/api/chat/conversations/${conversationId}/messages`, {
      contentType: 'IMAGE',
      content: url,
    }),

  uploadImage: (file: File) => uploadImage('/api/chat/images', file),
}

export const talentChatApi = {
  conversations: (limit = 50) =>
    http.get<ChatConversationView[]>(`/api/talent/chat/conversations?limit=${limit}`),

  unread: (options?: RequestOptions) =>
    http.get<{ total: number }>('/api/talent/chat/unread', options),

  open: (conversationId: number, limit = 200) =>
    http.get<ChatView>(`/api/talent/chat/conversations/${conversationId}?limit=${limit}`),

  sendText: (conversationId: number, content: string) =>
    http.post<ChatMessageView>(`/api/talent/chat/conversations/${conversationId}/messages`, {
      contentType: 'TEXT',
      content,
    }),

  sendImage: (conversationId: number, url: string) =>
    http.post<ChatMessageView>(`/api/talent/chat/conversations/${conversationId}/messages`, {
      contentType: 'IMAGE',
      content: url,
    }),

  uploadImage: (file: File) => uploadImage('/api/chat/images', file),
}
