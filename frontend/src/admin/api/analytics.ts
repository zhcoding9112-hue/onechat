import { http } from '@/api/http'

export interface FunnelStep {
  eventKey: string
  label: string
  uv: number
  conversionBp: number | null
  lostUv: number | null
}

export interface CoreMetrics {
  visitToPayBp: number | null
  acceptRateBp: number | null
  avgAcceptSeconds: number | null
  repurchaseBp: number | null
  acceptRateBelowThreshold: boolean
  acceptRateThresholdBp: number
  conversionTrustworthy: boolean
}

export interface Overview {
  from: string
  to: string
  funnel: FunnelStep[]
  metrics: CoreMetrics
}

export interface Breakdown {
  id: number
  name: string
  uvByKey: Record<string, number>
  acceptRateBp: number | null
}

export interface EntryBreakdown {
  source: string
  label: string
  /** 该入口自身的点击事件键，例如 ENTRY_POPULAR。 */
  eventKey: string
  uvByKey: Record<string, number>
}

export interface EntryBreakdownView {
  steps: { eventKey: string; label: string }[]
  rows: EntryBreakdown[]
  /** 各入口之和。服务端就是按行加出来的，所以与表格永远对得上。 */
  totals: Record<string, number>
}

const BASE = '/api/admin/analytics'

function range(from?: string, to?: string) {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const q = params.toString()
  return q ? `?${q}` : ''
}

export const analyticsApi = {
  funnel: (from?: string, to?: string) => http.get<Overview>(`${BASE}/funnel${range(from, to)}`),
  byTalent: (from?: string, to?: string) =>
    http.get<Breakdown[]>(`${BASE}/by-talent${range(from, to)}`),
  byService: (from?: string, to?: string) =>
    http.get<Breakdown[]>(`${BASE}/by-service${range(from, to)}`),
  byEntry: (from?: string, to?: string) =>
    http.get<EntryBreakdownView>(`${BASE}/by-entry${range(from, to)}`),
  settings: () => http.get<{ acceptRateThresholdBp: number }>(`${BASE}/settings`),
  updateThreshold: (acceptRateThresholdBp: number) =>
    http.put(`${BASE}/settings`, { acceptRateThresholdBp }),
}

/** 基点转百分比文案。null 表示该指标无意义（分母为零），显示为「—」。 */
export function bpText(bp: number | null): string {
  if (bp === null || bp === undefined) return '—'
  return `${(bp / 100).toFixed(2)}%`
}

/** 秒转可读时长。 */
export function durationText(seconds: number | null): string {
  if (seconds === null || seconds === undefined) return '—'
  if (seconds < 60) return `${seconds} 秒`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return s === 0 ? `${m} 分钟` : `${m} 分 ${s} 秒`
}
