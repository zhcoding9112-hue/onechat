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
}

export interface CreateCouponRequest {
  name: string
  amountCents: number
  thresholdCents: number
  serviceItemId?: number | null
  validFrom: string
  validTo: string
}

export const couponAdminApi = {
  list: () => http.get<AdminCoupon[]>('/api/admin/coupons'),

  create: (body: CreateCouponRequest) => http.post<AdminCoupon>('/api/admin/coupons', body),

  /** userIds 为空表示向全体粉丝发放。 */
  grant: (couponId: number, userIds?: number[]) =>
    http.post<{ granted: number }>(`/api/admin/coupons/${couponId}/grant`, { userIds }),
}
