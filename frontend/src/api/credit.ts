import { http } from './http'

/**
 * 元子（粉丝端）。
 *
 * 本阶段元子只记账：这里刻意只有读接口。没有 topUp / exchange / withdraw，
 * 因为后端也没有对应的端点——入口不是在前端藏起来，而是整条链路都不存在。
 */
export interface CreditTransactionView {
  id: number
  /** 变动数量，正为增、负为减。 */
  delta: number
  balanceAfter: number
  type: 'GRANT' | 'ADJUST' | 'DEDUCT'
  typeLabel: string
  reason: string
  createdAt: string
}

export interface CreditTransactionPage {
  items: CreditTransactionView[]
  total: number
  hasMore: boolean
}

export const creditApi = {
  balance: () => http.get<{ balance: number }>('/api/credits/me').then((r) => r.balance),

  transactions: (page = 0, size = 20) =>
    http.get<CreditTransactionPage>(
      `/api/credits/me/transactions?page=${page}&size=${size}`,
    ),
}
