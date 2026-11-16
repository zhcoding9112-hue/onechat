/**
 * 前端埋点上报。
 *
 * 全部 fire-and-forget：埋点绝不能阻塞页面，也绝不能因为失败而打扰用户。
 * 这里刻意不用统一的 http 封装——它会在失败时弹 toast，而埋点失败对用户毫无意义。
 */

export const ClientEvent = {
  OPEN_H5: 'OPEN_H5',
  VIEW_TALENT_LIST: 'VIEW_TALENT_LIST',
  VIEW_TALENT_DETAIL: 'VIEW_TALENT_DETAIL',
  CLICK_ORDER: 'CLICK_ORDER',
} as const

export type ClientEventKey = (typeof ClientEvent)[keyof typeof ClientEvent]

/**
 * 发现入口。与后端 EntrySource 枚举逐项对应。
 *
 * 没有「未知」这一项：认不出来的一律是 DIRECT（直接访问）。允许空值会让看板上
 * 按入口拆分的各行之和小于总数，而少掉的那部分没人知道去了哪。
 */
export const EntrySource = {
  CATEGORY_TAB: 'CATEGORY_TAB',
  TALENT_LIST: 'TALENT_LIST',
  POPULAR: 'POPULAR',
  GRID: 'GRID',
  BANNER: 'BANNER',
  AUDITION: 'AUDITION',
  RANDOM_MATCH: 'RANDOM_MATCH',
  CRUSH_WALL: 'CRUSH_WALL',
  FEED: 'FEED',
  LEADERBOARD: 'LEADERBOARD',
  FAVORITES: 'FAVORITES',
  CHAT: 'CHAT',
  DIRECT: 'DIRECT',
} as const

export type EntrySourceKey = (typeof EntrySource)[keyof typeof EntrySource]

/** 路由上携带来源的查询参数名。详情页据此归因。 */
export const ENTRY_QUERY = 'from'

/**
 * 把路由上的 `?from=` 解析成来源。
 *
 * 没有、或认不出来的，都是「直接访问」——分享链接进来的正是这种。
 */
export function entryFromRoute(value: unknown): EntrySourceKey {
  const key = typeof value === 'string' ? value.toUpperCase() : ''
  return (Object.values(EntrySource) as string[]).includes(key)
    ? (key as EntrySourceKey)
    : EntrySource.DIRECT
}

/** 带上来源的详情页路径。所有进详情的入口都应当走它，而不是自己拼字符串。 */
export function talentPath(talentId: number, from: EntrySourceKey): string {
  return `/talents/${talentId}?${ENTRY_QUERY}=${from}`
}

interface TrackOptions {
  talentId?: number
  serviceItemId?: number
  entrySource?: EntrySourceKey
  props?: Record<string, unknown>
}

function post(body: Record<string, unknown>): void {
  void fetch('/api/analytics/track', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    // 页面跳走时也要把这条送出去
    keepalive: true,
  }).catch(() => {
    // 静默：埋点失败不该让用户看到任何东西
  })
}

export function track(eventKey: ClientEventKey, options: TrackOptions = {}): void {
  post({ eventKey, ...options })
}

/** 入口被点击。事件键形如 ENTRY_POPULAR，不占漏斗节点。 */
export function trackEntry(source: EntrySourceKey, talentId?: number): void {
  post({ eventKey: `ENTRY_${source}`, talentId })
}
