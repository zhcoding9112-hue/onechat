import { ApiError, http } from '@/api/http'
import { ErrorCode } from '@/api/types'

/** 某主播某周期的结算行。周期内没流水的主播也会成行，各项为零。 */
export interface TalentStatement {
  talentId: number
  talentNickname: string
  period: string
  orderAmountCents: number
  tipAmountCents: number
  guildCents: number
  talentCents: number
  /** 促销成本（券抵扣），由公会承担，不从主播应得里扣。 */
  promoCostCents: number
  orderCount: number
  tipCount: number
  settled: boolean
  settledAt: string | null
}

/** 逐笔分账。已冲销的也在列表里，供纠纷追溯。 */
export interface SettlementEntry {
  id: number
  sourceType: 'ORDER' | 'TIP'
  sourceId: number
  sourceRef: string
  talentId: number
  amountCents: number
  commissionRateBp: number
  guildCents: number
  talentCents: number
  promoCostCents: number | null
  status: 'ACTIVE' | 'REVERSED'
  reversedAt: string | null
  reverseReason: string | null
  occurredAt: string
}

export interface SettlementMark {
  talentId: number
  period: string
  settledAt: string
  operatorId: number | null
  note: string | null
}

const BASE = '/api/admin/settlement'

/**
 * 下载 CSV。
 *
 * 不能走统一的 JSON 通路：响应体是文件而不是 ApiResponse。
 * 但失败时后端仍然返回 JSON 错误，所以这里按 Content-Type 分流，
 * 让报错和别处一样抛 ApiError，而不是把一段 JSON 当成表格存到本地。
 */
async function download(url: string, fallbackName: string): Promise<void> {
  const res = await fetch(url, { credentials: 'include' })
  const type = res.headers.get('Content-Type') ?? ''

  if (!res.ok || type.includes('application/json')) {
    let message = '导出失败'
    let code: string = ErrorCode.INTERNAL_ERROR
    try {
      const payload = await res.json()
      message = payload.message ?? message
      code = payload.code ?? code
    } catch {
      /* 响应体不是 JSON，用兜底文案 */
    }
    throw new ApiError(code, message, res.status)
  }

  const blob = await res.blob()
  // 文件名优先用后端给的，它带了周期和主播 id
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const match = /filename="([^"]+)"/.exec(disposition)
  const name = match ? match[1] : fallbackName

  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
}

export const settlementAdminApi = {
  /** 月度报表，period 形如 2026-11。 */
  report: (period: string) =>
    http.get<TalentStatement[]>(`${BASE}/report?period=${encodeURIComponent(period)}`),

  /** 某主播某周期的逐笔明细，含已冲销的。 */
  entries: (period: string, talentId: number) =>
    http.get<SettlementEntry[]>(
      `${BASE}/entries?period=${encodeURIComponent(period)}&talentId=${talentId}`,
    ),

  exportReport: (period: string) =>
    download(
      `${BASE}/report/export?period=${encodeURIComponent(period)}`,
      `settlement-${period}.csv`,
    ),

  exportEntries: (period: string, talentId: number) =>
    download(
      `${BASE}/entries/export?period=${encodeURIComponent(period)}&talentId=${talentId}`,
      `settlement-${period}-talent-${talentId}.csv`,
    ),

  /** 标记已线下结清。只记标记与时间，不发生任何资金动作。 */
  mark: (talentId: number, period: string, note: string) =>
    http.put<SettlementMark>(`${BASE}/marks/${talentId}/${period}`, { note }),

  unmark: (talentId: number, period: string) =>
    http.delete<void>(`${BASE}/marks/${talentId}/${period}`),
}
