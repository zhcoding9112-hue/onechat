import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'talent-list',
    component: () => import('@/views/TalentListView.vue'),
    meta: { title: '陪聊服务' },
  },
  {
    path: '/talents/:id',
    name: 'talent-detail',
    component: () => import('@/views/TalentDetailView.vue'),
    meta: { title: '主播详情' },
  },
  {
    path: '/talents/:id/order',
    name: 'order-create',
    component: () => import('@/views/OrderCreateView.vue'),
    meta: { title: '确认下单' },
  },
  {
    path: '/orders',
    name: 'order-list',
    component: () => import('@/views/OrderListView.vue'),
    meta: { title: '我的订单' },
  },
  {
    path: '/orders/:id',
    name: 'order-detail',
    component: () => import('@/views/OrderDetailView.vue'),
    meta: { title: '订单详情' },
  },
  {
    path: '/orders/:id/payment',
    name: 'order-payment',
    component: () => import('@/views/OrderPaymentView.vue'),
    meta: { title: '订单支付' },
  },
  {
    // 脚手架自检页，联调用，不在导航中暴露
    path: '/__dev',
    name: 'dev-probe',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '自检' },
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
  {
    // 运营后台与粉丝端同属一个工程，只是不同的路由分支。
    // 接口鉴权在服务端，前端这里不做角色判断——前端判断挡不住任何人。
    path: '/admin',
    component: () => import('@/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/pricing' },
      {
        path: 'pricing',
        name: 'admin-pricing',
        component: () => import('@/admin/views/PricingView.vue'),
        meta: { title: '服务与价格' },
      },
      {
        path: 'commission',
        name: 'admin-commission',
        component: () => import('@/admin/views/CommissionView.vue'),
        meta: { title: '分成比例' },
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
