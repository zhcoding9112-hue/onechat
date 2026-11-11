import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/auth/wechat-only',
    name: 'wechat-only',
    component: () => import('@/views/WechatOnlyView.vue'),
    meta: { title: '请在微信中打开' },
  },
  {
    path: '/auth/denied',
    name: 'auth-denied',
    component: () => import('@/views/AuthDeniedView.vue'),
    meta: { title: '需要微信授权' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
