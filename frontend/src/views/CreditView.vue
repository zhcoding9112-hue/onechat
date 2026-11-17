<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NavBar, Skeleton, Loading } from 'vant'
import { creditApi, type CreditTransactionView } from '@/api/credit'
import EmptyState from '@/components/EmptyState.vue'

const balance = ref(0)
const rows = ref<CreditTransactionView[]>([])
const loading = ref(true)
const failed = ref(false)
const page = ref(0)
const hasMore = ref(false)
const loadingMore = ref(false)

const PAGE_SIZE = 20

onMounted(async () => {
  try {
    const [b, first] = await Promise.all([
      creditApi.balance(),
      creditApi.transactions(0, PAGE_SIZE),
    ])
    balance.value = b
    rows.value = first.items
    hasMore.value = first.hasMore
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = await creditApi.transactions(page.value + 1, PAGE_SIZE)
    page.value += 1
    rows.value = rows.value.concat(next.items)
    hasMore.value = next.hasMore
  } finally {
    loadingMore.value = false
  }
}

/** 正负号自己带上：+30 / -12，看一眼就知道是进还是出。 */
function signed(delta: number) {
  return delta > 0 ? `+${delta}` : String(delta)
}

function dateText(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="page">
    <NavBar title="我的元子" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" :row="3" style="padding: 24px 16px" />

    <template v-else>
      <section class="balance-card">
        <p class="label">当前元子</p>
        <p class="value">{{ balance }}</p>
        <!--
          这里刻意没有任何按钮。元子目前只由运营发放，不开放充值、购买、兑换与提现，
          所以不放「立即充值」之类的入口，也不放灰掉的占位按钮——
          灰按钮同样是在暗示这个能力存在。
        -->
        <p class="note">元子由平台发放，用于活动奖励与差错补偿。</p>
      </section>

      <h3 class="sec-title">明细</h3>

      <EmptyState v-if="failed" image="network" description="加载失败" />
      <EmptyState v-else-if="rows.length === 0" description="还没有元子记录" />

      <div v-else class="list">
        <article v-for="r in rows" :key="r.id" class="row">
          <div class="main">
            <span class="reason">{{ r.reason }}</span>
            <span class="meta">{{ r.typeLabel }} · {{ dateText(r.createdAt) }}</span>
          </div>
          <div class="right">
            <span class="delta" :class="{ plus: r.delta > 0 }">{{ signed(r.delta) }}</span>
            <span class="after">余额 {{ r.balanceAfter }}</span>
          </div>
        </article>

        <button v-if="hasMore" class="more" :disabled="loadingMore" @click="loadMore">
          <Loading v-if="loadingMore" size="16" />
          <template v-else>加载更多</template>
        </button>
        <p v-else class="end">没有更多了</p>
      </div>
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

.balance-card {
  margin: var(--sp-4);
  padding: var(--sp-5);
  border-radius: var(--r-lg);
  background: var(--c-surface);
  box-shadow: var(--sd-card);
  text-align: center;
}

.label {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.value {
  margin: var(--sp-2) 0 0;
  font-size: 40px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-text);
}

.note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  line-height: 1.6;
}

.sec-title {
  margin: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

.list {
  margin: 0 var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.row:last-of-type {
  border-bottom: none;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reason {
  font-size: var(--fs-md);
  color: var(--c-text);
  word-break: break-all;
}

.meta {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.right {
  flex: none;
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.delta {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--c-text-2);
}

.delta.plus {
  color: var(--c-price);
}

.after {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.more {
  width: 100%;
  border: none;
  background: var(--c-surface);
  padding: var(--sp-3);
  font-size: var(--fs-md);
  color: var(--c-text-2);
  cursor: pointer;
}

.end {
  margin: 0;
  padding: var(--sp-3);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
</style>
