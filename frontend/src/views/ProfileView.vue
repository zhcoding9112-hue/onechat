<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Skeleton } from 'vant'
import { fetchCurrentUser, type CurrentUser } from '@/api/session'
import { fanLevelApi, type FanLevel } from '@/api/fanLevel'
import LevelBadge from '@/components/LevelBadge.vue'
import AvatarFrame from '@/components/AvatarFrame.vue'
import { centsToYuan } from '@/utils/money'
import FanTabBar from '@/components/FanTabBar.vue'

const router = useRouter()
const me = ref<CurrentUser | null>(null)
const level = ref<FanLevel | null>(null)

/**
 * 当前等级内的进度。
 *
 * 接口只给「已花多少」和「还差多少」，本级起点要自己倒推：
 * 本级跨度 = 已花 + 还差 − 本级门槛，而本级门槛接口没给，
 * 所以退一步用「已花 /（已花 + 还差）」——它表达的是「离下一级还有多远」，
 * 对粉丝来说正是想看的那个意思，也不会出现跨级后进度倒退。
 */
const progress = computed(() => {
  const l = level.value
  if (!l || l.toNextCents === null) return 100
  const total = l.spentCents + l.toNextCents
  if (total <= 0) return 0
  return Math.min(100, Math.max(2, Math.round((l.spentCents / total) * 100)))
})
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
      <section class="hero" :class="level ? `tier${level.level}` : ''">
        <AvatarFrame :src="me.avatarUrl" :level="level?.level ?? 1" :size="56" />
        <div class="who">
          <p class="name">{{ me.nickname }}</p>
          <LevelBadge v-if="level" :level="level.level" :name="level.name" size="lg" />
          <p v-if="me.placeholderProfile" class="hint">微信资料未获取到，不影响使用</p>
        </div>
      </section>

      <!-- 等级只影响展示，不影响下单价格与分成 -->
      <section v-if="level" class="block level-card">
        <div class="row">
          <span>累计消费</span>
          <span class="num spent">¥{{ centsToYuan(level.spentCents) }}</span>
        </div>

        <!-- 进度条：只有还能升级时才画，满级画一条满的会让人以为还差一点 -->
        <template v-if="level.toNextCents !== null">
          <div class="bar"><i :style="{ width: progress + '%' }" /></div>
          <p class="to-next">
            还差 <b class="num">¥{{ centsToYuan(level.toNextCents) }}</b> 升到
            <LevelBadge :level="(level.nextLevel ?? level.level + 1)" :name="level.nextName ?? ''" />
          </p>
        </template>
        <p v-else class="to-next maxed">已是最高等级</p>
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
  border-radius: 0 0 var(--r-lg) var(--r-lg);
}

/* 昵称与徽章分两行：徽章带翅膀后比文字高，挤在一行会把昵称顶歪 */
.who {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--sp-1);
  min-width: 0;
}

.who {
  min-width: 0;
}

.name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

/* 进度条：细一点，不要喧宾夺主盖过上面的金额 */
.bar {
  margin: var(--sp-3) 0 var(--sp-2);
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--c-border);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: var(--r-pill);
  background: linear-gradient(90deg, var(--c-brand), #7fe3dc);
  transition: width 0.6s ease;
}

/*
 * 头部按等级给一层很淡的氛围底。
 * 刻意只到「淡」为止：这是个人页，不是领奖台，底色压过头像就本末倒置了。
 */
.hero.tier3 {
  background: linear-gradient(135deg, #f7f9fc, var(--c-surface) 70%);
}
.hero.tier4 {
  background: linear-gradient(135deg, #fff8e8, var(--c-surface) 70%);
}
.hero.tier5 {
  background: linear-gradient(135deg, #f6efe2, var(--c-surface) 70%);
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

/* 写成 .to-next.maxed 而不是 .maxed：.to-next 在它后面定义，
   同特异性下后者赢，单写 .maxed 会被灰色盖掉 */
.to-next.maxed {
  color: var(--c-brand-deep);
  font-weight: 600;
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
