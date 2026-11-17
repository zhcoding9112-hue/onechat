<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Skeleton, Button, Rate, showToast } from 'vant'
import {
  orderApi,
  surveyApi,
  statusHint,
  OrderStatus,
  type HistoryEntry,
  type MySurvey,
  type OrderView,
} from '@/api/order'
import { formatPrice } from '@/utils/money'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const order = ref<OrderView | null>(null)
const history = ref<HistoryEntry[]>([])
const loading = ref(true)
const survey = ref<MySurvey | null>(null)
const pendingScore = ref(0)
const rating = ref(false)

onMounted(async () => {
  try {
    order.value = await orderApi.detail(orderId.value)
    history.value = await orderApi.history(orderId.value)
    if (order.value.actions.includes('RATE')) {
      survey.value = await surveyApi.mine(orderId.value)
    }
  } finally {
    loading.value = false
  }
})

/**
 * 正常流程的四步进度；终止态不展示进度，改用提示条说明。
 *
 * 第三步叫「已接单」而不是「服务中」——「服务中」是上一个 change 里砍掉的状态，
 * 这里的文案一直没跟着改，结果订单处于 ACCEPTED 时，状态标签写着「已接单」、
 * 进度条却高亮「服务中」，像是两个不同的状态。
 */
const STEPS = ['付款', '等待接单', '已接单', '已完成']

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

/** 状态色：待办催一下，进行中用主色，终止态收敛成灰。 */
function toneOf(status: string) {
  if (status === OrderStatus.PENDING_PAYMENT || status === OrderStatus.PENDING_CONFIRM) {
    return 'urge'
  }
  if (
    status === OrderStatus.TIMEOUT ||
    status === OrderStatus.CANCELLED ||
    status === OrderStatus.REFUNDED
  ) {
    return 'off'
  }
  return 'live'
}

async function submitRating() {
  if (pendingScore.value < 1) {
    showToast('请先选择分数')
    return
  }
  rating.value = true
  try {
    survey.value = await surveyApi.submit(orderId.value, pendingScore.value)
    showToast('感谢你的评价')
  } finally {
    rating.value = false
  }
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="page">
    <NavBar title="订单详情" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" :row="6" style="padding: 16px" />
    <EmptyState v-else-if="!order" description="订单不存在" />

    <template v-else>
      <!-- 状态 + 进度：一屏之内先回答「现在到哪一步了、我要做什么」 -->
      <section class="block hero" :class="toneOf(order.status)">
        <p class="state">{{ order.statusLabel }}</p>
        <p class="hint">{{ statusHint(order) }}</p>

        <ol v-if="stepIndex >= 0" class="steps">
          <li
            v-for="(label, i) in STEPS"
            :key="label"
            :class="{ done: i < stepIndex, on: i === stepIndex }"
          >
            <i class="dot"></i>
            <span>{{ label }}</span>
          </li>
        </ol>

        <p v-else-if="order.closeReason" class="closed">{{ order.closeReason }}</p>
      </section>

      <!-- 金额：整页最该被看清的东西，单独一块 -->
      <section class="block money">
        <div class="row">
          <span>{{ order.serviceItemName }} · {{ order.serviceSpecName }}</span>
          <span class="num">{{ formatPrice(order.unitPriceCents) }} × {{ order.quantity }}</span>
        </div>
        <!-- 没用券时原价与实付相同，多列一行只会让人以为被重复收费 -->
        <template v-if="order.couponDiscountCents > 0">
          <div class="row">
            <span>原价</span>
            <span class="num">{{ formatPrice(order.originalCents) }}</span>
          </div>
          <div class="row cut">
            <span>优惠券抵扣</span>
            <span class="num">-{{ formatPrice(order.couponDiscountCents) }}</span>
          </div>
        </template>
        <div class="row total">
          <span>应付</span>
          <span class="price num">{{ formatPrice(order.payableCents) }}</span>
        </div>
      </section>

      <section class="block info">
        <h3>服务信息</h3>
        <div class="row"><span>主播</span><span>{{ order.talentNickname ?? '—' }}</span></div>
        <div v-if="order.remark" class="row">
          <span>备注</span><span class="wrap">{{ order.remark }}</span>
        </div>
        <div class="row"><span>订单号</span><span class="num">{{ order.orderRef }}</span></div>
        <div class="row">
          <span>下单时间</span><span class="num">{{ formatTime(order.createdAt) }}</span>
        </div>
        <div v-if="order.paidAt" class="row">
          <span>收款时间</span><span class="num">{{ formatTime(order.paidAt) }}</span>
        </div>
        <div v-if="order.acceptedAt" class="row">
          <span>接单时间</span><span class="num">{{ formatTime(order.acceptedAt) }}</span>
        </div>
        <div v-if="order.completedAt" class="row">
          <span>完成时间</span><span class="num">{{ formatTime(order.completedAt) }}</span>
        </div>
      </section>

      <section v-if="history.length" class="block info">
        <h3>状态轨迹</h3>
        <ul class="trail">
          <li v-for="(h, i) in history" :key="i">
            <span class="t-label">{{ h.toLabel }}</span>
            <span class="t-time num">{{ formatTime(h.occurredAt) }}</span>
            <span v-if="h.reason" class="t-reason">{{ h.reason }}</span>
          </li>
        </ul>
      </section>

      <!-- 评价：规格要求对粉丝不公开展示，所以这块只在自己的订单里出现 -->
      <section v-if="order.actions.includes('RATE')" class="block rate">
        <h3>评价本次服务</h3>
        <template v-if="survey">
          <Rate :model-value="survey.score" readonly size="20" />
          <p class="tip">已提交。评价不会公开展示，主播只能看到汇总后的平均分。</p>
        </template>
        <template v-else>
          <Rate v-model="pendingScore" size="26" />
          <p class="tip">评价不会公开展示，主播只能看到汇总后的平均分。</p>
          <Button type="primary" block round :loading="rating" @click="submitRating">
            提交评价
          </Button>
        </template>
      </section>

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
          v-if="order.actions.includes('REORDER')"
          block
          round
          @click="router.push(`/talents/${order.talentId}/order`)"
        >
          再次下单
        </Button>
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

.block {
  margin: var(--sp-3) var(--sp-4) 0;
  padding: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
}

.block h3 {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-text-3);
}

