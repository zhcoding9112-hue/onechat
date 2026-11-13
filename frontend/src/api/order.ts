import { http } from './http'

/** 与后端 OrderStatus 一一对应。 */
export const OrderStatus = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PENDING_CONFIRM: 'PENDING_CONFIRM',
  PENDING_ACCEPT: 'PENDING_ACCEPT',
  ACCEPTED: 'ACCEPTED',
  COMPLETED: 'COMPLETED',
  TIMEOUT: 'TIMEOUT',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED',
} as const

export type OrderStatusValue = (typeof OrderStatus)[keyof typeof OrderStatus]

/** 服务端算好的可用操作，前端据此决定按钮，不自己判断状态。 */
export type OrderAction =
  | 'UPLOAD_PROOF'
  | 'REORDER'
  | 'RATE'
  | 'ACCEPT'
  | 'COMPLETE'
  | 'CONFIRM_PAYMENT'
  | 'REJECT_PROOF'
  | 'CANCEL'
  | 'MARK_REFUNDED'

export interface OrderView {
  id: number
  orderRef: string
  talentId: number
  talentNickname: string | null
  serviceItemName: string
  serviceSpecName: string
  unitPriceCents: number
  quantity: number
  payableCents: number
  paidCents: number | null
  status: OrderStatusValue
  statusLabel: string
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
  actions: OrderAction[]
}

export interface HistoryEntry {
  fromStatus: string | null
  fromLabel: string | null
  toStatus: string
  toLabel: string
  operatorType: string
  reason: string | null
  occurredAt: string
}

export interface CreateOrderRequest {
  talentId: number
  serviceSpecId: number
  quantity: number
  contact: string
  remark?: string
}

export const orderApi = {
  create: (body: CreateOrderRequest) => http.post<OrderView>('/api/orders', body),
  myOrders: () => http.get<OrderView[]>('/api/orders'),
  detail: (id: number) => http.get<OrderView>(`/api/orders/${id}`),
  history: (id: number) => http.get<HistoryEntry[]>(`/api/orders/${id}/history`),
}

/** 状态对应的展示色，与 Vant 的 tag type 对齐。 */
export function statusTagType(status: OrderStatusValue): 'primary' | 'success' | 'warning' | 'danger' | 'default' {
  switch (status) {
    case OrderStatus.PENDING_PAYMENT:
    case OrderStatus.PENDING_CONFIRM:
      return 'warning'
    case OrderStatus.PENDING_ACCEPT:
    case OrderStatus.ACCEPTED:
      return 'primary'
    case OrderStatus.COMPLETED:
      return 'success'
    case OrderStatus.TIMEOUT:
    case OrderStatus.REFUNDED:
      return 'danger'
    default:
      return 'default'
  }
}

/** 各状态下给粉丝看的一句话说明。 */
export function statusHint(order: OrderView): string {
  switch (order.status) {
    case OrderStatus.PENDING_PAYMENT:
      return '请按收款信息付款并上传支付凭证'
    case OrderStatus.PENDING_CONFIRM:
      return '凭证已提交，运营核对到账后将通知主播接单'
    case OrderStatus.PENDING_ACCEPT:
      return '已收款，正在等待主播接单'
    case OrderStatus.ACCEPTED:
      return '主播已接单，稍后会通过你填写的联系方式找你'
    case OrderStatus.COMPLETED:
      return '服务已完成'
    case OrderStatus.TIMEOUT:
      return '主播未在时限内接单，我们会为你安排退款'
    case OrderStatus.CANCELLED:
      return order.closeReason ?? '订单已取消'
    case OrderStatus.REFUNDED:
      return '退款已完成'
    default:
      return ''
  }
}

export interface MySurvey {
  orderId: number
  score: number
  createdAt: string
}

export const surveyApi = {
  submit: (orderId: number, score: number) =>
    http.post<MySurvey>(`/api/orders/${orderId}/survey`, { score }),
  mine: (orderId: number) => http.get<MySurvey | null>(`/api/orders/${orderId}/survey`),
}
