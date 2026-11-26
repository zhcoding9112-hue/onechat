<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Skeleton, Popup, Icon } from 'vant';
import { fetchCurrentUser, type CurrentUser } from '@/api/session';
import { fanLevelApi, type FanLevel } from '@/api/fanLevel';
import LevelBadge from '@/components/LevelBadge.vue';
import Portrait from '@/components/Portrait.vue';
import WingFrame from '@/components/WingFrame.vue';
import GiftArt from '@/components/GiftArt.vue';
import GiftPreview from '@/components/GiftPreview.vue';
import { creditApi } from '@/api/credit';
import { centsToYuan } from '@/utils/money';
import FanTabBar from '@/components/FanTabBar.vue';

const router = useRouter();
const SupportDialog = defineAsyncComponent(
  () => import('@/components/SupportDialog.vue'),
);
const supportOpen = ref(false);
const me = ref<CurrentUser | null>(null);
const level = ref<FanLevel | null>(null);
const balance = ref<number | null>(null);
const sheet = ref('');
const preview = ref(false);
const giftKind = ref('heart');
const giftKinds = [
  'heart',
  'rose',
  'diamond',
  'crown',
  'castle',
  'ring',
  'rocket',
  'candy',
];
const giftNames = [
  '怦然心动',
  '玫瑰花束',
  '璀璨星光',
  '为你加冕',
  '梦幻城堡',
  '心动约定',
  '星际旅行',
  '甜蜜糖果',
];
const menu = [
  { icon: 'gift-o', name: '我要打赏', path: '/wall' },
  { icon: 'like-o', name: '我的收藏', path: '/me/favorites' },
  { icon: 'coupon-o', name: '优惠券', path: '/me/coupons' },
  { icon: 'gem-o', name: '我的等级', sheet: 'level' },
  { icon: 'medal-o', name: '人气榜单', path: '/leaderboard' },
  { icon: 'gift-o', name: '礼物图鉴', sheet: 'gifts' },
  { icon: 'music-o', name: '试音选人', path: '/audition' },
  { icon: 'service-o', name: '联系客服', sheet: 'support' },
];
function openMenu(item: (typeof menu)[number]) {
  if (item.path) router.push(item.path);
  else if (item.sheet === 'support') supportOpen.value = true;
  else sheet.value = item.sheet ?? '';
}

/**
 * 当前等级内的进度。
 *
 * 接口只给「已花多少」和「还差多少」，本级起点要自己倒推：
 * 本级跨度 = 已花 + 还差 − 本级门槛，而本级门槛接口没给，
 * 所以退一步用「已花 /（已花 + 还差）」——它表达的是「离下一级还有多远」，
 * 对粉丝来说正是想看的那个意思，也不会出现跨级后进度倒退。
 */
const progress = computed(() => {
  const l = level.value;
  if (!l || l.toNextCents === null) return 100;
  const total = l.spentCents + l.toNextCents;
  if (total <= 0) return 0;
  return Math.min(100, Math.max(2, Math.round((l.spentCents / total) * 100)));
});
const loading = ref(true);

