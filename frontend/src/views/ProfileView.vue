<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Image as VanImage, Skeleton } from 'vant'
import { fetchCurrentUser, type CurrentUser } from '@/api/session'
import { fanLevelApi, type FanLevel } from '@/api/fanLevel'
import { centsToYuan } from '@/utils/money'
import FanTabBar from '@/components/FanTabBar.vue'

const router = useRouter()
const me = ref<CurrentUser | null>(null)
const level = ref<FanLevel | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    me.value = await fetchCurrentUser()
  } finally {
    loading.value = false
  }
  // 等级加载失败不该挡住整页，单独 catch 掉
  try {
    level.value = await fanLevelApi.mine()
  } catch {
    level.value = null
  }
})
</script>

<template>
  <div class="page">
    <NavBar title="我的" />

    <Skeleton v-if="loading" avatar :row="3" style="padding: 16px" />

    <template v-else-if="me">
      <!-- 头部：头像、昵称、等级与累计消费合成一张卡，而不是四段分开的灰条 -->
      <section class="hero">
        <VanImage :src="me.avatarUrl" width="56" height="56" round fit="cover" />
        <div class="who">
          <p class="name">
            {{ me.nickname }}
            <span v-if="level" class="level">{{ level.name }}</span>
          </p>
          <p v-if="me.placeholderProfile" class="hint">微信资料未获取到，不影响使用</p>
        </div>
      </section>

      <!-- 等级只影响展示，不影响下单价格与分成 -->
      <section v-if="level" class="block level-card">
        <div class="row">
          <span>累计消费</span>
          <span class="num spent">¥{{ centsToYuan(level.spentCents) }}</span>
        </div>
        <p v-if="level.toNextCents !== null" class="to-next">
          还差 <b class="num">¥{{ centsToYuan(level.toNextCents) }}</b> 升到 {{ level.nextName }}
        </p>
        <p v-else class="to-next">已是最高等级</p>
      </section>

      <nav class="block menu">
        <button @click="router.push('/me/chats')"><span>消息</span></button>
        <button @click="router.push('/feed')"><span>动态广场</span></button>
        <button @click="router.push('/leaderboard')"><span>打赏榜</span></button>
        <button @click="router.push('/orders')"><span>我的订单</span></button>
        <button @click="router.push('/me/favorites')"><span>我的收藏</span></button>
        <button @click="router.push('/me/coupons')"><span>我的优惠券</span></button>
        <!-- 只有入口没有充值：元子页本身也不含任何充值、兑换与提现 -->
        <button @click="router.push('/me/credits')"><span>我的元子</span></button>
      </nav>

      <!-- 身份态切换：只有主播角色才看得到 -->
      <nav v-if="me.talent" class="block menu">
        <button @click="router.push('/talent')"><span>进入主播工作台</span></button>
      </nav>

      <nav v-if="me.operator" class="block menu">
        <button @click="router.push('/admin')"><span>进入运营后台</span></button>
      </nav>

      <div class="tab-space"></div>
    </template>

    <FanTabBar />
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
}

.hero {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-5) var(--sp-4);
}

.who {
  min-width: 0;
}

.name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.level {
  margin-left: var(--sp-2);
  padding: 1px 7px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--c-brand-deep);
  background: var(--c-brand-soft);
  vertical-align: middle;
}

.hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.block {
  margin: var(--sp-3) var(--sp-4) 0;
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  overflow: hidden;
}

.level-card {
  padding: var(--sp-4);
}

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--fs-md);
}

.row > span:first-child {
  color: var(--c-text-2);
}

.spent {
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--c-price);
}

.to-next {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.to-next b {
  color: var(--c-text-2);
}

.menu button {
  width: 100%;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-4);
  font-size: var(--fs-md);
  color: var(--c-text);
  text-align: left;
  cursor: pointer;
}

.menu button + button {
  border-top: 1px solid var(--c-border);
}

/* 右侧箭头用边框画，不引图标字体 */
.menu button::after {
  content: '';
  flex: none;
  width: 7px;
  height: 7px;
  border-top: 1.5px solid var(--c-text-3);
  border-right: 1.5px solid var(--c-text-3);
  transform: rotate(45deg);
}

.tab-space {
  height: 68px;
}
</style>
