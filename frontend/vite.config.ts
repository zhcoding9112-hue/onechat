import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type Plugin, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'

/**
 * 去掉 Vant 图标字体里指向第三方 CDN 的那条 woff 回退。
 *
 * Vant 的 @font-face 第一条是内联的 woff2 data URI，第二条是
 * `//at.alicdn.com/...woff`。现代浏览器用第一条，不会发这个请求——实测粉丝端
 * 各页的资源请求里确实没有它。但只要这条还在产物里，一个不支持 woff2 的客户端
 * 就会让粉丝的浏览器去连一个我们不控制的域名；而本站是 Vue 3 应用，
 * 那种客户端本来也跑不起来。与其留着一个永远不该被用到的外部依赖，不如删掉。
 */
function stripRemoteIconFont() {
  const REMOTE = /,\s*url\(\/\/at\.alicdn\.com\/[^)]*\)\s*format\((['"])woff\1\)/g
  return {
    name: 'strip-remote-icon-font',
    generateBundle(_options: unknown, bundle: Record<string, { type: string; source?: unknown }>) {
      for (const file of Object.values(bundle)) {
        if (file.type === 'asset' && typeof file.source === 'string') {
          file.source = file.source.replace(REMOTE, '')
        }
      }
    },
  }
}

/**
 * 开发期把转发给后端的 User-Agent 伪造成微信内置浏览器。
 *
 * 后端有一道 UA 门禁：授权入口只认 UA 里的 MicroMessenger（或微信开发者工具的
 * wechatdevtools），其余一律跳「请在微信中打开」。这道门禁是对的——粉丝的身份
 * 只能由微信授权建立，在桌面浏览器里走下去也拿不到 openid。
 * 但它让本机用普通浏览器连一眼页面都看不到，只能开手机或开发者工具。
 *
 * 所以这里只在 **vite dev 的代理** 上动手：把 /api 请求的 UA 换掉，
 * 后端就当它来自微信，配合本机的授权桩（任何 code 都当 openid）
 * 就能在桌面浏览器里走完整个流程。
 *
 * 几条边界：
 * - 只挂在 server.proxy 上。生产是 nginx 转发，这段代码不在产物里，也没有运行时机
 * - 请求本来就带微信标记（手机微信、微信开发者工具访问同一个 dev server）时不覆盖，
 *   保留真实 UA，免得把真机联调的现场改掉
 * - UA 只骗过「引导去微信打开」这个体验判断，不是安全边界：后端自己在注释里就写明了
 *   伪造 UA 的请求走到授权环节同样拿不到 openid
 */
const DEV_WECHAT_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 ' +
  '(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49(0x18003128) NetType/WIFI Language/zh_CN'

const fakeWechatUserAgent: NonNullable<ProxyOptions['configure']> = (proxy) => {
  proxy.on('proxyReq', (proxyReq, req) => {
    const current = String(proxyReq.getHeader('user-agent') ?? '').toLowerCase()
    if (!current.includes('micromessenger') && !current.includes('wechatdevtools')) {
      proxyReq.setHeader('user-agent', DEV_WECHAT_UA)
    }

    // 隧道（cloudflared 等）在外面是 https，到这里是 http。原样把协议头带给后端，
    // 否则后端拼出来的授权回跳是 http://，在 https 页面里就是一次降级跳转。
    const proto = req.headers['x-forwarded-proto']
    if (typeof proto === 'string' && proto) {
      proxyReq.setHeader('x-forwarded-proto', proto)
    }
  })
}

/**
 * 开发期把 demo-assets/ 当作静态目录挂上，URL 与原来放在 public/ 时一模一样。
 *
 * 这些文件（模拟主播的合成语音、模拟动态配图、装饰头像框）只服务于开发预览。
 * 放在 public/ 下时会被原样拷进 dist——public/ 不参与摇树，演示代码被摇掉了，
 * 8.5 MB 的素材照样进生产包、发布到服务器，而线上没有任何页面会请求它们。
 *
 * 所以把它们挪出 public/，改由这个插件在 vite dev 时提供。`apply: 'serve'`
 * 保证它在 build 时根本不存在，生产产物里不可能再出现这些文件。
 *
 * 支持 Range 请求：Safari / iOS 的微信 WebView 对媒体一律先发 Range，
 * 只回 200 全量会导致音频拒播。
 */
function demoAssets(): Plugin {
  const root = fileURLToPath(new URL('./demo-assets', import.meta.url))
  const types: Record<string, string> = {
    '.png': 'image/png',
    '.webp': 'image/webp',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.mp3': 'audio/mpeg',
  }

  return {
    name: 'demo-assets',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const urlPath = decodeURIComponent((req.url ?? '').split('?')[0])
        const file = path.resolve(root, '.' + path.posix.normalize(urlPath))
        // 必须落在 demo-assets 里面：normalize 之后再比一次，挡住 ../ 穿越
        if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
          return next()
        }

        const size = fs.statSync(file).size
        const type = types[path.extname(file).toLowerCase()] ?? 'application/octet-stream'
        res.setHeader('Content-Type', type)
        res.setHeader('Accept-Ranges', 'bytes')

        const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? '')
        if (range) {
          const start = range[1] ? Number(range[1]) : 0
          const end = range[2] ? Math.min(Number(range[2]), size - 1) : size - 1
          if (start >= size || end < start) {
            res.statusCode = 416
            res.setHeader('Content-Range', `bytes */${size}`)
            return res.end()
          }
          res.statusCode = 206
          res.setHeader('Content-Range', `bytes ${start}-${end}/${size}`)
          res.setHeader('Content-Length', String(end - start + 1))
          return fs.createReadStream(file, { start, end }).pipe(res)
        }

        res.setHeader('Content-Length', String(size))
        fs.createReadStream(file).pipe(res)
      })
    },
  }
}

export default defineConfig({
  plugins: [
    stripRemoteIconFont(),
    demoAssets(),
    vue(),
    // Element Plus 按需引入：只打包实际用到的组件与样式，
    // 并随 /admin 路由进入异步分块，不污染粉丝端主包。
    Components({
      dts: 'src/components.d.ts',
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
    }),
    // 补齐以函数方式调用的组件（ElMessage / ElMessageBox 等）的样式
    ElementPlus({ useSource: true }),
  ],
  build: {
    // 产出 .vite/manifest.json，供 scripts/first-screen.mjs 离线量首屏体积。
    // 没有它就只能开浏览器数网络请求，那种数字没法在 CI 里回归。
    manifest: true,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 5173,
    /**
     * 允许用隧道域名访问开发服务器。
     *
     * Vite 6 会按 Host 头拦陌生域名（防 DNS 重绑定），cloudflared 的随机域名
     * 一律被 403 挡掉。临时给人看时隧道每次重启都换一个随机前缀，
     * 所以这里放的是整个 trycloudflare.com，而不是某一个具体域名。
     *
     * 只在 vite dev 生效。另外：把开发服务器挂到公网之前先想清楚谁能进来——
     * 本机跑的是授权桩，谁打开谁就直接登录。
     */
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      // 开发期把 /api 转给后端，避免跨域并让会话 Cookie 同源下发。
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://127.0.0.1:8080',
        changeOrigin: false,
        // 开发期伪造微信 UA，否则桌面浏览器被门禁挡在「请在微信中打开」
        configure: fakeWechatUserAgent,
      },
      // 上传的文件（封面、试听、支付凭证）由后端静态目录提供，
      // 开发期不转发的话这些图片一律 404，后台对账看不到凭证。
      '/uploads': {
        target: process.env.VITE_API_TARGET ?? 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
})