onMounted(async () => {
  void creditApi
    .balance()
    .then((v) => (balance.value = v))
    .catch(() => {});
  try {
    me.value = await fetchCurrentUser();
  } finally {
    loading.value = false;
  }
  // 等级加载失败不该挡住整页，单独 catch 掉
  try {
    level.value = await fanLevelApi.mine();
  } catch {
    level.value = null;
  }
});
</script>
<template>
  <div class="profile-page">
    <h1 class="page-title">个人中心</h1>
    <Skeleton v-if="loading" avatar :row="4" /><template v-else-if="me"
      ><section class="profile-person">
        <WingFrame
          v-if="(level?.level ?? 0) >= 4"
          :src="me.avatarUrl"
          :seed="me.userId"
          :size="58"
        />
        <div v-else class="profile-avatar">
          <Portrait :src="me.avatarUrl" :seed="me.userId" round />
        </div>
        <div class="profile-name">
          <h2>{{ me.nickname }}</h2>
          <LevelBadge v-if="level" :level="level.level" :name="level.name" />
        </div>
        <button class="profile-info" @click="sheet = 'info'">个人资料 ›</button>
      </section>
      <section class="wallet-card">
        <div class="wallet-label">我的元子 <span>✧</span></div>
        <div class="wallet-value">
          <b>{{ balance ?? '—' }}</b
          ><button @click="router.push('/me/credits')">查看明细 ›</button>
        </div>
        <p>记录每一份温暖的陪伴</p>
        <span class="wallet-orb o1" /><span class="wallet-orb o2" />
      </section>
      <section class="orders-card">
        <header>
          <h3>我的订单</h3>
          <button @click="router.push('/orders')">全部订单 ›</button>
        </header>
        <div class="order-links">
          <button @click="router.push('/orders?status=PENDING_PAYMENT')">
            <Icon name="paid" size="28" />待付款</button
          ><button @click="router.push('/orders?status=PENDING_ACCEPT')">
            <Icon name="clock-o" size="28" />待接单</button
          ><button @click="router.push('/orders?status=ACCEPTED')">
            <Icon name="service-o" size="28" />服务中</button
          ><button @click="router.push('/orders?status=COMPLETED')">
            <Icon name="completed" size="28" />已完成
          </button>
        </div>
      </section>
      <section class="menu-card">
        <h3>常用功能</h3>
        <nav class="menu-grid">
          <button
            v-for="(item, i) in menu"
            :key="item.name"
            @click="openMenu(item)"
          >
            <Icon :name="item.icon" :class="'icon-' + i" size="28" />{{
              item.name
            }}
          </button>
        </nav>
      </section>
      <button v-if="me.talent" class="role-row" @click="router.push('/talent')">
        主播工作台 <span>›</span></button
      ><button
        v-if="me.operator"
        class="role-row"
        @click="router.push('/admin')"
      >
        运营后台 <span>›</span>
      </button>
      <div class="profile-footer">♡ 晚星奶糖 · 陪伴每一份心动</div></template
    ><Popup
      :show="!!sheet"
      position="bottom"
      round
      closeable
      @update:show="
        (v) => {
          if (!v) sheet = '';
        }
      "
      ><section class="profile-sheet">
        <template v-if="sheet === 'info'"
          ><h3>个人资料</h3>
          <div class="info-row"><span>昵称</span>{{ me?.nickname }}</div>
          <div class="info-row"><span>用户 ID</span>{{ me?.userId }}</div>
          <div class="info-row">
            <span>当前等级</span>{{ level?.name ?? '—' }}
          </div>
          <p class="muted">资料来自微信授权，暂无站内修改入口。</p></template
        ><template v-else-if="sheet === 'level' && level"
          ><h3>我的等级</h3>
          <LevelBadge :level="level.level" :name="level.name" size="lg" />
          <div class="spent">
            累计消费 <b>¥{{ centsToYuan(level.spentCents) }}</b>
          </div>
          <div class="level-progress">
            <i :style="{ width: progress + '%' }" />
          </div>
          <p class="muted">
            {{
              level.toNextCents === null
                ? '已达到最高等级'
                : `再消费 ¥${centsToYuan(level.toNextCents)} 可升级至 ${level.nextName}`
            }}
          </p>
          <p class="muted">
            等级只影响展示，服务价格以店员档位为准。
          </p></template
        ><template v-else-if="sheet === 'gifts'"
          ><h3>礼物图鉴</h3>
          <p class="muted">点击查看礼物动效</p>
          <div class="gift-gallery">
            <button
              v-for="(g, i) in giftKinds"
              :key="g"
              @click="
                giftKind = g;
                preview = true;
              "
            >
              <GiftArt :kind="g" :size="60" /><span>{{ giftNames[i] }}</span>
            </button>
          </div></template
        >
        <p v-else class="muted">暂未获取等级资料，请稍后重试。</p>
      </section></Popup
    ><GiftPreview :show="preview" :kind="giftKind" @close="preview = false" />
    <SupportDialog v-if="supportOpen" v-model:show="supportOpen" />
    <FanTabBar />
  </div>
