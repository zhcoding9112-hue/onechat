<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  paymentAdminApi,
  type PaymentSetting,
  type ReconcileItem,
} from '@/admin/api/payment'
import type { AdminOrder } from '@/admin/api/order'
import { centsToYuan, formatPrice, yuanToCents } from '@/utils/money'
import { ApiError } from '@/api/http'

const tab = ref('reconcile')
const loading = ref(false)

const queue = ref<ReconcileItem[]>([])
const pending = ref<AdminOrder[]>([])
const settings = ref<PaymentSetting[]>([])

/** 确认收款弹窗。payableCents 用来判断是否出现差异，null 表示弹窗没开。 */
const confirmDialog = reactive({
  visible: false,
  orderId: 0,
  orderRef: '',
  payableCents: 0,
  paidYuan: '',
  externalRef: '',
  note: '',
  submitting: false,
})

const paidCents = computed(() => yuanToCents(confirmDialog.paidYuan))

/** 实收与应付不一致：后端此时要求必须填差异说明，这里提前提示。 */
const hasGap = computed(
  () => paidCents.value !== null && paidCents.value !== confirmDialog.payableCents,
)

const gapText = computed(() => {
  if (!hasGap.value || paidCents.value === null) return ''
  const diff = paidCents.value - confirmDialog.payableCents
  return diff > 0 ? `多收 ${formatPrice(diff)}` : `少收 ${formatPrice(-diff)}`
})

