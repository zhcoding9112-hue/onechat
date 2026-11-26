<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { NavBar, Skeleton } from 'vant';
import {
  leaderboardApi,
  type BoardRow,
  type PeriodType,
} from '@/api/leaderboard';
import { centsToYuan } from '@/utils/money';
import { EntrySource, talentPath } from '@/api/analytics';
import WingFrame from '@/components/WingFrame.vue';
import Portrait from '@/components/Portrait.vue';
import GiftArt from '@/components/GiftArt.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();

const type = ref<PeriodType>('DAY');
const rows = ref<BoardRow[]>([]);
const loading = ref(true);
const failed = ref(false);
let requestId = 0;

const TABS: { key: PeriodType; label: string }[] = [
  { key: 'DAY', label: '日榜' },
  { key: 'WEEK', label: '周榜' },
  { key: 'MONTH', label: '月榜' },
];

onMounted(load);
watch(type, load);

async function load() {
  const current = ++requestId;
  loading.value = true;
  failed.value = false;
  try {
    const result = await leaderboardApi.board(type.value);
    if (current !== requestId) return;
    rows.value = result.rows;
  } catch {
    if (current !== requestId) return;
    failed.value = true;
    rows.value = [];
  } finally {
    if (current === requestId) loading.value = false;
  }
}

/** 领奖台的站位：第二名在左、第一名居中、第三名在右。 */
function podium() {
  const [first, second, third] = rows.value;
  return [second, first, third].filter(Boolean) as BoardRow[];
}
</script>
<template>
  <div class="rank-page">
    <NavBar title="排行榜" left-arrow @click-left="router.back()" />
    <div class="rank-hero">
      <span class="rank-star s1">✦</span><span class="rank-star s2">✦</span
      ><GiftArt kind="trophy" :size="58" />
      <h1>心动人气榜</h1>
      <p>每一份心意，都闪闪发光</p>
    </div>
    <nav class="rank-tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="{ on: type === t.key }"
        @click="type = t.key"
      >
        {{ t.label }}
      </button>
    </nav>
    <Skeleton v-if="loading" :row="5" /><EmptyState
      v-else-if="failed"
      description="榜单加载失败"
    /><EmptyState
      v-else-if="!rows.length"
      description="本期暂无已确认打赏"
    /><template v-else
      ><section class="podium">
        <button
          v-for="r in podium()"
          :key="r.talentId"
          class="stand"
          :class="'rank-' + r.rank"
          @click="router.push(talentPath(r.talentId, EntrySource.LEADERBOARD))"
        >
          <WingFrame
            :src="r.coverUrl"
            :seed="r.talentId"
            :name="r.nickname"
            :size="r.rank === 1 ? 74 : 58"
            :tone="r.rank === 1 ? 'gold' : r.rank === 2 ? 'pink' : 'blue'"
          /><span class="rank-name">{{ r.nickname }}</span
          ><span class="rank-amount">{{ centsToYuan(r.amountCents) }} 元</span>
          <div class="pedestal">
            <span>NO.{{ r.rank }}</span
            ><b>{{ r.rank }}</b
            ><i>✦</i>
          </div>
        </button>
      </section>
      <section class="ranking-list">
        <article v-for="r in rows.slice(3)" :key="r.talentId">
          <button
            class="person"
            @click="
              router.push(talentPath(r.talentId, EntrySource.LEADERBOARD))
            "
          >
            <span class="number">{{ r.rank }}</span
            ><span class="photo"
              ><Portrait :src="r.coverUrl" :seed="r.talentId" round
            /></span>
            <div>
              <b>{{ r.nickname }}</b
              ><small>心动值 {{ centsToYuan(r.amountCents) }}</small>
            </div></button
          ><button
            class="support"
            @click="router.push(`/talents/${r.talentId}/tip`)"
          >
            助力上榜
          </button>
        </article>
        <p v-if="rows.length <= 3" class="rank-note">
          每一份陪伴，都值得被看见
        </p>
      </section></template
    >
    <p class="rank-note">仅统计已确认的打赏，退款不计入榜单</p>
  </div>
