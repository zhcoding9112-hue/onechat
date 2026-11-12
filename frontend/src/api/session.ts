import { http } from './http'

export interface CurrentUser {
  userId: number
  nickname: string
  avatarUrl: string
  /** 昵称头像是否为占位值 */
  placeholderProfile: boolean
  roles: string[]
  talent: boolean
  operator: boolean
}

/** 查询当前会话。未登录时请求层会自动触发重新授权。 */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return http.get<CurrentUser>('/api/session/me')
}
