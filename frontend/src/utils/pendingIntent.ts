/**
 * 授权前挂起的用户意图。
 *
 * 粉丝未登录时点收藏，会被带去微信授权。授权是一次整页跳转，组件状态全部丢失，
 * 所以必须把「他本来想做什么」存在跳转之外的地方，回来后再补做。
 *
 * 用 sessionStorage 而不是 localStorage：意图只在这一次会话里有效，
 * 留到下次打开再执行会让粉丝莫名其妙多出一条收藏。
 */

const KEY = 'pending-intent'

export interface PendingIntent {
  /** 目前只有收藏一种。后续新增意图在这里扩联合类型。 */
  type: 'favorite'
  talentId: number
  /** 写入时间戳，用于丢弃过期意图。 */
  at: number
}

/** 超过这个时长的意图不再执行：粉丝多半已经忘了自己点过什么。 */
const MAX_AGE_MS = 10 * 60 * 1000

export function savePendingIntent(intent: Omit<PendingIntent, 'at'>) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ ...intent, at: Date.now() }))
  } catch {
    // 隐私模式下 sessionStorage 可能抛异常。存不下就算了，
    // 粉丝回来后自己再点一次即可，不该因此让跳转失败。
  }
}

/** 取出并清除。只允许消费一次，避免刷新页面时重复执行。 */
export function takePendingIntent(): PendingIntent | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    sessionStorage.removeItem(KEY)
    const intent = JSON.parse(raw) as PendingIntent
    if (!intent || typeof intent.talentId !== 'number') return null
    if (Date.now() - intent.at > MAX_AGE_MS) return null
    return intent
  } catch {
    return null
  }
}
