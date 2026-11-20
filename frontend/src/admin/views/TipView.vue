<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tipAdminApi, type AdminTip, type TipLimits, type TipStatus } from '@/admin/api/tip'
import { centsToYuan, formatPrice, yuanToCents } from '@/utils/money'
import { ApiError } from '@/api/http'

const tab = ref('pending')
const loading = ref(false)

/** 待支付与待确认收款合成一个队列：对运营来说都是「还没入账、要我处理」。 */
const pending = ref<AdminTip[]>([])
const records = ref<AdminTip[]>([])
const limits = ref<TipLimits | null>(null)

const recordStatus = ref<TipStatus>('COMPLETED')

const limitForm = reactive({ editing: false, min: '', max: '' })

const confirmDialog = reactive({
  visible: false,
  tipId: 0,
  tipRef: '',
  talentNickname: '',
  amountCents: 0,
  paidYuan: '',
  note: '',
  submitting: false,
})

const paidCents = computed(() => yuanToCents(confirmDialog.paidYuan))

const hasGap = computed(
  () => paidCents.value !== null && paidCents.value !== confirmDialog.amountCents,
)

const gapText = computed(() => {
  if (!hasGap.value || paidCents.value === null) return ''
  const diff = paidCents.value - confirmDialog.amountCents
  return diff > 0 ? `多收 ${formatPrice(diff)}` : `少收 ${formatPrice(-diff)}`
})

