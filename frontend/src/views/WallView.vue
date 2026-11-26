<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Popup, Skeleton } from 'vant';
import { talentApi, type TalentCard } from '@/api/talent';
import { EntrySource, talentPath, trackEntry } from '@/api/analytics';
import FanTabBar from '@/components/FanTabBar.vue';
import Portrait from '@/components/Portrait.vue';
import GiftArt from '@/components/GiftArt.vue';
import GiftPreview from '@/components/GiftPreview.vue';
const router = useRouter();
const talents = ref<TalentCard[]>([]);
const loading = ref(true);
const picker = ref(false);
const preview = ref(false);
const selectedGift = ref('heart');
const giftKinds = ['heart', 'rose', 'crown', 'diamond', 'castle', 'ring'];
const giftNames = [
  '怦然心动',
  '玫瑰花束',
  '为你加冕',
  '璀璨星光',
  '梦幻城堡',
  '心动约定',
];
const cards = ref<{ talent: TalentCard; gift: number; value: number }[]>([]);
function shuffle() {
  cards.value = talents.value.slice(0, 8).map((t) => ({
    talent: t,
    gift: Math.floor(Math.random() * 6),
    value: [520, 1314, 5200, 999, 888][Math.floor(Math.random() * 5)]!,
  }));
}
onMounted(async () => {
  try {
    talents.value = (await talentApi.list({ size: 20 })).items;
    shuffle();
  } finally {
    loading.value = false;
  }
});
function detail(id: number) {
  trackEntry(EntrySource.CRUSH_WALL, id);
  router.push(talentPath(id, EntrySource.CRUSH_WALL));
}
</script>
<template>
  <div class="wall-page">
    <header>
      <span class="tiny-star">✦</span>
      <h1>心动墙</h1>
      <span class="tiny-star">✦</span>
    </header>
    <p class="wall-subtitle">把偏爱，留给心动的 TA</p>
    <div class="wall-board">
      <div class="wall-toolbar">
        <span>♡ 心动时刻</span><button @click="shuffle">换一组 ⟳</button>
      </div>
      <p class="demo-label">随机演示 · 非真实打赏记录</p>
      <Skeleton v-if="loading" :row="6" />
      <article v-for="(c, i) in cards" :key="c.talent.id" class="wall-card">
        <div class="pair">
          <div class="anonymous"><span>🐱</span><small>神秘访客</small></div>
          <div class="heartbeat">
            <span>心动值 {{ c.value }}</span
            ><svg viewBox="0 0 100 28">
              <path
                d="M0 15h25l8-10 12 20 9-15 7 5h39"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              /></svg
            ><small>♥</small>
          </div>
          <button class="wall-person" @click="detail(c.talent.id)">
            <Portrait
              :src="c.talent.coverUrl"
              :seed="c.talent.id"
              :name="c.talent.nickname"
              round
            /><small>{{ c.talent.nickname }}</small>
          </button>
        </div>
        <button
          class="gift-row"
          @click="
            selectedGift = giftKinds[c.gift]!;
            preview = true;
          "
        >
          <GiftArt :kind="giftKinds[c.gift]" :size="50" /><b>× 1</b
          ><span>{{ giftNames[c.gift] }}</span
          ><small>预览 ›</small></button
        ><i class="card-number">{{ String(i + 1).padStart(2, '0') }}</i>
      </article>
      <p v-if="!loading && !cards.length" class="empty-note">
        暂时没有可展示的店员
      </p>
    </div>
    <button class="tip-cta" @click="picker = true">
      <GiftArt kind="heart" :size="30" />我要打赏</button
    ><Popup v-model:show="picker" position="bottom" round
      ><div class="talent-picker">
        <h3>把心意送给谁</h3>
        <p class="muted">选择店员后填写打赏金额</p>
        <button
          v-for="t in talents"
          :key="t.id"
          @click="router.push(`/talents/${t.id}/tip`)"
        >
          <span><Portrait :src="t.coverUrl" :seed="t.id" round /></span
          >{{ t.nickname }}<i>›</i></button
        ><button @click="picker = false">取消</button>
      </div></Popup
    ><GiftPreview
      :show="preview"
      :kind="selectedGift"
      @close="preview = false"
    /><FanTabBar />
  </div>
