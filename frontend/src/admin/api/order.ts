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

export const orderAdminApi = {
  list: (status?: string) =>
    http.get<AdminOrder[]>(
      status ? `/api/admin/orders?status=${encodeURIComponent(status)}` : '/api/admin/orders',
    ),
}
