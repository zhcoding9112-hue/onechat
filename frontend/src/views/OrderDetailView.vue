<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Empty,
  Skeleton,
  Tag,
  Cell,
  CellGroup,
  Button,
  NoticeBar,
  Steps,
  Step,
} from 'vant'
import {
  orderApi,
  statusHint,
  statusTagType,
  OrderStatus,
  type HistoryEntry,
  type OrderView,
} from '@/api/order'
import { formatPrice } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const order = ref<OrderView | null>(null)
const history = ref<HistoryEntry[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    order.value = await orderApi.detail(orderId.value)
    history.value = await orderApi.history(orderId.value)
  } finally {
    loading.value = false
  }
})

/** 正常流程的四步进度条；终止态不展示进度，改用提示条说明。 */
const stepIndex = computed(() => {
  switch (order.value?.status) {
    case OrderStatus.PENDING_PAYMENT:
    case OrderStatus.PENDING_CONFIRM:
      return 0
    case OrderStatus.PENDING_ACCEPT:
      return 1
    case OrderStatus.ACCEPTED:
      return 2
    case OrderStatus.COMPLETED:
      return 3
    default:
      return -1
  }
})

const isTerminalAbnormal = computed(() =>
  order.value !== null &&
  (
    order.value.status === OrderStatus.TIMEOUT ||
    order.value.status === OrderStatus.CANCELLED ||
    order.value.status === OrderStatus.REFUNDED
  ),
)

function formatTime(value: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <NavBar title="订单详情" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" :row="6" style="padding: 16px" />
  <Empty v-else-if="!order" description="订单不存在" />

  <template v-else>
    <section class="status">
      <Tag :type="statusTagType(order.status)" size="large">{{ order.statusLabel }}</Tag>
      <p class="hint">{{ statusHint(order) }}</p>
    </section>

    <Steps v-if="stepIndex >= 0" :active="stepIndex" class="steps">
      <Step>付款</Step>
      <Step>等待接单</Step>
      <Step>服务中</Step>
      <Step>已完成</Step>
    </Steps>

    <NoticeBar
      v-if="isTerminalAbnormal && order.closeReason"
      :text="order.closeReason"
      color="#ed6a0c"
      background="#fffbe8"
      left-icon="info-o"
    />

    <CellGroup inset title="服务信息">
      <Cell title="主播" :value="order.talentNickname ?? '—'" />
      <Cell title="服务" :value="`${order.serviceItemName} · ${order.serviceSpecName}`" />
      <Cell title="数量" :value="String(order.quantity)" />
      <Cell title="单价" :value="formatPrice(order.unitPriceCents)" />
      <Cell title="应付">
        <template #value>
          <span class="price">{{ formatPrice(order.payableCents) }}</span>
        </template>
      </Cell>
      <Cell v-if="order.remark" title="备注" :value="order.remark" />
    </CellGroup>

    <CellGroup inset title="订单信息">
      <Cell title="订单号" :value="order.orderRef" />
      <Cell title="下单时间" :value="formatTime(order.createdAt)" />
      <Cell v-if="order.paidAt" title="收款时间" :value="formatTime(order.paidAt)" />
      <Cell v-if="order.acceptedAt" title="接单时间" :value="formatTime(order.acceptedAt)" />
      <Cell v-if="order.completedAt" title="完成时间" :value="formatTime(order.completedAt)" />
    </CellGroup>

    <CellGroup v-if="history.length" inset title="状态轨迹">
      <Cell
        v-for="(h, i) in history"
        :key="i"
        :title="h.toLabel"
        :label="h.reason ?? undefined"
        :value="formatTime(h.occurredAt)"
      />
    </CellGroup>

    <div class="actions">
      <Button
        v-if="order.actions.includes('UPLOAD_PROOF')"
        type="primary"
        block
        round
        @click="router.push(`/orders/${order.id}/payment`)"
      >
        去付款
      </Button>
      <Button
        v-if="order.actions.includes('RATE')"
        type="primary"
        block
        round
        disabled
      >
        评价本次服务（开发中）
      </Button>
      <Button
        v-if="order.actions.includes('REORDER')"
        block
        round
        @click="router.push(`/talents/${order.talentId}/order`)"
      >
        再次下单
      </Button>
    </div>
  </template>
</template>

<style scoped>
.status {
  padding: 20px 16px 12px;
  background: #fff;
  text-align: center;
}

.hint {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #646566;
}

.steps {
  background: #fff;
  padding-bottom: 12px;
}

.price {
  color: #ee0a24;
  font-weight: 600;
}

.actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
