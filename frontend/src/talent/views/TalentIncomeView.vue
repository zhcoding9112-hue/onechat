<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NavBar, Skeleton, Cell, CellGroup, NoticeBar } from 'vant'
import { workspaceApi, type IncomeView } from '@/talent/api/workspace'
import { formatPrice } from '@/utils/money'
import EmptyState from '@/components/EmptyState.vue'

const income = ref<IncomeView | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    income.value = await workspaceApi.income()
  } finally {
    loading.value = false
  }
})

const months = computed(() => Object.entries(income.value?.monthlyCents ?? {}))

function formatTime(value: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <NavBar title="我的收入" />

  <Skeleton v-if="loading" :row="5" style="padding: 16px" />

  <template v-else-if="income">
    <section class="total">
      <p class="label">累计应得</p>
      <p class="value">{{ formatPrice(income.totalCents) }}</p>
      <p class="sub">已完成 {{ income.completedOrderCount }} 单</p>
    </section>

    <NoticeBar
      text="分成仅为记账，实际结算按公会既有的线下月结流程进行。"
      color="#969799"
      background="#f7f8fa"
      left-icon="info-o"
    />

    <CellGroup v-if="months.length" inset title="按月汇总">
      <Cell v-for="[month, cents] in months" :key="month" :title="month">
        <template #value>
          <span class="amount">{{ formatPrice(cents) }}</span>
        </template>
      </Cell>
    </CellGroup>

    <CellGroup v-if="income.entries.length" inset title="收入明细">
      <Cell
        v-for="e in income.entries"
        :key="e.orderId"
        :title="`${e.serviceItemName} · ${e.serviceSpecName}`"
        :label="`${formatTime(e.completedAt)} · 单号 ${e.orderRef}`"
      >
        <template #value>
          <span class="amount">{{ formatPrice(e.talentCents) }}</span>
        </template>
      </Cell>
    </CellGroup>

    <EmptyState v-if="income.entries.length === 0" description="还没有已完成的订单" />
  </template>
</template>

<style scoped>
.total {
  padding: 24px 16px 16px;
  background: #fff;
  text-align: center;
}

.label {
  margin: 0;
  font-size: 13px;
  color: #969799;
}

.value {
  margin: 6px 0 0;
  font-size: 30px;
  font-weight: 600;
  color: #323233;
}

.sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: #969799;
}

.amount {
  color: #323233;
  font-weight: 600;
}
</style>
