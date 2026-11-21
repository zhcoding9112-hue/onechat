import { http } from '@/api/http'

export interface AdminCoupon {
  id: number
  name: string
  amountCents: number
  thresholdCents: number
  serviceItemId: number | null
  validFrom: string
  validTo: string
  enabled: boolean
  /** 注册即发：此后新注册的用户自动拿到这张券。 */
  autoGrantOnRegister: boolean
}

export interface CreateCouponRequest {
  name: string
  amountCents: number
  thresholdCents: number
  serviceItemId?: number | null
  validFrom: string
  validTo: string
  autoGrantOnRegister?: boolean
}

export const couponAdminApi = {
  list: () => http.get<AdminCoupon[]>('/api/admin/coupons'),

  create: (body: CreateCouponRequest) => http.post<AdminCoupon>('/api/admin/coupons', body),

  /**
   * 开关「注册即发」。
   * 只对此后注册的人生效；已经注册过的要补，还得用下面的发放。
   */
  setAutoGrant: (couponId: number, enabled: boolean) =>
    http.put<AdminCoupon>(`/api/admin/coupons/${couponId}/auto-grant/${enabled}`),

  /** userIds 为空表示向全体粉丝发放。 */
  grant: (couponId: number, userIds?: number[]) =>
    http.post<{ granted: number }>(`/api/admin/coupons/${couponId}/grant`, { userIds }),
}
