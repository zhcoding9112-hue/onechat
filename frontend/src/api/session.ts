import { http } from './http'

export interface CurrentUser {
  userId: number
}

/** 查询当前会话。未登录时请求层会自动触发重新授权。 */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return http.get<CurrentUser>('/api/session/me')
}
