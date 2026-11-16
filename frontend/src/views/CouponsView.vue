<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NavBar, Empty, Skeleton } from 'vant'
import { couponApi, type CouponBucket, type CouponItem } from '@/api/coupon'
import { centsToYuan } from '@/utils/money'

const items = ref<CouponItem[]>([])
const loading = ref(true)
const failed = ref(false)
const tab = ref<CouponBucket>('AVAILABLE')

const TABS: { key: CouponBucket; label: string }[] = [
  { key: 'AVAILABLE', label: '可用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' },
]

onMounted(async () => {
  try {
    items.value = await couponApi.wallet()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

function countOf(bucket: CouponBucket) {
  return items.value.filter((c) => c.bucket === bucket).length
}

const shown = computed(() => items.value.filter((c) => c.bucket === tab.value))

function dateText(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
}
</script>

<template>
  <div class="page">
    <NavBar title="我的优惠券" left-arrow @click-left="$router.back()" />

    <div class="tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        :class="{ on: tab === t.key }"
        @click="tab = t.key"
      >
        {{ t.label }}<i v-if="countOf(t.key)" class="n">{{ countOf(t.key) }}</i>
      </button>
    </div>

    <Skeleton v-if="loading" :row="3" style="padding: 16px" />
    <Empty v-else-if="failed" image="network" description="加载失败" />
    <Empty v-else-if="shown.length === 0" :description="`没有${TABS.find((t) => t.key === tab)?.label}的券`" />

    <div v-else class="list">
      <article
        v-for="c in shown"
        :key="c.userCouponId"
        class="ticket"
        :class="{ dim: c.bucket !== 'AVAILABLE' }"
      >
        <div class="left">
          <span class="amount"><i>¥</i>{{ centsToYuan(c.amountCents) }}</span>
          <span class="threshold">满 {{ centsToYuan(c.thresholdCents) }} 可用</span>
        </div>
        <div class="right">
          <span class="name">{{ c.name }}</span>
          <span class="valid">{{ dateText(c.expiresAt) }} 前有效</span>
          <span v-if="c.remark" class="remark">{{ c.remark }}</span>
        </div>
        <span class="status">{{ c.statusLabel }}</span>
      </article>
    </div>
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
  position: relative;
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

.n {
  font-style: normal;
  font-size: var(--fs-sm);
  margin-left: 2px;
  color: var(--c-text-3);
}

.list {
  margin: 0 var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.ticket {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  padding: var(--sp-4);
  overflow: hidden;
}

/* 已使用与已过期整张压暗，但保留可读性——它们仍是账目的一部分 */
.ticket.dim {
  opacity: 0.55;
}

.left {
  flex: none;
  width: 104px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 2px;
  border-right: 1px dashed var(--c-border);
}

.amount {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-price);
}

.amount i {
  font-style: normal;
  font-size: var(--fs-md);
  margin-right: 2px;
}

.threshold {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.name {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.valid,
.remark {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  word-break: break-all;
}

.status {
  position: absolute;
  top: var(--sp-3);
  right: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
</style>
