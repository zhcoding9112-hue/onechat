import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'

export default defineConfig({
  plugins: [
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
    },
  },
})
