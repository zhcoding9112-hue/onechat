<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Cell, CellGroup, Image as VanImage, Skeleton } from 'vant'
import { fetchCurrentUser, type CurrentUser } from '@/api/session'

const router = useRouter()
const me = ref<CurrentUser | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    me.value = await fetchCurrentUser()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <NavBar title="我的" />

  <Skeleton v-if="loading" avatar :row="3" style="padding: 16px" />

  <template v-else-if="me">
    <section class="header">
      <VanImage :src="me.avatarUrl" width="56" height="56" round fit="cover" />
      <div class="info">
        <p class="name">{{ me.nickname }}</p>
        <p v-if="me.placeholderProfile" class="hint">微信资料未获取到，不影响使用</p>
      </div>
    </section>

    <CellGroup inset style="margin-top: 12px">
      <Cell title="我的订单" is-link @click="router.push('/orders')" />
      <Cell title="我的收藏" is-link @click="router.push('/me/favorites')" />
      <!-- 只有入口没有充值：元子页本身也不含任何充值、兑换与提现 -->
      <Cell title="我的元子" is-link @click="router.push('/me/credits')" />
    </CellGroup>

    <!-- 身份态切换：只有主播角色才看得到 -->
    <CellGroup v-if="me.talent" inset title="主播" style="margin-top: 12px">
      <Cell title="进入主播工作台" is-link @click="router.push('/talent')" />
    </CellGroup>

    <CellGroup v-if="me.operator" inset title="运营" style="margin-top: 12px">
      <Cell title="进入运营后台" is-link @click="router.push('/admin')" />
    </CellGroup>
  </template>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  background: #fff;
}

.name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #969799;
}
</style>