async function load() {
  loading.value = true
  try {
    const [q, p, s] = await Promise.all([
      paymentAdminApi.reconcile(),
      paymentAdminApi.pendingPayment(),
      paymentAdminApi.settings(),
    ])
    queue.value = q
    pending.value = p
    settings.value = s
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- 确认收款 ----------

function openConfirm(row: { orderId: number; orderRef: string; payableCents: number }) {
  Object.assign(confirmDialog, {
    visible: true,
    orderId: row.orderId,
    orderRef: row.orderRef,
    payableCents: row.payableCents,
    // 默认按应付金额填好：绝大多数情况金额就是对的，运营只需核对后点确认
    paidYuan: centsToYuan(row.payableCents),
    externalRef: '',
    note: '',
    submitting: false,
  })
}

async function submitConfirm() {
  if (paidCents.value === null) {
    ElMessage.warning('实收金额请填到分，最多两位小数')
    return
  }
  if (paidCents.value < 0) {
    ElMessage.warning('实收金额不得为负')
    return
  }
  if (hasGap.value && !confirmDialog.note.trim()) {
    ElMessage.warning('实收与应付不一致，请填写差异说明')
    return
  }

  confirmDialog.submitting = true
  try {
    const result = await paymentAdminApi.confirm(
      confirmDialog.orderId,
      paidCents.value,
      confirmDialog.externalRef.trim(),
      confirmDialog.note.trim(),
    )
    if (result.outcome === 'ACCEPTED') {
      ElMessage.success(`已确认收款，订单转为${result.statusLabel}`)
    } else if (result.outcome === 'DUPLICATE') {
      // 幂等返回，不是失败：可能是另一个运营刚点过，或同一笔被点了两次
      ElMessage.warning(`这笔已经收过款了，未重复入账：${result.detail}`)
    } else {
      ElMessage.error(`未能入账：${result.detail}`)
    }
    confirmDialog.visible = false
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    confirmDialog.submitting = false
  }
}

// ---------- 驳回凭证 ----------

async function reject(row: ReconcileItem) {
  let reason: string
  try {
    const input = await ElMessageBox.prompt(
      `驳回后订单 ${row.orderRef} 退回待支付，粉丝可以重新上传凭证。原因会记在订单上。`,
      '驳回凭证',
      {
        inputPlaceholder: '例如：截图金额与应付不符 / 收款方不是本平台账户',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写驳回原因'),
      },
    )
    reason = input.value
  } catch {
    return // 运营取消
  }

  try {
    const result = await paymentAdminApi.reject(row.orderId, reason.trim())
    ElMessage.success(`已驳回，订单转为${result.statusLabel}`)
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 收款配置 ----------

const settingEdits = reactive<Record<string, PaymentSetting>>({})

function startEdit(s: PaymentSetting) {
  settingEdits[s.provider] = { ...s }
}

function cancelEdit(provider: string) {
  delete settingEdits[provider]
}

async function saveSetting(provider: string) {
  const edit = settingEdits[provider]
  if (!edit) return
  try {
    await paymentAdminApi.updateSetting(provider, {
      accountName: edit.accountName ?? '',
      accountNo: edit.accountNo ?? '',
      bankName: edit.bankName ?? '',
      qrUrl: edit.qrUrl ?? '',
      tips: edit.tips ?? '',
    })
    ElMessage.success('已保存，此后进入支付环节的订单展示新信息')
    delete settingEdits[provider]
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function toggleEnabled(s: PaymentSetting, enabled: boolean) {
  try {
    await paymentAdminApi.setEnabled(s.provider, enabled)
    ElMessage.success(enabled ? '已启用' : '已停用，不影响已创建的待支付订单')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

/** 占位账号：上线前必须换掉，这里直接标出来而不是等运营自己发现。 */
const PLACEHOLDER_HINTS = ['某某', '示例', '1234 5678', '12345678']

function looksPlaceholder(s: PaymentSetting) {
  const text = `${s.accountName ?? ''}${s.accountNo ?? ''}`
  return PLACEHOLDER_HINTS.some((hint) => text.includes(hint))
}

function providerLabel(provider: string) {
  // 库里存的是小写 manual，这里不假设大小写
  const key = provider.toUpperCase()
  return (
    { MANUAL: '人工收款（银行转账 / 收款码）', WECHAT: '微信支付' }[key] ?? provider
  )
}

// el-table 的插槽行是 DefaultRow，拿到强类型靠这两个转换，与后台其他页面一致
function asItem(row: unknown): ReconcileItem {
  return row as ReconcileItem
}

function asOrder(row: unknown): AdminOrder {
  return row as AdminOrder
}

function when(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <div v-loading="loading">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>人工收款模式下，订单靠这一页往前走</template>
      粉丝转账后上传凭证，订单停在「待确认收款」，<strong>必须有人在这里点确认，主播才能看到这一单</strong>。
      建议每天固定时间对一次账：先核对银行/收款码的入账流水，再回到这里逐条确认。
      确认走的是与微信支付回调同一条路径，同一笔重复点不会二次入账。
    </el-alert>

    <el-tabs v-model="tab">
      <!-- ---------- 待对账 ---------- -->
      <el-tab-pane name="reconcile">
        <template #label>
          待对账
          <el-badge v-if="queue.length" :value="queue.length" type="danger" class="tab-badge" />
        </template>

        <el-card shadow="never">
          <el-empty v-if="!queue.length" description="没有等待对账的订单" />
          <el-table v-else :data="queue" row-key="orderId">
            <el-table-column label="订单" min-width="190">
              <template #default="{ row }">
                <div class="mono">{{ row.orderRef }}</div>
                <div class="sub">下单 {{ when(row.createdAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="主播 / 服务" min-width="190">
              <template #default="{ row }">
                <div>{{ row.talentNickname ?? '—' }}</div>
                <div class="sub">
                  {{ row.serviceItemName ?? '—' }}
                  <template v-if="row.serviceSpecName"> · {{ row.serviceSpecName }}</template>
                  <template v-if="row.quantity > 1"> × {{ row.quantity }}</template>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="应收" width="140">
              <template #default="{ row }">
                <strong>{{ formatPrice(row.payableCents) }}</strong>
                <div v-if="row.couponDiscountCents > 0" class="sub">
                  原价 {{ formatPrice(row.originalCents) }}，券抵
                  {{ formatPrice(row.couponDiscountCents) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="凭证" width="190">
              <template #default="{ row }">
                <div v-if="!row.proofUrls.length" class="sub">无</div>
                <div v-else class="proofs">
                  <el-image
                    v-for="(url, i) in row.proofUrls"
                    :key="url"
                    :src="url"
                    :preview-src-list="row.proofUrls"
                    :initial-index="i"
                    fit="cover"
                    class="proof"
                    preview-teleported
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }">
                <span class="sub">{{ row.remark || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="openConfirm(asItem(row))">
                  确认收款
                </el-button>
                <el-button type="danger" size="small" plain @click="reject(asItem(row))">
                  驳回
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 待支付 ---------- -->
      <el-tab-pane name="pending">
        <template #label>
          待支付
          <el-badge v-if="pending.length" :value="pending.length" type="info" class="tab-badge" />
        </template>

        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          这些订单粉丝还没上传凭证。如果钱已经到账（粉丝直接转了却没回来点上传），
          可以在这里直接确认收款，不必等他补凭证。确认后同样会推进到待接单。
        </el-alert>

        <el-card shadow="never">
          <el-empty v-if="!pending.length" description="没有待支付的订单" />
          <el-table v-else :data="pending" row-key="id">
            <el-table-column label="订单" min-width="190">
              <template #default="{ row }">
                <div class="mono">{{ row.orderRef }}</div>
                <div class="sub">
                  下单 {{ when(row.createdAt) }} · 支付截止 {{ when(row.payDeadline) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="主播 / 服务" min-width="190">
              <template #default="{ row }">
                <div>{{ row.talentNickname ?? '—' }}</div>
                <div class="sub">
                  {{ row.serviceItemName ?? '—' }}
                  <template v-if="row.serviceSpecName"> · {{ row.serviceSpecName }}</template>
                  <template v-if="row.quantity > 1"> × {{ row.quantity }}</template>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="应收" width="120">
              <template #default="{ row }">
                <strong>{{ formatPrice(row.payableCents) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="粉丝微信" min-width="130">
              <template #default="{ row }">
                <span class="mono sub">{{ row.contact || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click="openConfirm({ orderId: asOrder(row).id, orderRef: asOrder(row).orderRef, payableCents: asOrder(row).payableCents })"
                >
                  确认收款
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 收款配置 ---------- -->
      <el-tab-pane label="收款配置" name="settings">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          这里填的收款账号会直接展示给粉丝。改动只影响<strong>此后</strong>进入支付环节的订单，
          已经在付款页的订单仍看到旧信息。
        </el-alert>

        <el-card
          v-for="s in settings"
          :key="s.provider"
          shadow="never"
          style="margin-bottom: 16px"
        >
          <template #header>
            <div class="setting-head">
              <span>{{ providerLabel(s.provider) }}</span>
              <el-switch
                :model-value="s.enabled"
                active-text="启用"
                inactive-text="停用"
                @update:model-value="(v: any) => toggleEnabled(s, Boolean(v))"
              />
            </div>
          </template>

          <el-alert
            v-if="looksPlaceholder(s)"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          >
            <template #title>这还是占位账号</template>
            当前账号看起来是开发期留下的示例值，上线前必须换成真实收款账号，否则粉丝会把钱转错。
          </el-alert>

          <template v-if="settingEdits[s.provider]">
            <el-form label-width="90px" style="max-width: 560px">
              <el-form-item label="收款名称">
                <el-input v-model="settingEdits[s.provider].accountName" maxlength="64" show-word-limit />
              </el-form-item>
              <el-form-item label="收款账号">
                <el-input v-model="settingEdits[s.provider].accountNo" maxlength="64" show-word-limit />
              </el-form-item>
              <el-form-item label="开户行">
                <el-input v-model="settingEdits[s.provider].bankName" maxlength="64" show-word-limit />
              </el-form-item>
              <el-form-item label="收款码图片">
                <el-input
                  v-model="settingEdits[s.provider].qrUrl"
                  placeholder="图片地址，留空则粉丝端不展示收款码"
                  maxlength="512"
                />
              </el-form-item>
              <el-form-item label="付款提示">
                <el-input
                  v-model="settingEdits[s.provider].tips"
                  type="textarea"
                  :rows="2"
                  placeholder="展示在付款页，例如：转账后请截图上传，1 小时内核对"
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveSetting(s.provider)">保存</el-button>
                <el-button @click="cancelEdit(s.provider)">取消</el-button>
              </el-form-item>
            </el-form>
          </template>

          <template v-else>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="收款名称">{{ s.accountName || '未填' }}</el-descriptions-item>
              <el-descriptions-item label="收款账号">
                <span class="mono">{{ s.accountNo || '未填' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="开户行">{{ s.bankName || '未填' }}</el-descriptions-item>
              <el-descriptions-item label="收款码">{{ s.qrUrl || '未设置' }}</el-descriptions-item>
              <el-descriptions-item label="付款提示" :span="2">
                {{ s.tips || '未填' }}
              </el-descriptions-item>
            </el-descriptions>
            <el-button style="margin-top: 12px" @click="startEdit(s)">修改</el-button>
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ---------- 确认收款弹窗 ---------- -->
    <el-dialog v-model="confirmDialog.visible" title="确认收款" width="480px">
      <el-form label-width="90px">
        <el-form-item label="订单">
          <span class="mono">{{ confirmDialog.orderRef }}</span>
        </el-form-item>
        <el-form-item label="应收">
          <strong>{{ formatPrice(confirmDialog.payableCents) }}</strong>
        </el-form-item>
        <el-form-item label="实收" required>
          <el-input v-model="confirmDialog.paidYuan" placeholder="元，最多两位小数">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="流水号">
          <el-input
            v-model="confirmDialog.externalRef"
            placeholder="银行流水号或收款码交易单号，留空则自动用订单号"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="差异说明" :required="hasGap">
          <el-input
            v-model="confirmDialog.note"
            type="textarea"
            :rows="2"
            :placeholder="hasGap ? '金额不一致，必须说明原因' : '选填'"
            maxlength="255"
          />
        </el-form-item>
      </el-form>

      <el-alert v-if="hasGap" type="warning" :closable="false" show-icon>
        <template #title>实收与应收不一致：{{ gapText }}</template>
        订单仍会按<strong>原价</strong>计算分成，不会按实收重算。确认前请想清楚差额怎么处理。
      </el-alert>

      <template #footer>
        <el-button @click="confirmDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="confirmDialog.submitting" @click="submitConfirm">
          确认收款
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.proofs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.proof {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  cursor: zoom-in;
}

.setting-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tab-badge {
  margin-left: 2px;
  vertical-align: middle;
}
</style>
