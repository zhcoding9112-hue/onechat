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
    path: '/audition',
    name: 'audition',
    component: () => import('@/views/AuditionView.vue'),
    meta: { title: '试音选人' },
  },
  {
    path: '/match',
    name: 'random-match',
    component: () => import('@/views/MatchView.vue'),
    meta: { title: '随机匹配' },
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
    path: '/talents/:id/tip',
    name: 'tip-create',
    component: () => import('@/views/TipCreateView.vue'),
    meta: { title: '打赏' },
  },
  {
    path: '/tips/:id',
    name: 'tip-detail',
    component: () => import('@/views/TipDetailView.vue'),
    meta: { title: '打赏详情' },
  },
  {
    path: '/me/favorites',
    name: 'favorites',
    component: () => import('@/views/FavoritesView.vue'),
    meta: { title: '我的收藏' },
  },
  {
    path: '/feed',
    name: 'feed',
    component: () => import('@/views/FeedView.vue'),
    meta: { title: '动态广场' },
  },
  {
    path: '/feed/:id',
    name: 'feed-detail',
    component: () => import('@/views/FeedDetailView.vue'),
    meta: { title: '动态' },
  },
  {
    path: '/leaderboard',
    name: 'leaderboard',
    component: () => import('@/views/LeaderboardView.vue'),
    meta: { title: '打赏榜' },
  },
  {
    path: '/me/coupons',
    name: 'coupons',
    component: () => import('@/views/CouponsView.vue'),
    meta: { title: '我的优惠券' },
  },
  {
    path: '/me/credits',
    name: 'credits',
    component: () => import('@/views/CreditView.vue'),
    meta: { title: '我的元子' },
  },
  {
    path: '/me/chats',
    name: 'chat-list',
    component: () => import('@/views/ChatListView.vue'),
    meta: { title: '消息' },
  },
  {
    path: '/me/chats/:id',
    name: 'chat-room',
    component: () => import('@/views/ChatRoomView.vue'),
    meta: { title: '聊天' },
  },
  {
    path: '/me',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '我的' },
  },
  {
    // 主播工作台是同一工程里的一个身份态，不是独立应用。
    // 接口鉴权在服务端，前端不做角色判断——前端判断挡不住任何人。
    path: '/talent',
    component: () => import('@/talent/TalentLayout.vue'),
    children: [
      {
        path: '',
        name: 'talent-orders',
        component: () => import('@/talent/views/TalentOrdersView.vue'),
        meta: { title: '主播工作台' },
      },
      {
        path: 'auditions',
        name: 'talent-auditions',
        component: () => import('@/talent/views/TalentAuditionView.vue'),
        meta: { title: '试音需求' },
      },
      {
        path: 'feed',
        name: 'talent-feed',
        component: () => import('@/talent/views/TalentFeedView.vue'),
        meta: { title: '我的动态' },
      },
      {
        path: 'chats',
        name: 'talent-chat-list',
        component: () => import('@/talent/views/TalentChatListView.vue'),
        meta: { title: '消息' },
      },
      {
        path: 'chats/:id',
        name: 'talent-chat-room',
        component: () => import('@/talent/views/TalentChatRoomView.vue'),
        meta: { title: '聊天' },
      },
      {
        path: 'income',
        name: 'talent-income',
        component: () => import('@/talent/views/TalentIncomeView.vue'),
        meta: { title: '我的收入' },
      },
      {
        path: 'me',
        name: 'talent-me',
        component: () => import('@/talent/views/TalentMeView.vue'),
        meta: { title: '主播资料' },
      },
    ],
  },
  {
    // 运营后台与粉丝端同属一个工程，只是不同的路由分支。
    // 接口鉴权在服务端，前端这里不做角色判断——前端判断挡不住任何人。
    path: '/admin',
    component: () => import('@/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/admin/views/DashboardView.vue'),
        meta: { title: '转化看板' },
      },
      {
        path: 'payment',
        name: 'admin-payment',
        component: () => import('@/admin/views/PaymentView.vue'),
        meta: { title: '收款与对账' },
      },
      {
        path: 'talents',
        name: 'admin-talents',
        component: () => import('@/admin/views/TalentView.vue'),
        meta: { title: '主播管理' },
      },
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
      {
        path: 'feed',
        name: 'admin-feed',
        component: () => import('@/admin/views/FeedView.vue'),
        meta: { title: '内容审核' },
      },
      {
        path: 'chat',
        name: 'admin-chat',
        component: () => import('@/admin/views/ChatView.vue'),
        meta: { title: '会话查询' },
      },
      {
        path: 'fan-levels',
        name: 'admin-fan-levels',
        component: () => import('@/admin/views/FanLevelView.vue'),
        meta: { title: '粉丝等级' },
      },
      {
        path: 'coupons',
        name: 'admin-coupons',
        component: () => import('@/admin/views/CouponView.vue'),
        meta: { title: '优惠券' },
      },
      {
        path: 'credits',
        name: 'admin-credits',
        component: () => import('@/admin/views/CreditView.vue'),
        meta: { title: '元子记账' },
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
