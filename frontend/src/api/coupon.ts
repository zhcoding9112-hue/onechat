import { http } from './http'

/**
 * 券包。
 *
 * 刻意不用 Vant 的 CouponList：它自带一个「兑换码」输入框，
 * 而本阶段没有兑换码这回事，放一个用不了的输入框只会让人以为漏填了什么。
 */
export type CouponBucket = 'AVAILABLE' | 'USED' | 'EXPIRED'

export interface CouponItem {
  userCouponId: number
  couponId: number
  name: string
  /** 面额，单位分。 */
  amountCents: number
  /** 使用门槛（订单原价下限），单位分。 */
  thresholdCents: number
  serviceItemId: number | null
  expiresAt: string
  bucket: CouponBucket
  /** 可用 / 使用中 / 已使用 / 已过期。 */
  statusLabel: string
  remark: string | null
}

export const couponApi = {
  wallet: () => http.get<CouponItem[]>('/api/coupons/mine'),

  /** 这笔订单当前可用的券。门槛与品类由后端判定，前端不自己算。 */
  usable: (originalCents: number, serviceItemId?: number | null) =>
    http.get<CouponItem[]>(
      `/api/coupons/usable?originalCents=${originalCents}` +
        (serviceItemId == null ? '' : `&serviceItemId=${serviceItemId}`),
    ),

  claim: (couponId: number) => http.post<CouponItem>(`/api/coupons/${couponId}/claim`),
}
