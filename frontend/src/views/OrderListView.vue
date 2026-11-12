<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton, Tag, Button, PullRefresh } from 'vant'
import { orderApi, statusTagType, type OrderView } from '@/api/order'
import { formatPrice } from '@/utils/money'

const router = useRouter()
const orders = ref<OrderView[]>([])
const loading = ref(true)
const refreshing = ref(false)

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
</script>

<template>
  <NavBar title="我的订单" left-arrow @click-left="$router.push('/')" />

  <Skeleton v-if="loading" :row="4" style="padding: 16px" />

  <PullRefresh v-else v-model="refreshing" @refresh="load">
    <Empty v-if="orders.length === 0" description="还没有订单">
      <Button type="primary" round @click="router.push('/')">去看看主播</Button>
    </Empty>

    <div v-else class="list">
      <article v-for="o in orders" :key="o.id" class="card" @click="open(o.id)">
        <header class="head">
          <span class="talent">{{ o.talentNickname ?? '主播' }}</span>
          <Tag :type="statusTagType(o.status)" size="medium">{{ o.statusLabel }}</Tag>
        </header>
        <p class="service">
          {{ o.serviceItemName }} · {{ o.serviceSpecName }}
          <span v-if="o.quantity > 1"> × {{ o.quantity }}</span>
        </p>
        <footer class="foot">
          <span class="ref">单号 {{ o.orderRef }}</span>
          <span class="price">{{ formatPrice(o.payableCents) }}</span>
        </footer>
        <div v-if="o.actions.includes('REORDER')" class="actions">
          <Button size="small" round @click="reorder(o, $event)">再次下单</Button>
        </div>
      </article>
    </div>
  </PullRefresh>
</template>

<style scoped>
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
  cursor: pointer;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.talent {
  font-size: 15px;
  font-weight: 600;
}

.service {
  margin: 8px 0 0;
  font-size: 13px;
  color: #646566;
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
  color: #ee0a24;
  font-weight: 600;
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>
