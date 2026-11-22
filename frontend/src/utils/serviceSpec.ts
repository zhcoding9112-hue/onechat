/**
 * 档位名称与时长的去重展示。
 *
 * 档位名称是运营自己填的，绝大多数就填成「30分钟」；这时候再在旁边补一行
 * 「30 分钟」属于同一句话说两遍。只有名称本身没带时长（比如「深夜陪聊」）时，
 * 时长才是额外信息，才值得单独显示。
 */
export function durationHint(spec: { name: string; durationMinutes?: number | null }): string | null {
  const minutes = spec.durationMinutes
  if (!minutes) return null
  if (spec.name.includes(String(minutes))) return null
  return `${minutes} 分钟`
}
