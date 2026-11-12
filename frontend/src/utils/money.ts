/**
 * 金额换算。
 *
 * 与后端一律以「分」通信，只在展示与输入框里用「元」。
 * 不用浮点做中间运算：0.1 + 0.2 在 JS 里不等于 0.3，金额经不起这种误差。
 */

/** 分 → 显示用的元字符串，固定两位小数。 */
export function centsToYuan(cents: number): string {
  const negative = cents < 0
  const abs = Math.abs(Math.trunc(cents))
  const text = `${Math.trunc(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
  return negative ? `-${text}` : text
}

/** 分 → 带 ¥ 前缀。 */
export function formatPrice(cents: number): string {
  return `¥${centsToYuan(cents)}`
}

/**
 * 用户输入的元 → 分。
 * 超过两位小数视为非法，返回 null 由调用方提示，而不是悄悄四舍五入。
 */
export function yuanToCents(input: string | number): number | null {
  const text = String(input).trim()
  if (text === '') return null
  if (!/^-?\d+(\.\d{1,2})?$/.test(text)) return null

  const negative = text.startsWith('-')
  const [intPart, fracPart = ''] = text.replace('-', '').split('.')
  const cents = Number(intPart) * 100 + Number(fracPart.padEnd(2, '0'))
  return negative ? -cents : cents
}

/** 基点 → 百分比数值，用于输入框。 */
export function bpToPercent(bp: number): number {
  return bp / 100
}

/**
 * 百分比 → 基点。
 * 先四舍五入到两位小数再乘，避免 12.34 * 100 得到 1233.9999999999998。
 */
export function percentToBp(percent: number | string): number | null {
  const text = String(percent).trim()
  if (text === '') return null
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null

  const [intPart, fracPart = ''] = text.split('.')
  const bp = Number(intPart) * 100 + Number(fracPart.padEnd(2, '0'))
  return bp > 10000 ? null : bp
}
