import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import './styles.css'
import App from './App.vue'
import { router } from './router'

// Element Plus 只给运营后台用，按需引入到 /admin 的异步分块里，
// 不进主包——粉丝端是微信里的移动 H5，首屏体积直接影响转化。
createApp(App).use(createPinia()).use(router).mount('#app')
