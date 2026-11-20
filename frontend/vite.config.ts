import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
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
