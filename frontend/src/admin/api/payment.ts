import { http } from '@/api/http'
import type { AdminOrder } from '@/admin/api/order'
import type { PageResponse } from '@/api/types'

/** 待对账的一行：粉丝已上传凭证、等运营核对的订单。 */
export interface ReconcileItem {
  orderId: number
  orderRef: string
  payableCents: number
  status: string
  statusLabel: string
  createdAt: string
  proofUrls: string[]
  talentId: number | null
  talentNickname: string | null
  serviceItemName: string | null
  serviceSpecName: string | null
  quantity: number
  originalCents: number
  couponDiscountCents: number
  remark: string | null
  payDeadline: string | null
}

export interface ConfirmResult {
  /** ACCEPTED 已入账并推进订单；DUPLICATE 重复已忽略；REJECTED 状态不允许收款。 */
  outcome: 'ACCEPTED' | 'DUPLICATE' | 'REJECTED'
  status: string
  statusLabel: string
  detail: string
}

export interface PaymentSetting {
  id: number
  provider: string
  enabled: boolean
  accountName: string | null
  accountNo: string | null
  bankName: string | null
  qrUrl: string | null
  tips: string | null
  updatedAt: string | null
}

export interface SettingPatch {
  accountName?: string
  accountNo?: string
  bankName?: string
  qrUrl?: string
  tips?: string
}

export const paymentAdminApi = {
  /** 待对账队列，最早的排在前面。 */
  reconcile: () => http.get<ReconcileItem[]>('/api/admin/payment/reconcile'),

  /**
   * 待支付订单：粉丝没传凭证，但钱可能已经到账。
   * 这一类不在待对账队列里，只能从订单列表捞。
   */
  pendingPayment: async () =>
    // 订单列表已经改成分页响应，这里只要待支付那一页；
    // 人工收款模式下这个队列不会很长，取一页足够。
    (await http.get<PageResponse<AdminOrder>>(
      '/api/admin/orders?status=PENDING_PAYMENT&page=0&size=100',
    )).items,

  /**
   * 确认收款。实收与应付不一致时后端要求必须填差异说明。
   * 走的是与微信支付回调同一条路径，重复确认会返回 DUPLICATE 而不是二次推进订单。
   */
  confirm: (orderId: number, paidCents: number, externalRef: string, note: string) =>
    http.put<ConfirmResult>(`/api/admin/payment/${orderId}/confirm`, {
      paidCents,
      externalRef,
      note,
    }),

  /** 驳回凭证：订单退回待支付，粉丝可重新上传。原因必填。 */
  reject: (orderId: number, reason: string) =>
    http.put<{ status: string; statusLabel: string; reason: string }>(
      `/api/admin/payment/${orderId}/reject`,
      { reason },
    ),

  settings: () => http.get<PaymentSetting[]>('/api/admin/payment/settings'),

  updateSetting: (provider: string, patch: SettingPatch) =>
    http.put<PaymentSetting>(`/api/admin/payment/settings/${provider}`, patch),

  setEnabled: (provider: string, enabled: boolean) =>
    http.put<PaymentSetting>(
      `/api/admin/payment/settings/${provider}/enabled/${enabled}`,
    ),
}
