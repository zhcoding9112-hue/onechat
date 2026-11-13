<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  NavBar,
  Tabs,
  Tab,
  Empty,
  Skeleton,
  Tag,
  Button,
  Cell,
  CellGroup,
  showToast,
  showConfirmDialog,
} from 'vant'
import { workspaceApi, remainingText } from '@/talent/api/workspace'
import { statusTagType, type OrderView } from '@/api/order'
import { formatPrice } from '@/utils/money'

const tab = ref(0)
const pending = ref<OrderView[]>([])
const active = ref<OrderView[]>([])
const history = ref<OrderView[]>([])
const loading = ref(true)
const busyId = ref<number | null>(null)

/** 每秒走一次，让剩余接单时间真的在倒数。 */
const now = ref(Date.now())
let ticker: number | undefined

onMounted(async () => {
  ticker = window.setInterval(() => (now.value = Date.now()), 1000)
  await load()
})

onBeforeUnmount(() => {
  if (ticker) window.clearInterval(ticker)
})

async function load() {
  loading.value = true
  try {
    const [p, a, h] = await Promise.all([
      workspaceApi.pendingOrders(),
      workspaceApi.activeOrders(),
      workspaceApi.historyOrders(),
    ])
    pending.value = p
    active.value = a
    history.value = h
  } finally {
    loading.value = false
  }
}

/** 依赖 now 以便每秒重算。 */
function remaining(order: OrderView): string | null {
  void now.value
  return remainingText(order.acceptDeadline)
}

/** 服务端把可用操作算好了，前端只照着渲染。 */
function can(order: OrderView, action: string) {
  return order.actions.includes(action as never)
}

/**
 * 能否接单。
 *
 * actions 是拉列表那一刻服务端算好的快照，不会随时间变化；而接单时限会走到。
 * 所以这里再叠一层本地倒计时判断，否则时限已过按钮却还在，点下去只会收到报错。
 * 服务端仍是唯一权威——即便按钮漏显示，接单请求也会被拒。
 */
function canAccept(order: OrderView) {
  return can(order, 'ACCEPT') && remaining(order) !== null
}

async function accept(order: OrderView) {
  busyId.value = order.id
  try {
    await workspaceApi.accept(order.id)
    showToast('已接单，请尽快联系粉丝')
    await load()
  } finally {
    busyId.value = null
  }
}

async function complete(order: OrderView) {
  await showConfirmDialog({
    title: '确认完成',
    message: `确认「${order.serviceItemName} · ${order.serviceSpecName}」已经服务完毕？`,
  })
  busyId.value = order.id
  try {
    await workspaceApi.complete(order.id)
    showToast('已标记完成')
    await load()
  } finally {
    busyId.value = null
  }
}

const currentList = computed(() => [pending.value, active.value, history.value][tab.value] ?? [])
const emptyText = computed(
  () => ['暂无待接单的订单', '暂无进行中的订单', '还没有历史订单'][tab.value],
)
</script>

<template>
  <NavBar title="主播工作台">
    <template #right>
      <span class="nav-link" @click="load">刷新</span>
    </template>
  </NavBar>

  <Tabs v-model:active="tab" sticky>
    <Tab :title="`待接单${pending.length ? ` (${pending.length})` : ''}`" />
    <Tab :title="`进行中${active.length ? ` (${active.length})` : ''}`" />
    <Tab title="历史" />
  </Tabs>

  <Skeleton v-if="loading" :row="4" style="padding: 16px" />
  <Empty v-else-if="currentList.length === 0" :description="emptyText" />

  <div v-else class="list">
    <article v-for="o in currentList" :key="o.id" class="card">
      <header class="head">
        <span class="service">{{ o.serviceItemName }} · {{ o.serviceSpecName }}</span>
        <Tag :type="statusTagType(o.status)" size="medium">{{ o.statusLabel }}</Tag>
      </header>

      <!-- 待接单：倒数剩余时间，到零就不给按钮 -->
      <p v-if="tab === 0" class="countdown" :class="{ expired: !remaining(o) }">
        <template v-if="remaining(o)">剩余接单时间 {{ remaining(o) }}</template>
        <template v-else>已超过接单时限</template>
      </p>

      <CellGroup v-if="o.contact" :border="false">
        <Cell title="粉丝微信" :value="o.contact" />
        <Cell v-if="o.remark" title="备注" :value="o.remark" />
      </CellGroup>

      <footer class="foot">
        <span class="ref">{{ o.orderRef }}</span>
        <span class="price">应得 {{ formatPrice(o.payableCents) }}</span>
      </footer>

      <div v-if="canAccept(o) || can(o, 'COMPLETE')" class="actions">
        <Button
          v-if="canAccept(o)"
          type="primary"
          size="small"
          round
          :loading="busyId === o.id"
          @click="accept(o)"
        >
          接单
        </Button>
        <Button
          v-if="can(o, 'COMPLETE')"
          type="primary"
          size="small"
          round
          :loading="busyId === o.id"
          @click="complete(o)"
        >
          标记完成
        </Button>
      </div>
    </article>
  </div>
</template>

<style scoped>
.nav-link {
  font-size: 14px;
  color: #3366ff;
}

.list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.service {
  font-size: 15px;
  font-weight: 600;
}

.countdown {
  margin: 8px 0 0;
  font-size: 13px;
  color: #ee0a24;
}

.countdown.expired {
  color: #969799;
}

.foot {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ref {
  font-size: 12px;
  color: #c8c9cc;
}

.price {
  font-size: 13px;
  color: #646566;
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
