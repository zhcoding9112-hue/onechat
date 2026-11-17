<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Skeleton, Cell, CellGroup, Tag, Button, NoticeBar } from 'vant'
import { http } from '@/api/http'
import type { TipView } from '@/api/tip'
import { formatPrice } from '@/utils/money'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const tip = ref<TipView | null>(null)
const loading = ref(true)

const tipId = computed(() => Number(route.params.id))

onMounted(async () => {
  try {
    tip.value = await http.get<TipView>(`/api/tips/${tipId.value}`)
  } finally {
    loading.value = false
  }
})

const tagType = computed(() => {
  switch (tip.value?.status) {
    case 'COMPLETED':
      return 'success'
    case 'PENDING_PAYMENT':
    case 'PENDING_CONFIRM':
      return 'warning'
    default:
      return 'default'
  }
})

function formatTime(value: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <NavBar title="打赏详情" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" :row="5" style="padding: 16px" />
  <EmptyState v-else-if="!tip" description="打赏记录不存在" />

  <template v-else>
    <section class="amount">
      <p class="label">打赏金额</p>
      <p class="value">{{ formatPrice(tip.amountCents) }}</p>
      <Tag :type="tagType" size="medium">{{ tip.statusLabel }}</Tag>
    </section>

    <NoticeBar
      v-if="tip.status === 'PENDING_PAYMENT'"
      text="请完成付款，付款方式与服务订单一致"
      color="#ed6a0c"
      background="#fffbe8"
      left-icon="info-o"
    />

    <CellGroup inset>
      <Cell title="主播" :value="tip.talentNickname ?? '—'" />
      <Cell v-if="tip.message" title="留言" :value="tip.message" />
      <Cell title="单号" :value="tip.tipRef" />
      <Cell title="创建时间" :value="formatTime(tip.createdAt)" />
      <Cell v-if="tip.paidAt" title="收款时间" :value="formatTime(tip.paidAt)" />
    </CellGroup>

    <div class="actions">
      <Button block round @click="router.push(`/talents/${tip.talentId}`)">返回主播</Button>
    </div>
  </template>
</template>

<style scoped>
.amount {
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
  margin: 6px 0 10px;
  font-size: 28px;
  font-weight: 600;
  color: #ee0a24;
}

.actions {
  padding: 16px;
}
</style>
