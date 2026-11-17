<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Skeleton } from 'vant'
import { leaderboardApi, type BoardRow, type PeriodType } from '@/api/leaderboard'
import { centsToYuan } from '@/utils/money'
import { EntrySource, talentPath } from '@/api/analytics'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const type = ref<PeriodType>('DAY')
const rows = ref<BoardRow[]>([])
const loading = ref(true)
const failed = ref(false)

const TABS: { key: PeriodType; label: string }[] = [
  { key: 'DAY', label: '日榜' },
  { key: 'WEEK', label: '周榜' },
  { key: 'MONTH', label: '月榜' },
]

onMounted(load)
watch(type, load)

async function load() {
  loading.value = true
  failed.value = false
  try {
    rows.value = (await leaderboardApi.board(type.value)).rows
  } catch {
    failed.value = true
    rows.value = []
  } finally {
    loading.value = false
  }
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}

/** 领奖台的站位：第二名在左、第一名居中、第三名在右。 */
function podium() {
  const [first, second, third] = rows.value
  return [second, first, third].filter(Boolean) as BoardRow[]
}
</script>

<template>
  <div class="page">
    <NavBar title="打赏榜" left-arrow @click-left="$router.back()" />

    <div class="tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="{ on: type === t.key }"
        @click="type = t.key"
      >
        {{ t.label }}
      </button>
    </div>

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />
    <EmptyState v-else-if="failed" image="network" description="加载失败" />
    <EmptyState v-else-if="rows.length === 0" description="这个周期还没有打赏" />

    <template v-else>
      <!-- 前三名与其余名次同处一张卡：分成两块白底会在中间裂出一道缝 -->
      <section class="board">
        <div class="podium">
          <div
            v-for="r in podium()"
            :key="r.talentId"
            class="stand"
            :class="`r${r.rank}`"
            @click="router.push(talentPath(r.talentId, EntrySource.LEADERBOARD))"
          >
            <div class="avatar">
              <img v-if="r.coverUrl" :src="r.coverUrl" alt="" />
              <span v-else class="fallback">{{ initial(r.nickname) }}</span>
              <span class="crown">{{ r.rank }}</span>
            </div>
            <span class="name">{{ r.nickname }}</span>
            <span class="amount">¥{{ centsToYuan(r.amountCents) }}</span>
          </div>
        </div>

        <div v-if="rows.length > 3" class="list">
          <article
            v-for="r in rows.slice(3)"
            :key="r.talentId"
            class="row"
            @click="router.push(talentPath(r.talentId, EntrySource.LEADERBOARD))"
          >
            <span class="rank">{{ r.rank }}</span>
            <div class="avatar sm">
              <img v-if="r.coverUrl" :src="r.coverUrl" alt="" />
              <span v-else class="fallback">{{ initial(r.nickname) }}</span>
            </div>
            <span class="row-name">{{ r.nickname }}</span>
            <span class="row-amount">¥{{ centsToYuan(r.amountCents) }}</span>
          </article>
        </div>
      </section>

      <!--
        榜单只呈现主播。打赏人的昵称、头像与任何标识都不在这里出现，
        接口返回体里也没有对应字段。
      -->
      <p class="note">按已确认收款的打赏金额统计，退款的打赏不计入。</p>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: var(--sp-8);
}

.tabs {
  display: flex;
  gap: var(--sp-5);
  padding: var(--sp-2) var(--sp-4) var(--sp-4);
}

.tabs button {
  border: none;
  background: none;
  padding: var(--sp-2) 0;
  font-size: var(--fs-lg);
  color: var(--c-text-2);
  cursor: pointer;
}

.tabs button.on {
  color: var(--c-text);
  font-weight: 700;
  font-size: var(--fs-xl);
}

/* ---------- 领奖台 ---------- */
.board {
  margin: 0 var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  overflow: hidden;
}

.podium {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: var(--sp-2);
  padding: var(--sp-5) var(--sp-3) var(--sp-4);
}

/*
  名次差靠「头像大小 + 整列下沉」表达，不画假台阶。
  之前用白色方块当台阶，在白底卡片上既看不出是台阶，又会和下方列表撞在一起。
*/
.stand {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.stand.r2,
.stand.r3 {
  padding-bottom: var(--sp-2);
}

.avatar {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: visible;
  background: var(--g-avatar);
  display: flex;
  align-items: center;
  justify-content: center;
}

.r1 .avatar {
  width: 72px;
  height: 72px;
}

.avatar.sm {
  width: 40px;
  height: 40px;
  flex: none;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.crown {
  position: absolute;
  top: -6px;
  left: 50%;
  transform: translateX(-50%);
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  line-height: 20px;
  border-radius: var(--r-pill);
  font-size: var(--fs-sm);
  font-weight: 700;
  color: #fff;
  text-align: center;
  background: var(--c-text-3);
  box-shadow: 0 0 0 2px var(--c-surface);
}

.r1 .crown {
  background: #d9a441;
}

.r2 .crown {
  background: #a9b4bd;
}

.r3 .crown {
  background: #c08457;
}

.fallback {
  font-size: var(--fs-lg);
  font-weight: 300;
  color: #fff;
}

.name {
  margin-top: 2px;
  font-size: var(--fs-md);
  font-weight: 600;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.r1 .name {
  font-size: var(--fs-lg);
}

.amount {
  font-size: var(--fs-sm);
  color: var(--c-price);
  font-weight: 600;
}

/* ---------- 第四名起 ---------- */
.list {
  border-top: 1px solid var(--c-border);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
}

.row:last-child {
  border-bottom: none;
}

.rank {
  flex: none;
  width: 24px;
  text-align: center;
  font-size: var(--fs-md);
  color: var(--c-text-3);
}

.row-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-lg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-amount {
  flex: none;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--c-price);
}

.note {
  margin: var(--sp-4) var(--sp-4) 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  text-align: center;
  line-height: 1.6;
}
</style>
