#!/usr/bin/env node
/**
 * 量粉丝端首屏要下载的 JS 体积。
 *
 * 首屏 = 入口 chunk + 它静态依赖的全部 chunk + 落地路由（'/'，主播列表）那一块
 * 及其依赖。用 Vite 的 manifest 顺着 imports 闭包走一遍，不猜也不靠人工挑文件。
 *
 * 为什么不直接数 dist 下所有 .js：那里面有运营后台、主播工作台、以及十几个
 * 懒加载路由，它们一个字节也不会出现在粉丝打开首页时的下载里。
 *
 *   node scripts/first-screen.mjs [dist 目录]      # 只打印
 *   node scripts/first-screen.mjs --check          # 超过上限就退出码 1
 */
import { readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 改版前（2bf9ef1，第 1 组之前）的首屏 JS 字节数，实测值。
 * 同一个数字在真实浏览器里按 resource timing 量过一次，逐字节一致。
 */
const BASELINE_BYTES = 158552

/** tasks 12.3 的上限：不超过改版前的 1.5 倍。 */
const MAX_RATIO = 1.5

const args = process.argv.slice(2)
const check = args.includes('--check')
const dist = args.find((a) => !a.startsWith('--')) ?? 'dist'
const manifest = JSON.parse(readFileSync(join(dist, '.vite/manifest.json'), 'utf8'))

const entry = Object.values(manifest).find((c) => c.isEntry)
if (!entry) {
  console.error('manifest 里没有入口 chunk，确认 vite.config.ts 开了 build.manifest')
  process.exit(1)
}

/** 落地路由的源文件。改首页组件时要同步改这里。 */
const LANDING = 'src/views/TalentListView.vue'

const seen = new Set()
const files = []

function walk(key) {
  if (!key || seen.has(key)) return
  seen.add(key)
  const chunk = manifest[key]
  if (!chunk) return
  if (chunk.file?.endsWith('.js')) files.push(chunk.file)
  // 只跟 imports（静态依赖）；dynamicImports 是别的路由，首屏不下载
  for (const dep of chunk.imports ?? []) walk(dep)
}

walk(Object.keys(manifest).find((k) => manifest[k].isEntry))
walk(LANDING)

let total = 0
for (const f of files) total += statSync(join(dist, f)).size

const ratio = total / BASELINE_BYTES
const limit = Math.round(BASELINE_BYTES * MAX_RATIO)
const report = {
  files: files.length,
  bytes: total,
  baselineBytes: BASELINE_BYTES,
  ratio: Number(ratio.toFixed(3)),
  limitBytes: limit,
}
console.log(JSON.stringify(report, null, 2))

if (check && total > limit) {
  console.error(
    `首屏 JS ${total} 字节，超过改版前 ${BASELINE_BYTES} 字节的 ${MAX_RATIO} 倍（上限 ${limit}）。` +
      '若确属必要，请先确认不是误把某个路由从懒加载改成了静态 import。',
  )
  process.exit(1)
}