async function load() {
  loading.value = true
  try {
    const [p1, p2, rec, lim] = await Promise.all([
      tipAdminApi.list('PENDING_PAYMENT'),
      tipAdminApi.list('PENDING_CONFIRM'),
      tipAdminApi.list(recordStatus.value),
      tipAdminApi.limits(),
    ])
    // 两个状态合起来按 id 倒序，新的在前
    pending.value = [...p1, ...p2].sort((a, b) => b.id - a.id)
    records.value = rec
    limits.value = lim
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function loadRecords() {
  try {
    records.value = await tipAdminApi.list(recordStatus.value)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 确认收款 ----------

function openConfirm(row: AdminTip) {
  Object.assign(confirmDialog, {
    visible: true,
    tipId: row.id,
    tipRef: row.tipRef,
    talentNickname: row.talentNickname ?? '—',
    amountCents: row.amountCents,
    paidYuan: centsToYuan(row.amountCents),
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

  confirmDialog.submitting = true
  try {
    await tipAdminApi.confirm(confirmDialog.tipId, paidCents.value, confirmDialog.note.trim())
    ElMessage.success('已确认收款，这笔打赏直接计为已完成')
    confirmDialog.visible = false
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    confirmDialog.submitting = false
  }
}

// ---------- 取消 ----------

async function cancel(row: AdminTip) {
  let reason: string
  try {
    const input = await ElMessageBox.prompt(
      `取消 ${row.fanNickname} 给 ${row.talentNickname ?? '—'} 的 ${formatPrice(row.amountCents)} 打赏。` +
        '只能取消还没收款的记录；如果钱已经到账，请先确认收款再走退款。',
      '取消打赏',
      {
        inputPlaceholder: '例如：粉丝发起后一直未付款',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写取消原因'),
      },
    )
    reason = input.value
  } catch {
    return
  }

  try {
    await tipAdminApi.cancel(row.id, reason.trim())
    ElMessage.success('已取消')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 退款 ----------

async function refund(row: AdminTip) {
  let reason: string
  try {
    const input = await ElMessageBox.prompt(
      `标记 ${row.tipRef}（${formatPrice(row.amountCents)}）为已退款。` +
        '结算台账会冲销这一笔，榜单也会按它当初计入的那个周期扣回——不是从本周期扣。' +
        '钱要你自己转回去，系统不会代为退款。',
      '标记已退款',
      {
        inputPlaceholder: '例如：粉丝误操作，已原路转回',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写退款原因'),
      },
    )
    reason = input.value
  } catch {
    return
  }

  try {
    await tipAdminApi.refund(row.id, reason.trim())
    ElMessage.success('已标记退款，台账与榜单已冲销')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 金额上限 ----------

function startEditLimits() {
  if (!limits.value) return
  limitForm.editing = true
  limitForm.min = centsToYuan(limits.value.minCents)
  limitForm.max = centsToYuan(limits.value.maxCents)
}

async function saveLimits() {
  const min = yuanToCents(limitForm.min)
  const max = yuanToCents(limitForm.max)
  if (min === null || max === null) {
    ElMessage.warning('金额请填到分，最多两位小数')
    return
  }
  try {
    await tipAdminApi.updateLimits(min, max)
    ElMessage.success('已保存，此后发起的打赏按新范围校验')
    limitForm.editing = false
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 展示 ----------

function asTip(row: unknown): AdminTip {
  return row as AdminTip
}

function when(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function statusType(status: TipStatus) {
  return (
    {
      PENDING_PAYMENT: 'info',
      PENDING_CONFIRM: 'warning',
      COMPLETED: 'success',
      CANCELLED: 'info',
      REFUNDED: 'danger',
    } as const
  )[status]
}

function splitText(row: AdminTip) {
  if (row.guildCents === null || row.talentCents === null) return '—'
  return `公会 ${formatPrice(row.guildCents)} / 主播 ${formatPrice(row.talentCents)}`
}
</script>

<template>
  <div v-loading="loading" class="page">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>打赏也要人工确认收款</template>
      和订单一样，钱到账后要有人在这里点确认，这笔打赏才会计入结算与榜单。
      与订单不同的是：<strong>打赏确认后直接计为已完成</strong>，主播不需要做任何事。
      另外<strong>打赏没有超时回收</strong>，粉丝发起却不付款的记录会一直挂在待处理里，
      只能手工取消。
    </el-alert>

    <el-tabs v-model="tab">
      <!-- ---------- 待处理 ---------- -->
      <el-tab-pane name="pending">
        <template #label>
          待处理
          <el-badge v-if="pending.length" :value="pending.length" type="danger" class="tab-badge" />
        </template>

        <el-card shadow="never">
          <el-empty v-if="!pending.length" description="没有待处理的打赏" />
          <el-table v-else :data="pending" row-key="id">
            <el-table-column label="单号" min-width="180">
              <template #default="{ row }">
                <div class="mono">{{ asTip(row).tipRef }}</div>
                <div class="sub">
                  发起 {{ when(asTip(row).createdAt) }} · 支付截止
                  {{ when(asTip(row).payDeadline) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="粉丝 → 主播" min-width="170">
              <template #default="{ row }">
                <div>{{ asTip(row).fanNickname }} → {{ asTip(row).talentNickname ?? '—' }}</div>
                <div class="sub">粉丝 id {{ asTip(row).fanUserId ?? '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="190">
              <template #default="{ row }">
                <strong>{{ formatPrice(asTip(row).amountCents) }}</strong>
                <div class="sub">{{ splitText(asTip(row)) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="留言" min-width="160">
              <template #default="{ row }">
                <span class="sub">{{ asTip(row).message || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(asTip(row).status)" size="small">
                  {{ asTip(row).statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="openConfirm(asTip(row))">
                  确认收款
                </el-button>
                <el-button size="small" plain @click="cancel(asTip(row))">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 记录 ---------- -->
      <el-tab-pane label="记录" name="records">
        <el-card shadow="never" style="margin-bottom: 12px">
          <el-radio-group v-model="recordStatus" @change="loadRecords">
            <el-radio-button value="COMPLETED">已完成</el-radio-button>
            <el-radio-button value="REFUNDED">已退款</el-radio-button>
            <el-radio-button value="CANCELLED">已取消</el-radio-button>
          </el-radio-group>
          <span class="sub" style="margin-left: 12px">
            接口暂未分页，记录多了之后这一页会变慢
          </span>
        </el-card>

        <el-card shadow="never">
          <el-empty v-if="!records.length" description="没有这一类的记录" />
          <el-table v-else :data="records" row-key="id">
            <el-table-column label="单号" min-width="180">
              <template #default="{ row }">
                <div class="mono">{{ asTip(row).tipRef }}</div>
                <div class="sub">发起 {{ when(asTip(row).createdAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="粉丝 → 主播" min-width="170">
              <template #default="{ row }">
                {{ asTip(row).fanNickname }} → {{ asTip(row).talentNickname ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="金额" width="190">
              <template #default="{ row }">
                <strong>{{ formatPrice(asTip(row).amountCents) }}</strong>
                <div v-if="asTip(row).paidCents !== null" class="sub">
                  实收 {{ formatPrice(asTip(row).paidCents!) }}
                </div>
                <div class="sub">{{ splitText(asTip(row)) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="留言" min-width="150">
              <template #default="{ row }">
                <span class="sub">{{ asTip(row).message || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(asTip(row).status)" size="small">
                  {{ asTip(row).statusLabel }}
                </el-tag>
                <div v-if="asTip(row).paidAt" class="sub">收款 {{ when(asTip(row).paidAt) }}</div>
                <div v-if="asTip(row).closeReason" class="sub">
                  {{ asTip(row).closeReason }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="asTip(row).status === 'COMPLETED'"
                  type="danger"
                  size="small"
                  plain
                  @click="refund(asTip(row))"
                >
                  标记退款
                </el-button>
                <span v-else class="sub">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ---------- 金额范围 ---------- -->
      <el-tab-pane label="金额范围" name="limits">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          粉丝发起打赏时按这个范围校验。改动只影响<strong>此后</strong>发起的打赏，
          已经创建的记录不受影响。
        </el-alert>

        <el-card shadow="never" v-if="limits">
          <template v-if="limitForm.editing">
            <el-form label-width="90px" style="max-width: 420px">
              <el-form-item label="最低金额">
                <el-input v-model="limitForm.min" placeholder="元">
                  <template #prepend>¥</template>
                </el-input>
              </el-form-item>
              <el-form-item label="最高金额">
                <el-input v-model="limitForm.max" placeholder="元">
                  <template #prepend>¥</template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveLimits">保存</el-button>
                <el-button @click="limitForm.editing = false">取消</el-button>
              </el-form-item>
            </el-form>
          </template>

          <template v-else>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="最低金额">
                {{ formatPrice(limits.minCents) }}
              </el-descriptions-item>
              <el-descriptions-item label="最高金额">
                {{ formatPrice(limits.maxCents) }}
              </el-descriptions-item>
            </el-descriptions>
            <p class="hint-block">
              上限尤其该确认过再上线：一笔两千块的打赏，风控与纠纷处理要跟得上。
              系统不会代为退款，退钱是人工转账。
            </p>
            <el-button style="margin-top: 4px" @click="startEditLimits">修改</el-button>
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ---------- 确认收款弹窗 ---------- -->
    <el-dialog v-model="confirmDialog.visible" title="确认打赏收款" width="480px">
      <el-form label-width="90px">
        <el-form-item label="单号">
          <span class="mono">{{ confirmDialog.tipRef }}</span>
        </el-form-item>
        <el-form-item label="主播">{{ confirmDialog.talentNickname }}</el-form-item>
        <el-form-item label="应收">
          <strong>{{ formatPrice(confirmDialog.amountCents) }}</strong>
        </el-form-item>
        <el-form-item label="实收" required>
          <el-input v-model="confirmDialog.paidYuan" placeholder="元，最多两位小数">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="confirmDialog.note"
            type="textarea"
            :rows="2"
            placeholder="选填，例如银行流水号或差异说明"
            maxlength="255"
          />
        </el-form-item>
      </el-form>

      <el-alert v-if="hasGap" type="warning" :closable="false" show-icon>
        <template #title>实收与应收不一致：{{ gapText }}</template>
        分成已在发起时按<strong>打赏金额</strong>算好并固定，不会按实收重算。
        差额怎么处理请写在备注里。
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
.hint-block {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.tab-badge {
  margin-left: 2px;
  vertical-align: middle;
}
</style>
