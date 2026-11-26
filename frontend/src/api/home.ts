import { http } from './http'

export interface CategoryView {
  code: string
  name: string
}

export interface PopularTalentView {
  id: number
  nickname: string
  coverUrl: string | null
  online: boolean
}

/** 跑马灯项。刻意只有昵称与金额——后端不返回任何打赏人信息。 */
export interface MarqueeItem {
  nickname: string
  amountCents: number
}

export interface EntryView {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string | null
  targetUrl: string | null
}

export interface SupportInfo {
  title: string
  description: string
  hours: string
  wechatId: string
  qrCodeUrl: string
  serviceUrl: string
  configured: boolean
}

/*
 * 首页区块一律静默失败：区块取数失败时整块隐藏即可，不该给粉丝弹错误提示——
 * 一个运营位挂了不是粉丝需要知道的事，弹窗只会让首页看起来坏掉了。
 * 信息流不走这里，它失败时有自己的重试态。
 */
const SILENT = { toastOnError: false }

export const homeApi = {
  support: () => http.get<SupportInfo>('/api/home/support', SILENT),
  categories: () => http.get<CategoryView[]>('/api/home/categories', SILENT),
  popularTalents: () => http.get<PopularTalentView[]>('/api/home/popular-talents', SILENT),
  tipMarquee: () => http.get<MarqueeItem[]>('/api/home/tip-marquee', SILENT),
  entries: (type: 'GRID' | 'BANNER') =>
    http.get<EntryView[]>(`/api/home/entries?type=${type}`, SILENT),
}
