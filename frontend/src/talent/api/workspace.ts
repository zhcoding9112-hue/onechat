import { http } from '@/api/http'
import type { OrderView } from '@/api/order'

export interface MyProfile {
  talentId: number
  nickname: string
  coverUrl: string | null
  intro: string | null
  tags: string[]
  online: boolean
  listed: boolean
  audioUrl: string | null
  audioSeconds: number | null
}

export interface IncomeEntry {
  orderId: number
  orderRef: string
  serviceItemName: string
  serviceSpecName: string
  payableCents: number
  commissionRateBp: number
  talentCents: number
  completedAt: string | null
}

/** 主播只拿得到聚合值，服务端刻意不下发任何可追溯到订单或粉丝的字段。 */
export interface SurveyAggregate {
  count: number
  average: number | null
  distribution: Record<string, number>
}

export interface IncomeView {
  totalCents: number
  completedOrderCount: number
  monthlyCents: Record<string, number>
  entries: IncomeEntry[]
}

const BASE = '/api/talent/workspace'

export const workspaceApi = {
  me: () => http.get<MyProfile>(`${BASE}/me`),
  setOnline: (online: boolean) => http.put<MyProfile>(`${BASE}/online/${online}`),
  pendingOrders: () => http.get<OrderView[]>(`${BASE}/pending-orders`),
  activeOrders: () => http.get<OrderView[]>(`${BASE}/active-orders`),
  historyOrders: () => http.get<OrderView[]>(`${BASE}/history-orders`),
  accept: (orderId: number) => http.put<OrderView>(`${BASE}/orders/${orderId}/accept`),
  complete: (orderId: number) => http.put<OrderView>(`${BASE}/orders/${orderId}/complete`),
  income: () => http.get<IncomeView>(`${BASE}/income`),
  survey: () => http.get<SurveyAggregate>(`${BASE}/survey`),
}

/**
 * 剩余接单时间的展示文案。
 * 到期返回 null，调用方据此隐藏接单按钮——但真正的拦截在服务端。
 */
export function remainingText(deadline: string | null): string | null {
  if (!deadline) return null
  const ms = new Date(deadline).getTime() - Date.now()
  if (ms <= 0) return null
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
