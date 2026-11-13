import { http } from './http'

export interface TipView {
  id: number
  tipRef: string
  talentId: number
  talentNickname: string | null
  amountCents: number
  status: string
  statusLabel: string
  message: string | null
  payDeadline: string | null
  paidAt: string | null
  createdAt: string
}

export interface TipLimits {
  minCents: number
  maxCents: number
}

export interface TipEntry {
  id: number
  tipRef: string
  amountCents: number
  talentCents: number
  message: string | null
  paidAt: string | null
}

export const tipApi = {
  limits: () => http.get<TipLimits>('/api/tips/limits'),
  create: (talentId: number, amountCents: number, message?: string) =>
    http.post<TipView>('/api/tips', { talentId, amountCents, message }),
  myTips: () => http.get<TipView[]>('/api/tips'),
  receivedTips: () => http.get<TipEntry[]>('/api/talent/workspace/tips'),
}