</template>
<style scoped>
.rank-page {
  min-height: 100dvh;
  overflow: hidden;
  padding-bottom: 32px;
  background: radial-gradient(
    ellipse at 50% 25%,
    #fffef1 0,
    #fff2bd 34%,
    #ffe7b4 60%,
    #fff9e9
  );
}
.rank-hero {
  position: relative;
  text-align: center;
  padding: 5px 0 20px;
  color: #ac782f;
}
.rank-hero .gift-art {
  vertical-align: middle;
}
.rank-hero h1 {
  font-size: 27px;
  display: inline-block;
  margin: 0 0 0 6px;
  font-weight: 800;
  text-shadow: 0 2px white;
  vertical-align: middle;
}
.rank-hero p {
  font-size: 11px;
  letter-spacing: 3px;
  color: #c49b58;
  margin: 4px 0 0;
}
.rank-star {
  position: absolute;
  font-size: 27px;
  color: #edc978;
}
.s1 {
  top: 0;
  left: 9%;
  transform: rotate(-20deg);
}
.s2 {
  right: 8%;
  bottom: 9px;
  font-size: 20px;
}
.rank-tabs {
  display: flex;
  justify-content: center;
  gap: 55px;
  margin: 5px 0 40px;
}
.rank-tabs button {
  background: none;
  border: 0;
  font-size: 17px;
  color: #c6a365;
  padding: 8px 0;
  position: relative;
}
.rank-tabs button.on {
  color: #916624;
  font-weight: 700;
}
.rank-tabs button.on:after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20%;
  right: 20%;
  height: 4px;
  background: #cfa554;
  border-radius: 50%;
}
.podium {
  display: flex;
  align-items: flex-end;
  padding: 0 25px;
  gap: 0;
  min-height: 290px;
}
.stand {
  border: 0;
  background: none;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 0;
  position: relative;
}
.rank-1 {
  z-index: 2;
}
.rank-2 {
  order: -1;
}
.rank-3 {
  order: 3;
}
.rank-name {
  font-size: 13px;
  margin-top: 15px;
  max-width: 90%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #70573f;
}
.rank-amount {
  font-size: 10px;
  color: #ab8666;
  margin: 7px 0 14px;
}
.pedestal {
  width: 100%;
  background: linear-gradient(125deg, #e7d4ff, #c5a9f5);
  border-radius: 9px 9px 0 0;
  border-top: 5px solid #f9eeff;
  height: 106px;
  display: flex;
  align-items: center;
  flex-direction: column;
  box-shadow: inset -7px 0 0 #ae8fd32b;
  color: white;
}
.rank-1 .pedestal {
  height: 142px;
  background: linear-gradient(125deg, #ffd1e5, #f1a8cc);
  border-color: #fff0f7;
}
.rank-3 .pedestal {
  height: 88px;
  background: linear-gradient(125deg, #d7e7ff, #9dbeed);
  border-color: #f0f7ff;
}
.pedestal span {
  font-size: 11px;
  letter-spacing: 1px;
  color: #fff;
  padding-top: 10px;
}
.pedestal b {
  font-size: 44px;
  font-family: Georgia, serif;
  font-weight: 500;
  text-shadow: 0 2px 0 #bc8fb355;
  line-height: 1.3;
}
.pedestal i {
  font-size: 14px;
  font-style: normal;
}
.ranking-list {
  position: relative;
  z-index: 3;
  margin: 0 15px;
  border-radius: 14px;
  background: linear-gradient(#fffffff5, #fffdf5);
  padding: 9px 13px;
}
.ranking-list article {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #f6f0e6;
  gap: 8px;
}
.person {
  display: flex;
  gap: 12px;
  align-items: center;
  border: 0;
  background: none;
  flex: 1;
  text-align: left;
  padding: 0;
  min-width: 0;
}
.number {
  color: #a3a1a0;
  font-size: 16px;
  width: 16px;
}
.photo {
  width: 42px;
  height: 42px;
  flex: none;
}
.person div {
  min-width: 0;
}
.person b {
  display: block;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 150px;
}
.person small {
  display: block;
  color: #c5a06e;
  font-size: 10px;
  margin-top: 7px;
}
.support {
  border: 0;
  background: linear-gradient(120deg, #fff2cb, #ffdfa8);
  color: #b49059;
  border-radius: 16px;
  font-size: 10px;
  padding: 7px 9px;
  white-space: nowrap;
}
.rank-note {
  text-align: center;
  font-size: 10px;
  color: #c4ad87;
  margin: 20px 14px;
}
</style>