</template>
<style scoped>
.wall-page {
  min-height: 100dvh;
  padding: 18px 14px calc(100px + env(safe-area-inset-bottom, 0px));
  background: radial-gradient(circle at 0 10%, #c5b0ff, transparent 55%),
    linear-gradient(135deg, #b2a1ee, #c5b7fa 55%, #e5b6ee);
}
header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  color: white;
}
h1 {
  font-size: 27px;
  margin: 10px 0 0;
  letter-spacing: 6px;
  font-weight: 650;
  text-shadow: 0 3px 0 #a89add;
}
.tiny-star {
  font-size: 21px;
}
.wall-subtitle {
  text-align: center;
  color: #fff;
  font-size: 12px;
  letter-spacing: 2px;
  margin: 10px 0 20px;
}
.wall-board {
  border: 4px solid #fff;
  border-radius: 22px;
  background: linear-gradient(140deg, #e1ffef, #fff4de 44%, #ffe3ee);
  padding: 12px 12px 20px;
  box-shadow: 0 5px 0 #9a84d833;
}
.wall-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #c187be;
  font-size: 16px;
}
.wall-toolbar button {
  border: 0;
  background: #fff9;
  color: #b592bd;
  font-size: 11px;
  padding: 6px 9px;
  border-radius: 15px;
}
.demo-label {
  font-size: 10px;
  color: #b4a5ba;
  margin: 7px 0 14px;
}
.wall-card {
  position: relative;
  background: #fff;
  border-radius: 14px;
  padding: 19px 16px 10px;
  margin: 0 0 13px;
  box-shadow: 0 3px 8px #e5cfd32a;
}
.pair {
  display: flex;
  justify-content: space-around;
  align-items: center;
}
.anonymous,
.wall-person {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 7px;
  width: 66px;
}
.anonymous > span {
  width: 54px;
  height: 54px;
  background: #ffedda;
  border: 2px solid #ffe8c3;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 32px;
}
.pair small {
  font-size: 10px;
  color: #777;
  overflow: hidden;
  max-width: 75px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wall-person {
  background: none;
  border: 0;
  padding: 0;
}
.wall-person > .portrait-art {
  width: 54px;
  height: 54px;
}
.heartbeat {
  color: #f195ac;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  padding: 0 10px;
}
.heartbeat > span {
  font-size: 10px;
  white-space: nowrap;
}
.heartbeat svg {
  width: 100%;
  height: 28px;
  animation: pulse 2s ease-in-out infinite;
}
.heartbeat > small {
  color: #fd9fb6;
  font-size: 14px;
}
.gift-row {
  width: 100%;
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  margin-top: 6px;
  color: #af8ca7;
}
.gift-row b {
  font-size: 13px;
  font-weight: 500;
}
.gift-row > span:not(.gift-art) {
  font-size: 11px;
  margin-left: 10px;
}
.gift-row > small {
  font-size: 10px;
  color: #bbb;
  margin-left: auto;
}
.card-number {
  position: absolute;
  left: 7px;
  top: 7px;
  font-size: 9px;
  color: #d7c9e0;
  font-style: normal;
}
.tip-cta {
  position: fixed;
  bottom: 77px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 28px;
  border: 2px solid white;
  border-radius: 25px;
  color: #fff;
  background: linear-gradient(120deg, #f89ec5, #ef79b1);
  font-size: 15px;
  box-shadow: 0 5px 15px #e577a955;
}
.talent-picker {
  padding: 18px;
  max-height: 70dvh;
  overflow: auto;
  text-align: center;
}
.talent-picker h3 {
  margin: 0;
}
.talent-picker button {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 0;
  background: white;
  border-bottom: 1px solid #f2f2f2;
  padding: 12px;
  text-align: left;
}
.talent-picker button > span {
  width: 40px;
  height: 40px;
}
.talent-picker i {
  margin-left: auto;
  color: #aaa;
}
.empty-note {
  text-align: center;
  color: #b497b3;
  font-size: 12px;
}
@keyframes pulse {
  50% {
    opacity: 0.5;
  }
}
</style>
