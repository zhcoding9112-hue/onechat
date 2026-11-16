import { http } from '@/api/http'

export interface AdminCreditTransaction {
  id: number
  delta: number
  balanceAfter: number
  type: string
  reason: string
  operatorUserId: number | null
  createdAt: string
}

export interface AdminCreditAccount {
  userId: number
  nickname: string
  balance: number
  transactions: AdminCreditTransaction[]
}

export const creditAdminApi = {
  account: (userId: number, page = 0, size = 50) =>
    http.get<AdminCreditAccount>(
      `/api/admin/credits/${userId}?page=${page}&size=${size}`,
    ),

  /** 发放：数量必须为正，原因必填。 */
  grant: (userId: number, amount: number, reason: string) =>
    http.post<AdminCreditTransaction>(`/api/admin/credits/${userId}/grant`, { amount, reason }),

  /** 差错调整：可正可负，原因必填。 */
  adjust: (userId: number, delta: number, reason: string) =>
    http.post<AdminCreditTransaction>(`/api/admin/credits/${userId}/adjust`, { delta, reason }),
}
