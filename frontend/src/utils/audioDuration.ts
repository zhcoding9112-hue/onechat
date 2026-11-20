/**
 * 读取音频文件的时长（秒）。
 *
 * 后端不解析音频，试听时长必须由前端读出来一并提交，所以这一步不能省。
 *
 * 浏览器对时长的支持并不可靠：部分 VBR MP3 和没写完整头部的文件，
 * loadedmetadata 时拿到的 duration 是 Infinity 或 NaN。对这类文件先把
 * currentTime 推到一个极大值，浏览器扫完整个文件后会触发 durationchange
 * 并给出真实时长。扫描可能很慢，所以加了超时；拿不到就返回 null，
 * 由调用方让运营手填，而不是悄悄写一个错的数字进去。
 */
export function readAudioSeconds(file: File, timeoutMs = 8000): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const audio = new Audio()
    let done = false

    const finish = (value: number | null) => {
      if (done) return
      done = true
      clearTimeout(timer)
      audio.removeAttribute('src')
      // 不撤销 objectURL 会一直占着这份文件的内存
      URL.revokeObjectURL(url)
      resolve(value)
    }

    const timer = setTimeout(() => finish(null), timeoutMs)

    const take = () => {
      const d = audio.duration
      if (Number.isFinite(d) && d > 0) {
        // 不足一秒的片段也得算一秒，后端要求时长大于零
        finish(Math.max(1, Math.round(d)))
        return true
      }
      return false
    }

    audio.addEventListener('loadedmetadata', () => {
      if (take()) return
      // 触发全文件扫描，真实时长随后由 durationchange 给出
      audio.currentTime = 1e101
    })
    audio.addEventListener('durationchange', () => {
      take()
    })
    audio.addEventListener('error', () => finish(null))

    audio.preload = 'metadata'
    audio.src = url
  })
}