/* ---------- 状态 ---------- */
.hero {
  text-align: center;
}

.state {
  margin: 0;
  font-size: var(--fs-xl);
  font-weight: 700;
}

.hero.urge .state {
  color: var(--c-price);
}

.hero.live .state {
  color: var(--c-brand-deep);
}

.hero.off .state {
  color: var(--c-text-3);
}

.hint {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--c-text-2);
}

.closed {
  margin: var(--sp-3) 0 0;
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  background: var(--c-danger-soft);
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--c-danger);
}

.steps {
  list-style: none;
  display: flex;
  margin: var(--sp-5) 0 0;
  padding: 0;
}

.steps li {
  flex: 1;
  position: relative;
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

/* 连接线画在圆点之间，第一段不画，否则左端会多出半截 */
.steps li::before {
  content: '';
  position: absolute;
  top: 5px;
  right: 50%;
  left: -50%;
  height: 2px;
  background: var(--c-border);
}

.steps li:first-child::before {
  display: none;
}

.steps li.done::before,
.steps li.on::before {
  background: var(--c-brand);
}

.dot {
  position: relative;
  display: block;
  width: 12px;
  height: 12px;
  margin: 0 auto var(--sp-2);
  border-radius: 50%;
  background: var(--c-border);
}

.steps li.done .dot {
  background: var(--c-brand);
}

.steps li.on .dot {
  background: var(--c-brand);
  box-shadow: 0 0 0 4px var(--c-brand-soft);
}

.steps li.done,
.steps li.on {
  color: var(--c-text);
}

.steps li.on {
  font-weight: 600;
}

/* ---------- 明细 ---------- */
.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: 7px 0;
  font-size: var(--fs-md);
}

.row > span:first-child {
  flex: none;
  color: var(--c-text-2);
}

.row > span:last-child {
  text-align: right;
}

.wrap {
  word-break: break-word;
}

.money .row.cut span:last-child {
  color: var(--c-price);
}

.money .row.total {
  margin-top: var(--sp-2);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
}

.price {
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--c-price);
}

.info .row {
  font-size: var(--fs-sm);
}

/* ---------- 轨迹 ---------- */
.trail {
  list-style: none;
  margin: 0;
  padding: 0;
}

.trail li {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px var(--sp-3);
  padding: 6px 0;
  font-size: var(--fs-sm);
}

.t-label {
  font-weight: 600;
}

.t-time {
  text-align: right;
  color: var(--c-text-3);
}

.t-reason {
  grid-column: 1 / -1;
  color: var(--c-text-2);
  line-height: 1.6;
}

/* ---------- 评价 ---------- */
.rate {
  text-align: center;
}

.rate h3 {
  text-align: left;
}

.tip {
  margin: var(--sp-3) 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--c-text-3);
}

.actions {
  padding: var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}
</style>
