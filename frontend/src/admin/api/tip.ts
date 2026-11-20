import { http } from '@/api/http'

/** 打赏状态。比订单少了接单、完成与超时：确认收款后直接计为已完成。 */
export type TipStatus =
  | 'PENDING_PAYMENT'
  | 'PENDING_CONFIRM'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED'

/** 运营看到的一笔打赏，与后端 TipAdminController.TipAdminView 一致。 */
export interface AdminTip {
  id: number
  tipRef: string
  fanUserId: number | null
  fanNickname: string
  talentId: number | null
  talentNickname: string | null
  amountCents: number
  paidCents: number | null
  /** 分成比例，基点。下单那一刻快照固定，此后改比例不影响这一笔。 */
  commissionRateBp: number | null
  guildCents: number | null
  talentCents: number | null
  status: TipStatus
  statusLabel: string
  /** 粉丝留言。私信性质，只对本人、目标主播与运营开放。 */
  message: string | null
  payDeadline: string | null
  paidAt: string | null
  closedAt: string | null
  closeReason: string | null
  createdAt: string
}

export interface TipLimits {
  id: number
  minCents: number
  maxCents: number
}

const BASE = '/api/admin/tips'

export const tipAdminApi = {
  list: (status?: TipStatus) =>
    http.get<AdminTip[]>(status ? `${BASE}?status=${status}` : BASE),

  /**
   * 确认收款。打赏不经过订单状态机，确认后直接计为已完成，
   * 同时写结算台账并计入榜单。
   */
  confirm: (tipId: number, paidCents: number, note: string) =>
    http.put<{ status: TipStatus }>(`${BASE}/${tipId}/confirm`, { paidCents, note }),

  /** 标记已退款：台账冲销，榜单按这笔原本计入的周期扣回。原因必填。 */
  refund: (tipId: number, reason: string) =>
    http.put<{ status: TipStatus }>(`${BASE}/${tipId}/refund`, { reason }),

  /** 取消未收款的打赏。打赏没有超时回收，挂着的记录只能从这里清。 */
  cancel: (tipId: number, reason: string) =>
    http.put<{ status: TipStatus }>(`${BASE}/${tipId}/cancel`, { reason }),

  limits: () => http.get<TipLimits>(`${BASE}/limits`),

  updateLimits: (minCents: number, maxCents: number) =>
    http.put<TipLimits>(`${BASE}/limits`, { minCents, maxCents }),
}
