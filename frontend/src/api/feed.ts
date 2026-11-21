import { http } from './http'

export interface FeedPostView {
  id: number
  talentId: number
  nickname: string | null
  coverUrl: string | null
  content: string | null
  images: string[]
  likeCount: number
  commentCount: number
  /** 当前粉丝是否已点赞。 */
  liked: boolean
  createdAt: string
}

export interface FeedPostPage {
  items: FeedPostView[]
  total: number
  hasMore: boolean
}

export interface FeedCommentView {
  id: number
  userId: number
  nickname: string
  avatarUrl: string
  /** 是这条动态的主播本人。 */
  author: boolean
  /** 粉丝等级。作者本人为 null——主播没有粉丝等级。 */
  level: number | null
  levelName: string | null
  content: string
  createdAt: string
}

export interface CrushWallItem {
  talentId: number
  nickname: string
  coverUrl: string | null
  online: boolean
  promoTags: string[]
}

export const feedApi = {
  square: (page = 0, size = 10) =>
    http.get<FeedPostPage>(`/api/feed/posts?page=${page}&size=${size}`),

  byTalent: (talentId: number, limit = 5) =>
    http.get<FeedPostView[]>(`/api/feed/talents/${talentId}/posts?limit=${limit}`),

  /** 点赞与取消点赞都返回最新计数，前端不自己加减，避免与服务端漂移。 */
  like: (postId: number) => http.post<number>(`/api/feed/posts/${postId}/like`),
  unlike: (postId: number) => http.delete<number>(`/api/feed/posts/${postId}/like`),

  comments: (postId: number) =>
    http.get<FeedCommentView[]>(`/api/feed/posts/${postId}/comments`),

  comment: (postId: number, content: string) =>
    http.post<FeedCommentView>(`/api/feed/posts/${postId}/comments`, { content }),

  reportPost: (postId: number, reason: string) =>
    http.post<void>(`/api/feed/posts/${postId}/report`, { reason }),

  crushWall: (limit = 12) =>
    http.get<CrushWallItem[]>(`/api/feed/crush-wall?limit=${limit}`),
}

export const talentFeedApi = {
  limits: () => http.get<{ maxImages: number }>('/api/talent/feed/limits'),

  mine: (limit = 20) => http.get<FeedPostView[]>(`/api/talent/feed/posts?limit=${limit}`),

  publish: (content: string, images: string[]) =>
    http.post<{ postId: number }>('/api/talent/feed/posts', { content, images }),

  uploadImage: async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    // 不走 http.post：那条路径会把 body 当 JSON 序列化并带上 Content-Type，
    // 而 multipart 的 boundary 必须由浏览器自己生成
    const res = await fetch('/api/talent/feed/images', {
      method: 'POST',
      credentials: 'include',
      body: form,
    })
    const payload = await res.json()
    if (!res.ok || !payload?.success) {
      throw new Error(payload?.message ?? '图片上传失败')
    }
    return (payload.data as { url: string }).url
  },
}
