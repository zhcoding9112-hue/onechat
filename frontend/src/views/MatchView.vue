<script setup lang="ts">
import { onMounted, ref, watch, defineAsyncComponent, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { Popup } from 'vant';
import Portrait from '@/components/Portrait.vue';
import WingFrame from '@/components/WingFrame.vue';
import GiftArt from '@/components/GiftArt.vue';
import FanTabBar from '@/components/FanTabBar.vue';
import AudioPreview from '@/components/AudioPreview.vue';
import { matchApi, type MatchedTalent } from '@/api/match';
import { homeApi, type CategoryView } from '@/api/home';
import type { Gender } from '@/api/talent';
import { EntrySource, talentPath } from '@/api/analytics';

const router = useRouter();
const OrderCreate = defineAsyncComponent(() => import('./OrderCreateView.vue'));
const level = ref<number | null>(null);
const checkout = ref(false);
const resultPanel = ref<HTMLElement | null>(null);
let matchRequest = 0;

const categories = ref<CategoryView[]>([]);
const category = ref<string | null>(null);
const gender = ref<Gender | null>(null);
const onlineOnly = ref(false);
const excludeOrdered = ref(false);

const result = ref<MatchedTalent | null>(null);
const emptyMessage = ref<string | null>(null);
const matching = ref(false);
/** 已经看过的，用于「换一位」时避开。 */
const seen = ref<number[]>([]);

onMounted(async () => {
  try {
    categories.value = await homeApi.categories();
  } catch {
    categories.value = [];
  }
});

async function run(fresh: boolean) {
  if (matching.value) return;
  const request = ++matchRequest;
  matching.value = true;
  if (fresh) {
    seen.value = [];
  }
  try {
    const res = await matchApi.random({
      category: category.value,
      level: level.value,
      gender: gender.value,
      onlineOnly: onlineOnly.value,
      excludeOrdered: excludeOrdered.value,
      exclude: seen.value,
    });
    if (request !== matchRequest) return;
    if (res.matched && res.talent) {
      result.value = res.talent;
      await nextTick();
      resultPanel.value?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
      emptyMessage.value = null;
      if (!seen.value.includes(res.talent.id)) {
        seen.value = seen.value.concat(res.talent.id);
      }
    } else {
      result.value = null;
      emptyMessage.value = res.message ?? '暂时没有符合条件的主播';
    }
  } finally {
    if (request === matchRequest) matching.value = false;
  }
}

watch([gender, category, level, onlineOnly, excludeOrdered], () => {
  matchRequest++;
  matching.value = false;
  result.value = null;
  emptyMessage.value = null;
  seen.value = [];
});

function toggleGender(g: Gender) {
  gender.value = gender.value === g ? null : g;
}

function openDetail() {
  if (!result.value) return;
  router.push(talentPath(result.value.id, EntrySource.RANDOM_MATCH));
}
</script>
<template>
  <div class="match-page">
    <h1 class="page-title">随机盲盒</h1>
    <div class="match-intro">
      <GiftArt kind="box" :size="60" />
      <div>
        <b>下一位，会是心动吗</b>
        <p>选好你的偏爱，让相遇充满惊喜</p>
      </div>
    </div>
    <section class="match-card">
      <h2>喜欢的类型</h2>
      <div class="gender-cards">
        <button
          :class="{ selected: gender === 'FEMALE' }"
          @click="toggleGender('FEMALE')"
        >
          <span><Portrait :seed="0" /></span><b>甜甜女生</b
          ><small>♀</small></button
        ><button
          :class="{ selected: gender === 'MALE' }"
          @click="toggleGender('MALE')"
        >
          <span><Portrait :seed="2" /></span><b>心动男生</b><small>♂</small>
        </button>
      </div>
      <p class="subhint">再次点击可恢复不限性别</p>
    </section>
    <section class="match-card">
      <h2>店员等级 <small>自由选择你的偏好</small></h2>
      <div class="level-grid">
        <button
          class="choice-chip"
          :class="{ selected: level === null }"
          @click="level = null"
        >
          不限</button
        ><button
          v-for="n in 6"
          :key="n"
          class="choice-chip"
          :class="{ selected: level === n }"
          @click="level = level === n ? null : n"
        >
          <span>♛</span> LV.{{ n }}
        </button>
      </div>
    </section>
    <section class="match-card">
      <h2>服务类型</h2>
      <div class="chips-row">
        <button
          class="choice-chip"
          :class="{ selected: category === null }"
          @click="category = null"
        >
          不限</button
        ><button
          v-for="c in categories"
          :key="c.code"
          class="choice-chip"
          :class="{ selected: category === c.code }"
          @click="category = category === c.code ? null : c.code"
        >
          {{ c.name }}
        </button>
      </div>
      <div class="match-switches">
        <label><input v-model="onlineOnly" type="checkbox" /> 只匹配在线</label
        ><label
          ><input v-model="excludeOrdered" type="checkbox" />
          排除下过单的店员</label
        >
      </div>
    </section>
    <section v-if="result" ref="resultPanel" class="match-result">
      <div class="result-title">✦ 遇见你的心动 ✦</div>
      <button class="matched-person" @click="openDetail">
        <WingFrame
          :src="result.coverUrl"
          :seed="result.id"
          :size="80"
          tone="pink"
        />
        <h3>{{ result.nickname }}</h3>
        <p>{{ result.intro || '点击查看 TA 的资料与服务' }}</p></button
      ><AudioPreview
        v-if="result.audioUrl"
        :url="result.audioUrl"
        :seconds="result.audioSeconds"
      />
      <div class="result-actions">
        <button class="choice-chip" :disabled="matching" @click="run(false)">
          换一位</button
        ><button class="primary-btn" @click="checkout = true">
          就 TA 了，选服务
        </button>
      </div>
    </section>
    <p v-if="emptyMessage" class="empty-match">{{ emptyMessage }}</p>
    <div class="match-bottom">
      <div><b>免费匹配</b><small>选定店员后确认时长、数量与价格</small></div>
      <button class="primary-btn" :disabled="matching" @click="run(true)">
        {{ matching ? '正在寻找…' : '开始匹配' }}
      </button>
    </div>
    <Popup v-model:show="checkout" position="bottom" round
      ><OrderCreate
        v-if="checkout && result"
        embedded
        :talent-id="result.id"
        :entry-source="EntrySource.RANDOM_MATCH"
        @close="checkout = false" /></Popup
    ><FanTabBar />
  </div>
</template>
<style scoped>
.match-page {
  min-height: 100dvh;
  padding: 0 15px calc(160px + env(safe-area-inset-bottom, 0px));
  background: radial-gradient(ellipse at 0 0, #fff7d7, transparent 60%),
    linear-gradient(#dafaf5, #f5f7f8 480px);
}
.match-intro {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 3px 15px 12px;
  color: #497c7e;
}
.match-intro b {
  font-size: 19px;
  font-weight: 600;
}
.match-intro p {
  font-size: 11px;
  color: #8eadae;
  margin: 8px 0;
}
.match-card {
  background: white;
  border-radius: 14px;
  padding: 17px;
  margin-top: 12px;
}
.match-card h2 {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 15px;
  color: #555;
}
.match-card h2 small {
  font-size: 10px;
  font-weight: 400;
  color: #aaa;
  margin-left: 12px;
}
.gender-cards {
  display: flex;
  gap: 16px;
}
.gender-cards button {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1.5px solid #f3e6ef;
  background: linear-gradient(115deg, #fff4f5, #ffeff8);
  border-radius: 9px;
  padding: 10px;
  color: #b584a0;
}
.gender-cards button + button {
  background: linear-gradient(115deg, #eef9ff, #f3f5ff);
  border-color: #e9edf8;
  color: #86a6c8;
}
.gender-cards .selected {
  border-color: #11c6b8;
  background: #f0fffa;
}
.gender-cards button > span {
  width: 42px;
  height: 50px;
  border-radius: 8px;
  overflow: hidden;
}
.gender-cards b {
  font-size: 12px;
  font-weight: 500;
}
.gender-cards small {
  position: absolute;
  bottom: 3px;
  right: 5px;
  font-size: 22px;
  opacity: 0.3;
}
.subhint {
  font-size: 9px;
  color: #b3b8bd;
  text-align: center;
  margin: 12px 0 0;
}
.level-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.choice-chip {
  font-size: 12px;
  padding: 8px 9px;
}
.level-grid span {
  color: #cbb0e6;
}
.match-switches {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #999;
  border-top: 1px solid #f5f5f5;
  margin-top: 20px;
  padding-top: 15px;
}
.match-switches input {
  accent-color: #0dbfb5;
  vertical-align: middle;
}
.match-bottom {
  position: fixed;
  bottom: 55px;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, var(--app-max-width));
  background: #fff;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 17px calc(13px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 9px #babfc015;
  gap: 10px;
}
.match-bottom b {
  display: block;
  color: #ed8ba6;
  font-size: 18px;
  font-weight: 500;
}
.match-bottom small {
  display: block;
  color: #aaa;
  font-size: 9px;
  margin-top: 6px;
}
.match-bottom button {
  font-size: 14px;
  white-space: nowrap;
}
.match-result {
  margin: 18px 0 0;
  padding: 20px;
  background: linear-gradient(#fff3fa, white);
  border: 1px solid #fff;
  border-radius: 16px;
  text-align: center;
}
.result-title {
  font-size: 13px;
  color: #c68cab;
  margin-bottom: 30px;
}
.matched-person {
  background: none;
  border: 0;
  color: #555;
}
.matched-person h3 {
  font-size: 17px;
  margin-top: 20px;
}
.matched-person p {
  font-size: 12px;
  color: #aaa;
  line-height: 1.8;
}
.result-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
.result-actions button {
  flex: 1;
  font-size: 13px;
}
.empty-match {
  font-size: 12px;
  color: #a8a0a0;
  text-align: center;
  padding: 20px 0;
}
</style>
