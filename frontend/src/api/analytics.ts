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

interface TrackOptions {
  talentId?: number
  serviceItemId?: number
  props?: Record<string, unknown>
}

export function track(eventKey: ClientEventKey, options: TrackOptions = {}): void {
  void fetch('/api/analytics/track', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ eventKey, ...options }),
    // 页面跳走时也要把这条送出去
    keepalive: true,
  }).catch(() => {
    // 静默：埋点失败不该让用户看到任何东西
  })
}
