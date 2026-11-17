<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Skeleton, Button, PullRefresh } from 'vant'
import { orderApi, OrderStatus, type OrderView } from '@/api/order'
import { formatPrice } from '@/utils/money'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const orders = ref<OrderView[]>([])
const loading = ref(true)
const refreshing = ref(false)

/** 只分「进行中 / 已结束」两组。更细的筛选在这个量级上只是多一排要点的东西。 */
const tab = ref<'ALL' | 'OPEN' | 'DONE'>('ALL')

const OPEN_STATUSES: string[] = [
  OrderStatus.PENDING_PAYMENT,
  OrderStatus.PENDING_CONFIRM,
  OrderStatus.PENDING_ACCEPT,
  OrderStatus.ACCEPTED,
]

const shown = computed(() => {
  if (tab.value === 'ALL') return orders.value
  const open = tab.value === 'OPEN'
  return orders.value.filter((o) => OPEN_STATUSES.includes(o.status) === open)
})

const openCount = computed(() => orders.value.filter((o) => OPEN_STATUSES.includes(o.status)).length)

onMounted(load)

async function load() {
  try {
    orders.value = await orderApi.myOrders()
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function open(id: number) {
  router.push(`/orders/${id}`)
}

function reorder(order: OrderView, event: Event) {
  event.stopPropagation()
  router.push(`/talents/${order.talentId}/order`)
}

/** 待办态标红，终止态变灰，其余用主色——比 Vant 的五色标签更容易一眼扫过。 */
function toneOf(status: string) {
  if (status === OrderStatus.PENDING_PAYMENT || status === OrderStatus.PENDING_CONFIRM) {
    return 'urge'
  }
  if (OPEN_STATUSES.includes(status)) return 'live'
  if (status === OrderStatus.COMPLETED) return 'done'
  return 'off'
}

function initial(nickname: string | null) {
  return (nickname ?? '').trim().charAt(0) || '·'
}

function dayText(iso: string) {
  const at = new Date(iso)
  return `${at.getMonth() + 1}/${at.getDate()}`
}
</script>

<template>
  <div class="page">
    <NavBar title="我的订单" left-arrow @click-left="$router.push('/')" />

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />

    <PullRefresh v-else v-model="refreshing" @refresh="load">
      <nav v-if="orders.length" class="tabs">
        <button :class="{ on: tab === 'ALL' }" @click="tab = 'ALL'">
          全部 {{ orders.length }}
        </button>
        <button :class="{ on: tab === 'OPEN' }" @click="tab = 'OPEN'">
          进行中 {{ openCount }}
        </button>
        <button :class="{ on: tab === 'DONE' }" @click="tab = 'DONE'">
          已结束 {{ orders.length - openCount }}
        </button>
      </nav>

      <EmptyState v-if="orders.length === 0" description="还没有订单">
        <Button type="primary" round @click="router.push('/')">去看看主播</Button>
      </EmptyState>
      <EmptyState v-else-if="shown.length === 0" description="这一类暂时没有订单" />

      <div v-else class="list">
        <article v-for="o in shown" :key="o.id" class="card" @click="open(o.id)">
          <header class="head">
            <div class="avatar">{{ initial(o.talentNickname) }}</div>
            <div class="who">
              <span class="talent">{{ o.talentNickname ?? '主播' }}</span>
              <span class="day">{{ dayText(o.createdAt) }}</span>
            </div>
            <span class="state" :class="toneOf(o.status)">{{ o.statusLabel }}</span>
          </header>

          <p class="service">
            {{ o.serviceItemName }} · {{ o.serviceSpecName }}
            <span v-if="o.quantity > 1" class="num"> × {{ o.quantity }}</span>
          </p>

          <footer class="foot">
            <span class="ref num">{{ o.orderRef }}</span>
            <span class="money">
              <!-- 用了券才展示原价删除线。没用券时并排两个一样的数字只会让人以为被重复收费 -->
              <s v-if="o.couponDiscountCents > 0" class="was num">
                {{ formatPrice(o.originalCents) }}
              </s>
              <span class="price num">{{ formatPrice(o.payableCents) }}</span>
            </span>
          </footer>

          <div v-if="o.actions.includes('REORDER')" class="actions">
            <button class="again" @click="reorder(o, $event)">再次下单</button>
          </div>
        </article>
      </div>
    </PullRefresh>
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
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4) 0;
}

.tabs button {
  flex: 1;
  border: none;
  border-radius: var(--r-pill);
  padding: 7px 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  background: var(--c-surface);
  box-shadow: var(--sd-card);
  cursor: pointer;
}

.tabs button.on {
  color: #fff;
  background: var(--c-brand);
}

.list {
  padding: var(--sp-3) var(--sp-4) 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.card {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  padding: var(--sp-4);
  cursor: pointer;
}

.head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.avatar {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 50%;
  background: var(--g-avatar);
  color: #fff;
  font-size: var(--fs-lg);
  font-weight: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}

.who {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.talent {
  font-size: var(--fs-md);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day {
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.state {
  flex: none;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.state.urge {
  color: var(--c-price);
}

.state.live {
  color: var(--c-brand-deep);
}

.state.done {
  color: var(--c-text-2);
}

.state.off {
  color: var(--c-text-3);
}

.service {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.foot {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
}

.ref {
  font-size: var(--fs-xs);
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.money {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.was {
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.price {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--c-price);
}

.actions {
  margin-top: var(--sp-3);
  display: flex;
  justify-content: flex-end;
}

.again {
  border: 1px solid var(--c-border);
  border-radius: var(--r-pill);
  background: none;
  padding: 5px 14px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
}
</style>
