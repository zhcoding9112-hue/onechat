import { http } from '@/api/http'

/** 运营看到的订单。字段与后端 OrderView 一致。 */
export interface AdminOrder {
  id: number
  orderRef: string
  talentId: number | null
  talentNickname: string | null
  serviceItemName: string | null
  serviceSpecName: string | null
  unitPriceCents: number
  quantity: number
  originalCents: number
  couponDiscountCents: number
  payableCents: number
  paidCents: number | null
  status: string
  statusLabel: string
  /** 粉丝微信号，仅该单主播与运营可见。 */
  contact: string | null
  remark: string | null
  payDeadline: string | null
  acceptDeadline: string | null
  paidAt: string | null
  acceptedAt: string | null
  completedAt: string | null
  closedAt: string | null
  closeReason: string | null
  createdAt: string
  /** 后端算好的可用操作，前端据此决定按钮。 */
  actions: string[]
}

/** 订单时限配置。只影响此后创建的订单。 */
export interface OrderDeadlineSetting {
  id: number
  payMinutes: number
  acceptMinutes: number
}

/** 一次状态流转。与后端 OrderController.HistoryEntry 一致。 */
export interface OrderHistoryEntry {
  fromStatus: string | null
  fromLabel: string | null
  toStatus: string
  toLabel: string
  operatorType: 'FAN' | 'TALENT' | 'OPERATOR' | 'SYSTEM'
  reason: string | null
  occurredAt: string
}

export const orderAdminApi = {
  list: (status?: string) =>
    http.get<AdminOrder[]>(
      status ? `/api/admin/orders?status=${encodeURIComponent(status)}` : '/api/admin/orders',
    ),

  /** 待退款队列：已超时但尚未标记退款的订单，最早超时的在前。 */
  refundQueue: () => http.get<AdminOrder[]>('/api/admin/orders/refund-queue'),

  /**
   * 状态流转记录。
   *
   * 走的是粉丝端那个接口——它本来就对运营开放，字段也够用，
   * 没必要在 admin 下再开一个一模一样的。
   */
  history: (orderId: number) =>
    http.get<OrderHistoryEntry[]>(`/api/orders/${orderId}/history`),

  /** 人工取消。原因必填，会记在订单上并留痕。 */
  cancel: (orderId: number, reason: string) =>
    http.put<AdminOrder>(`/api/admin/orders/${orderId}/cancel`, { reason }),

  /** 标记已退款。重复标记是幂等的，不会产生第二条记录。 */
  markRefunded: (orderId: number, reason: string) =>
    http.put<AdminOrder>(`/api/admin/orders/${orderId}/refund`, { reason }),

  deadlines: () => http.get<OrderDeadlineSetting>('/api/admin/orders/deadlines'),

  updateDeadlines: (payMinutes: number, acceptMinutes: number) =>
    http.put<OrderDeadlineSetting>('/api/admin/orders/deadlines', {
      payMinutes,
      acceptMinutes,
    }),
}