</template>
<style scoped>
.profile-page {
  min-height: 100dvh;
  background: radial-gradient(ellipse at 0 0, #fff9da, transparent 55%),
    linear-gradient(#d8faf1, #f5f6f7 400px);
  padding: 0 15px
    calc(var(--fan-tabbar-height) + 25px + env(safe-area-inset-bottom, 0px));
}
.profile-person {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 10px 4px 25px;
}
.profile-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 2px solid white;
  flex: none;
}
.profile-name {
  flex: 1;
  min-width: 0;
}
.profile-name h2 {
  font-size: 19px;
  font-weight: 600;
  margin: 0 0 9px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-info {
  border: 0;
  background: none;
  color: #96a7a3;
  font-size: 10px;
  white-space: nowrap;
  padding: 0;
}
.wallet-card {
  position: relative;
  background: linear-gradient(115deg, #3bd6ca, #28c7c4 55%, #4ed8d5);
  color: white;
  border-radius: 17px;
  padding: 20px 22px 45px;
  overflow: hidden;
}
.wallet-label {
  font-size: 12px;
  position: relative;
  z-index: 1;
  letter-spacing: 1px;
}
.wallet-label span {
  margin-left: 8px;
}
.wallet-value {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}
.wallet-value b {
  font-size: 32px;
  font-weight: 600;
}
.wallet-value button {
  background: #fff;
  border: 0;
  color: #24c5b9;
  border-radius: 20px;
  padding: 8px 16px;
  font-size: 11px;
}
.wallet-card p {
  font-size: 9px;
  color: #e8ffff;
  margin: 10px 0 0;
}
.wallet-orb {
  position: absolute;
  border: 20px solid #ffffff12;
  border-radius: 50%;
  width: 155px;
  height: 155px;
  right: -20px;
  top: -65px;
}
.wallet-orb.o2 {
  width: 220px;
  height: 220px;
  right: -50px;
  top: -90px;
  border: 1px solid #ffffff16;
}
.orders-card {
  position: relative;
  background: #fff;
  border-radius: 15px;
  padding: 18px 15px;
  margin: -24px 0 14px;
  z-index: 1;
  box-shadow: 0 5px 20px #adb4bb0b;
}
.orders-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.orders-card h3,
.menu-card h3 {
  font-size: 14px;
  font-weight: 500;
  margin: 0;
}
.orders-card header button {
  background: none;
  border: 0;
  color: #aaa;
  font-size: 10px;
}
.order-links {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 23px;
  gap: 10px;
}
.order-links button {
  border: 0;
  background: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 11px;
  font-size: 11px;
  color: #888;
}
.order-links span {
  font-size: 27px;
  line-height: 30px;
  font-weight: 300;
  color: #777;
}
.menu-card {
  background: #fff;
  border-radius: 15px;
  padding: 18px 15px;
}
.menu-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 25px 10px;
  padding: 22px 0 8px;
}
.menu-grid button {
  background: none;
  border: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 12px;
  font-size: 10px;
  color: #888;
  white-space: nowrap;
  padding: 0;
}
.menu-grid button > span {
  font-size: 29px;
  line-height: 30px;
  color: #81868a;
  font-weight: 300;
}
.role-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 17px;
  margin-top: 12px;
  border: 0;
  border-radius: 12px;
  background: white;
  font-size: 13px;
  color: #888;
}
.role-row span {
  color: #bbb;
}
.profile-footer {
  text-align: center;
  color: #bdc9c7;
  font-size: 10px;
  letter-spacing: 1px;
  padding: 25px 0;
}
.profile-sheet {
  padding: 23px 22px 30px;
  max-height: 78dvh;
  overflow: auto;
  text-align: center;
}
.profile-sheet h3 {
  font-size: 17px;
  margin: 0 0 22px;
  font-weight: 500;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 17px 0;
  border-bottom: 1px solid #f3f3f3;
  font-size: 13px;
}
.info-row span {
  color: #999;
}
.profile-sheet > .muted {
  line-height: 1.8;
  margin: 18px 0 0;
}
.spent {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #888;
  margin-top: 30px;
}
.spent b {
  color: #eab0a0;
  font-size: 19px;
}
.level-progress {
  height: 6px;
  border-radius: 6px;
  background: #f0f2f2;
  margin: 18px 0 0;
  overflow: hidden;
}
.level-progress i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #3dcbbb, #a4e4d6);
  border-radius: 6px;
}
.gift-gallery {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px 8px;
  margin-top: 20px;
}
.gift-gallery button {
  background: none;
  border: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 0;
  font-size: 10px;
  color: #aaa;
}
</style>
