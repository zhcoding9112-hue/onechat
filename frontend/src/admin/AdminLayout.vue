<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { fetchCurrentUser, type CurrentUser } from '@/api/session'

const route = useRoute()
const router = useRouter()
const me = ref<CurrentUser | null>(null)
const loading = ref(true)

onMounted(async () => {
  // 解除粉丝端 750px 版心，详见 styles.css 里 html[data-app='admin'] 的说明
  document.documentElement.dataset.app = 'admin'
  try {
    me.value = await fetchCurrentUser()
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  delete document.documentElement.dataset.app
})

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <!-- 按需引入后没有全局 app.use(ElementPlus, { locale })，
       中文文案靠这里提供，否则确认弹窗会显示 Cancel / OK。 -->
  <el-config-provider :locale="zhCn">
  <el-container class="admin">
    <el-header class="admin-header">
      <span class="admin-title">运营后台</span>
      <!--
        :ellipsis="false" 关掉 Element Plus 的自动折叠。
        菜单项一多，默认行为会把后面的项藏进「…」里——1440px 下「会话查询」就已经看不见了，
        而且越往后加页面藏得越多。这里改成宽度不够时横向滚动，所有项始终在同一行里可达。
      -->
      <el-menu
        mode="horizontal"
        :default-active="route.path"
        :ellipsis="false"
        class="admin-menu"
        @select="go"
      >
        <el-menu-item index="/admin/dashboard">转化看板</el-menu-item>
        <el-menu-item index="/admin/payment">收款与对账</el-menu-item>
        <el-menu-item index="/admin/talents">主播管理</el-menu-item>
        <el-menu-item index="/admin/pricing">服务与价格</el-menu-item>
        <el-menu-item index="/admin/commission">分成比例</el-menu-item>
        <el-menu-item index="/admin/tips">打赏管理</el-menu-item>
        <el-menu-item index="/admin/coupons">优惠券</el-menu-item>
        <el-menu-item index="/admin/settlement">结算报表</el-menu-item>
        <el-menu-item index="/admin/credits">元子记账</el-menu-item>
        <el-menu-item index="/admin/fan-levels">粉丝等级</el-menu-item>
        <el-menu-item index="/admin/feed">内容审核</el-menu-item>
        <el-menu-item index="/admin/chat">会话查询</el-menu-item>
      </el-menu>
      <span class="admin-user">
        <template v-if="loading">加载中…</template>
        <template v-else-if="me">{{ me.nickname }}</template>
      </span>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
  </el-config-provider>
</template>

<style scoped>
.admin {
  min-height: 100vh;
  background: #f5f7fa;
}

.admin-header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
}

.admin-title {
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}

.admin-menu {
  flex: 1;
  min-width: 0;
  border-bottom: none;
  overflow-x: auto;
  flex-wrap: nowrap;
  scrollbar-width: thin;
}

.admin-menu::-webkit-scrollbar {
  height: 4px;
}

.admin-menu::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 2px;
}

.admin-menu :deep(.el-menu-item) {
  flex: none;
  white-space: nowrap;
}

.admin-user {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}
</style>
