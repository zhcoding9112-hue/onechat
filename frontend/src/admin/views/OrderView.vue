<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  orderAdminApi,
  type AdminOrder,
  type OrderDeadlineSetting,
  type OrderHistoryEntry,
} from '@/admin/api/order'
import { formatPrice } from '@/utils/money'
import { ApiError } from '@/api/http'

/** 与后端 OrderStatus 一一对应。 */
const STATUSES = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PENDING_CONFIRM', label: '待确认收款' },
  { value: 'PENDING_ACCEPT', label: '待接单' },
  { value: 'ACCEPTED', label: '已接单' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'TIMEOUT', label: '已超时' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' },
] as const

const tab = ref('refund')
const loading = ref(false)

const refundQueue = ref<AdminOrder[]>([])
const orders = ref<AdminOrder[]>([])
const deadlines = ref<OrderDeadlineSetting | null>(null)

const filterStatus = ref('')
/** 订单号 / 主播 / 粉丝微信号的本地过滤。接口没有搜索参数。 */
const keyword = ref('')

const deadlineForm = reactive({ editing: false, pay: 30, accept: 15 })

const history = reactive({
  visible: false,
  order: null as AdminOrder | null,
  loading: false,
  entries: [] as OrderHistoryEntry[],
})

const shown = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return orders.value
  return orders.value.filter((o) =>
    [o.orderRef, o.talentNickname, o.contact, o.serviceItemName]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw)),
  )
})

/** 待退款队列里压着的钱。超时订单的钱已经收了，还没退回去。 */
const owedCents = computed(() =>
  refundQueue.value.reduce((sum, o) => sum + (o.paidCents ?? o.payableCents), 0),
)

