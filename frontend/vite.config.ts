import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type ProxyOptions } from 'vite'
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
  proxy.on('proxyReq', (proxyReq) => {
    const current = String(proxyReq.getHeader('user-agent') ?? '').toLowerCase()
    if (current.includes('micromessenger') || current.includes('wechatdevtools')) {
      return
    }
    proxyReq.setHeader('user-agent', DEV_WECHAT_UA)
  })
}

export default defineConfig({
  plugins: [
    stripRemoteIconFont(),
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