async function load() {
  loading.value = true
  try {
    const [queue, list, dl] = await Promise.all([
      orderAdminApi.refundQueue(),
      orderAdminApi.list(filterStatus.value || undefined),
      orderAdminApi.deadlines(),
    ])
    refundQueue.value = queue
    orders.value = list
    deadlines.value = dl
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function loadOrders() {
  try {
    orders.value = await orderAdminApi.list(filterStatus.value || undefined)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 处置 ----------

function can(order: AdminOrder, action: string) {
  return order.actions.includes(action)
}

async function cancel(order: AdminOrder) {
  let reason: string
  try {
    const input = await ElMessageBox.prompt(
      `取消订单 ${order.orderRef}（${order.talentNickname ?? '—'}，${formatPrice(order.payableCents)}）。` +
        (order.status === 'PENDING_PAYMENT' || order.status === 'PENDING_CONFIRM'
          ? '这一单还没确认收款，取消后不涉及退钱。'
          : '这一单的钱已经收了，取消不会自动退款，退钱要你另外做。') +
        '原因会记在订单上，粉丝看得到。',
      '取消订单',
      {
        inputPlaceholder: '例如：粉丝主动申请取消',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写取消原因'),
      },
    )
    reason = input.value
  } catch {
    return
  }

  try {
    await orderAdminApi.cancel(order.id, reason.trim())
    ElMessage.success('已取消')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function markRefunded(order: AdminOrder) {
  let reason: string
  try {
    const input = await ElMessageBox.prompt(
      `标记订单 ${order.orderRef} 为已退款，应退 ${formatPrice(order.paidCents ?? order.payableCents)}。` +
        '系统只做台账冲销，钱要你自己转回去。重复标记不会出问题。',
      '标记已退款',
      {
        inputPlaceholder: '例如：主播超时未接单，已原路退回',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写退款原因'),
      },
    )
    reason = input.value
  } catch {
    return
  }

  try {
    await orderAdminApi.markRefunded(order.id, reason.trim())
    ElMessage.success('已标记退款，台账已冲销')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 流转记录 ----------

async function openHistory(order: AdminOrder) {
  Object.assign(history, { visible: true, order, loading: true, entries: [] })
  try {
    history.entries = await orderAdminApi.history(order.id)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    history.loading = false
  }
}

// ---------- 时限 ----------

function startEditDeadlines() {
  if (!deadlines.value) return
  deadlineForm.editing = true
  deadlineForm.pay = deadlines.value.payMinutes
  deadlineForm.accept = deadlines.value.acceptMinutes
}

async function saveDeadlines() {
  if (deadlineForm.pay < 1 || deadlineForm.accept < 1) {
    ElMessage.warning('时限必须大于零分钟')
    return
  }
  try {
    await orderAdminApi.updateDeadlines(deadlineForm.pay, deadlineForm.accept)
    ElMessage.success('已保存，只影响此后创建的订单')
    deadlineForm.editing = false
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 展示 ----------

function asOrder(row: unknown): AdminOrder {
  return row as AdminOrder
}

function asEntry(row: unknown): OrderHistoryEntry {
  return row as OrderHistoryEntry
}

function when(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function statusType(status: string) {
  return (
    {
      PENDING_PAYMENT: 'info',
      PENDING_CONFIRM: 'warning',
      PENDING_ACCEPT: 'warning',
      ACCEPTED: 'primary',
      COMPLETED: 'success',
      TIMEOUT: 'danger',
      CANCELLED: 'info',
      REFUNDED: 'info',
    } as Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'>
  )[status] ?? 'info'
}

function operatorLabel(type: OrderHistoryEntry['operatorType']) {
  return { FAN: '粉丝', TALENT: '主播', OPERATOR: '运营', SYSTEM: '系统' }[type] ?? type
}
</script>

<template>
  <div v-loading="loading" class="page">
    <el-tabs v-model="tab">
      <!-- ---------- 待退款 ---------- -->
      <el-tab-pane name="refund">
        <template #label>
          待退款
          <el-badge
            v-if="refundQueue.length"
            :value="refundQueue.length"
            type="danger"
            class="tab-badge"
          />
        </template>

        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
          <template #title>这里压着已经收了、但还没退回去的钱</template>
          订单超时（粉丝付了钱、主播没在时限内接单）后会落到这个队列。
          <strong>系统只做台账冲销，不会把钱退回去</strong>——转账要你自己做，
          做完回来点「标记已退款」。当前队列合计
          <strong>{{ formatPrice(owedCents) }}</strong>。
        </el-alert>

        <el-card shadow="never">
          <el-empty v-if="!refundQueue.length" description="没有待退款的订单" />
          <el-table v-else :data="refundQueue" row-key="id">
            <el-table-column label="订单" min-width="190">
              <template #default="{ row }">
                <div class="mono">{{ asOrder(row).orderRef }}</div>
                <div class="sub">超时于 {{ when(asOrder(row).closedAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="主播 / 服务" min-width="170">
              <template #default="{ row }">
                <div>{{ asOrder(row).talentNickname ?? '—' }}</div>
                <div class="sub">
                  {{ asOrder(row).serviceItemName ?? '—' }}
                  <template v-if="asOrder(row).serviceSpecName">
                    · {{ asOrder(row).serviceSpecName }}</template
                  >
                </div>
              </template>
            </el-table-column>
            <el-table-column label="应退" width="130">
              <template #default="{ row }">
                <strong>{{
                  formatPrice(asOrder(row).paidCents ?? asOrder(row).payableCents)
                }}</strong>
                <div v-if="asOrder(row).paidCents === null" class="sub">按应付金额</div>
              </template>
            </el-table-column>
            <el-table-column label="粉丝微信" min-width="130">
              <template #default="{ row }">
                <span class="mono sub">{{ asOrder(row).contact || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="超时原因" min-width="150">
              <template #default="{ row }">
                <span class="sub">{{ asOrder(row).closeReason || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openHistory(asOrder(row))">轨迹</el-button>
                <el-button
                  v-if="can(asOrder(row), 'MARK_REFUNDED')"
                  type="primary"
                  size="small"
                  @click="markRefunded(asOrder(row))"
                >
                  标记已退款
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 全部订单 ---------- -->
      <el-tab-pane label="全部订单" name="all">
        <el-card shadow="never" style="margin-bottom: 12px">
          <div class="filters">
            <el-select v-model="filterStatus" style="width: 150px" @change="loadOrders">
              <el-option
                v-for="s in STATUSES"
                :key="s.value"
                :value="s.value"
                :label="s.label"
              />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="订单号 / 主播 / 粉丝微信号"
              clearable
              style="width: 260px"
            />
            <span class="sub">
              共 {{ shown.length }} 条。接口没有分页与搜索，这里是在已加载的列表里过滤。
            </span>
          </div>
        </el-card>

        <el-card shadow="never">
          <el-empty v-if="!shown.length" description="没有符合条件的订单" />
          <el-table v-else :data="shown" row-key="id">
            <el-table-column label="订单" min-width="190">
              <template #default="{ row }">
                <div class="mono">{{ asOrder(row).orderRef }}</div>
                <div class="sub">下单 {{ when(asOrder(row).createdAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="主播 / 服务" min-width="170">
              <template #default="{ row }">
                <div>{{ asOrder(row).talentNickname ?? '—' }}</div>
                <div class="sub">
                  {{ asOrder(row).serviceItemName ?? '—' }}
                  <template v-if="asOrder(row).serviceSpecName">
                    · {{ asOrder(row).serviceSpecName }}</template
                  >
                  <template v-if="asOrder(row).quantity > 1">
                    × {{ asOrder(row).quantity }}</template
                  >
                </div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="160">
              <template #default="{ row }">
                <strong>{{ formatPrice(asOrder(row).payableCents) }}</strong>
                <div v-if="asOrder(row).couponDiscountCents > 0" class="sub">
                  原价 {{ formatPrice(asOrder(row).originalCents) }}，券抵
                  {{ formatPrice(asOrder(row).couponDiscountCents) }}
                </div>
                <div v-if="asOrder(row).paidCents !== null" class="sub">
                  实收 {{ formatPrice(asOrder(row).paidCents!) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="粉丝微信" min-width="120">
              <template #default="{ row }">
                <span class="mono sub">{{ asOrder(row).contact || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(asOrder(row).status)" size="small">
                  {{ asOrder(row).statusLabel }}
                </el-tag>
                <div v-if="asOrder(row).closeReason" class="sub">
                  {{ asOrder(row).closeReason }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openHistory(asOrder(row))">轨迹</el-button>
                <el-button
                  v-if="can(asOrder(row), 'CANCEL')"
                  link
                  type="primary"
                  @click="cancel(asOrder(row))"
                >
                  取消
                </el-button>
                <el-button
                  v-if="can(asOrder(row), 'MARK_REFUNDED')"
                  link
                  type="primary"
                  @click="markRefunded(asOrder(row))"
                >
                  标记退款
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 时限 ---------- -->
      <el-tab-pane label="订单时限" name="deadlines">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          改动<strong>只影响此后创建的订单</strong>。已经在计时中的订单，
          在创建时就把时限折算成了绝对时间写进自己身上，不会被追溯修改。
        </el-alert>

        <el-card shadow="never" v-if="deadlines">
          <template v-if="deadlineForm.editing">
            <el-form label-width="120px" style="max-width: 440px">
              <el-form-item label="支付时限">
                <el-input-number v-model="deadlineForm.pay" :min="1" :max="1440" />
                <span class="hint">分钟，下单后多久不付款算超时</span>
              </el-form-item>
              <el-form-item label="接单时限">
                <el-input-number v-model="deadlineForm.accept" :min="1" :max="1440" />
                <span class="hint">分钟，确认收款后主播多久不接单算超时</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveDeadlines">保存</el-button>
                <el-button @click="deadlineForm.editing = false">取消</el-button>
              </el-form-item>
            </el-form>
          </template>

          <template v-else>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="支付时限">
                {{ deadlines.payMinutes }} 分钟
              </el-descriptions-item>
              <el-descriptions-item label="接单时限">
                {{ deadlines.acceptMinutes }} 分钟
              </el-descriptions-item>
            </el-descriptions>
            <p class="hint-block">
              接单超时的订单会落到「待退款」页签，钱已经收了，要人工退回去。
              时限设得太短会凭空制造退款工作量，设得太长粉丝等不起。
            </p>
            <el-button style="margin-top: 4px" @click="startEditDeadlines">修改</el-button>
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ---------- 流转轨迹 ---------- -->
    <el-drawer
      v-model="history.visible"
      :title="history.order ? `${history.order.orderRef} 流转轨迹` : '流转轨迹'"
      size="720px"
    >
      <template v-if="history.order">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="主播">
            {{ history.order.talentNickname ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="服务">
            {{ history.order.serviceItemName ?? '—' }}
            <template v-if="history.order.serviceSpecName">
              · {{ history.order.serviceSpecName }}</template
            >
          </el-descriptions-item>
          <el-descriptions-item label="应付">
            {{ formatPrice(history.order.payableCents) }}
          </el-descriptions-item>
          <el-descriptions-item label="实收">
            {{
              history.order.paidCents === null
                ? '未收款'
                : formatPrice(history.order.paidCents)
            }}
          </el-descriptions-item>
          <el-descriptions-item label="粉丝微信">
            <span class="mono">{{ history.order.contact || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注">
            {{ history.order.remark || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-empty
          v-if="!history.loading && !history.entries.length"
          description="没有流转记录"
        />
        <el-table v-else v-loading="history.loading" :data="history.entries">
          <el-table-column label="时间" width="140">
            <template #default="{ row }">
              <span class="sub">{{ when(asEntry(row).occurredAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变化" min-width="180">
            <template #default="{ row }">
              <span class="sub">{{ asEntry(row).fromLabel ?? '创建' }}</span>
              <span class="arrow">→</span>
              <strong>{{ asEntry(row).toLabel }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="操作者" width="90">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="asEntry(row).operatorType === 'SYSTEM' ? 'info' : 'primary'"
              >
                {{ operatorLabel(asEntry(row).operatorType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="200">
            <template #default="{ row }">
              <span class="sub">{{ asEntry(row).reason || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.hint-block {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.arrow {
  margin: 0 6px;
  color: #c0c4cc;
}
.tab-badge {
  margin-left: 2px;
  vertical-align: middle;
}
</style>
