<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  settlementAdminApi,
  type SettlementEntry,
  type TalentStatement,
} from '@/admin/api/settlement'
import { formatPrice } from '@/utils/money'
import { ApiError } from '@/api/http'

/** 当前月，形如 2026-11。 */
function currentPeriod(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const period = ref(currentPeriod())
const rows = ref<TalentStatement[]>([])
const loading = ref(false)
const exporting = ref(false)
/** 只看有流水的主播。报表默认零值成行，人多了之后全是空行。 */
const onlyActive = ref(false)

const detail = reactive({
  visible: false,
  talentId: 0,
  talentNickname: '',
  loading: false,
  exporting: false,
  entries: [] as SettlementEntry[],
})

const shown = computed(() =>
  onlyActive.value
    ? rows.value.filter((r) => r.orderCount > 0 || r.tipCount > 0)
    : rows.value,
)

const totals = computed(() => {
  const t = {
    orderAmountCents: 0,
    tipAmountCents: 0,
    guildCents: 0,
    talentCents: 0,
    promoCostCents: 0,
    orderCount: 0,
    tipCount: 0,
    settled: 0,
  }
  for (const r of rows.value) {
    t.orderAmountCents += r.orderAmountCents
    t.tipAmountCents += r.tipAmountCents
    t.guildCents += r.guildCents
    t.talentCents += r.talentCents
    t.promoCostCents += r.promoCostCents
    t.orderCount += r.orderCount
    t.tipCount += r.tipCount
    if (r.settled) t.settled += 1
  }
  return t
})

/** 公会实得 = 公会分成 − 促销成本。券是公会掏的，不从主播应得里扣。 */
const guildNetCents = computed(() => totals.value.guildCents - totals.value.promoCostCents)

/** 还没结清、且这个周期有钱要付的主播数。 */
const owing = computed(
  () => rows.value.filter((r) => !r.settled && r.talentCents > 0).length,
)

async function load() {
  loading.value = true
  try {
    rows.value = await settlementAdminApi.report(period.value)
  } catch (e) {
    rows.value = []
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function exportReport() {
  exporting.value = true
  try {
    await settlementAdminApi.exportReport(period.value)
    ElMessage.success('已导出，去浏览器的下载里找')
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
    else throw e
  } finally {
    exporting.value = false
  }
}

// ---------- 结清标记 ----------

async function mark(row: TalentStatement) {
  let note = ''
  try {
    const input = await ElMessageBox.prompt(
      `标记 ${row.talentNickname} 的 ${row.period} 已结清，应付 ${formatPrice(row.talentCents)}。` +
        '这只是在系统里留个记号和时间，不会发生任何资金动作——钱要你自己转。',
      '标记已结清',
      {
        inputPlaceholder: '选填：转账流水号或备注',
        inputValidator: () => true,
      },
    )
    note = input.value ?? ''
  } catch {
    return
  }

  try {
    await settlementAdminApi.mark(row.talentId, row.period, note.trim())
    ElMessage.success('已标记结清')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function unmark(row: TalentStatement) {
  try {
    await ElMessageBox.confirm(
      `撤销 ${row.talentNickname} 的 ${row.period} 结清标记？`,
      '撤销结清',
      { type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await settlementAdminApi.unmark(row.talentId, row.period)
    ElMessage.success('已撤销')
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

// ---------- 明细 ----------

async function openDetail(row: TalentStatement) {
  Object.assign(detail, {
    visible: true,
    talentId: row.talentId,
    talentNickname: row.talentNickname,
    loading: true,
    exporting: false,
    entries: [],
  })
  try {
    detail.entries = await settlementAdminApi.entries(period.value, row.talentId)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    detail.loading = false
  }
}

async function exportDetail() {
  detail.exporting = true
  try {
    await settlementAdminApi.exportEntries(period.value, detail.talentId)
    ElMessage.success('已导出')
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message)
    else throw e
  } finally {
    detail.exporting = false
  }
}

const reversedCount = computed(
  () => detail.entries.filter((e) => e.status === 'REVERSED').length,
)

// ---------- 展示 ----------

function asRow(row: unknown): TalentStatement {
  return row as TalentStatement
}

function asEntry(row: unknown): SettlementEntry {
  return row as SettlementEntry
}

function when(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function rateText(bp: number) {
  return `${(bp / 100).toFixed(2)}%`
}

function sourceLabel(type: 'ORDER' | 'TIP') {
  return type === 'ORDER' ? '服务订单' : '打赏'
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>结算报表</h2>
      <div class="head-actions">
        <el-date-picker
          v-model="period"
          type="month"
          value-format="YYYY-MM"
          format="YYYY 年 M 月"
          :clearable="false"
          placeholder="选择月份"
          @change="load"
        />
        <el-checkbox v-model="onlyActive">只看有流水的</el-checkbox>
        <el-button :loading="exporting" @click="exportReport">导出 CSV</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>这张表只算账，不动钱</template>
      「标记已结清」只是在系统里留个记号和时间，<strong>转账要你自己去做</strong>。
      已冲销的订单与打赏不计入合计，但在明细里仍然列出，便于纠纷追溯。
      券抵扣记在<strong>促销成本</strong>里由公会承担，不从主播应得里扣。
    </el-alert>

    <div class="tiles">
      <el-card shadow="never" class="tile">
        <div class="tile-label">主播应得合计</div>
        <div class="tile-value primary">{{ formatPrice(totals.talentCents) }}</div>
        <div class="tile-sub">这是本期要付出去的钱</div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">公会实得</div>
        <div class="tile-value">{{ formatPrice(guildNetCents) }}</div>
        <div class="tile-sub">
          分成 {{ formatPrice(totals.guildCents) }} − 促销成本
          {{ formatPrice(totals.promoCostCents) }}
        </div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">流水</div>
        <div class="tile-value">
          {{ formatPrice(totals.orderAmountCents + totals.tipAmountCents) }}
        </div>
        <div class="tile-sub">
          订单 {{ totals.orderCount }} 笔 · 打赏 {{ totals.tipCount }} 笔
        </div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">结清进度</div>
        <div class="tile-value">
          {{ totals.settled }} <span class="tile-unit">/ {{ rows.length }}</span>
        </div>
        <div class="tile-sub">
          <template v-if="owing">还有 {{ owing }} 位有钱没结</template>
          <template v-else>没有待结的主播</template>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-empty v-if="!loading && !shown.length" description="这个月没有数据" />
      <el-table v-else v-loading="loading" :data="shown" row-key="talentId">
        <el-table-column label="主播" min-width="140">
          <template #default="{ row }">
            <div>{{ asRow(row).talentNickname }}</div>
            <div class="sub">id {{ asRow(row).talentId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="服务订单" width="150">
          <template #default="{ row }">
            {{ formatPrice(asRow(row).orderAmountCents) }}
            <div class="sub">{{ asRow(row).orderCount }} 笔</div>
          </template>
        </el-table-column>
        <el-table-column label="打赏" width="150">
          <template #default="{ row }">
            {{ formatPrice(asRow(row).tipAmountCents) }}
            <div class="sub">{{ asRow(row).tipCount }} 笔</div>
          </template>
        </el-table-column>
        <el-table-column label="公会分成" width="130">
          <template #default="{ row }">{{ formatPrice(asRow(row).guildCents) }}</template>
        </el-table-column>
        <el-table-column label="促销成本" width="130">
          <template #default="{ row }">
            <span :class="{ cost: asRow(row).promoCostCents > 0 }">
              {{ formatPrice(asRow(row).promoCostCents) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="主播应得" width="140">
          <template #default="{ row }">
            <strong>{{ formatPrice(asRow(row).talentCents) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="结清" width="170">
          <template #default="{ row }">
            <template v-if="asRow(row).settled">
              <el-tag type="success" size="small">已结清</el-tag>
              <div class="sub">{{ when(asRow(row).settledAt) }}</div>
            </template>
            <el-tag v-else-if="asRow(row).talentCents > 0" type="warning" size="small">
              待结
            </el-tag>
            <span v-else class="sub">无需结算</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(asRow(row))">明细</el-button>
            <el-button
              v-if="!asRow(row).settled"
              link
              type="primary"
              :disabled="asRow(row).talentCents <= 0"
              @click="mark(asRow(row))"
            >
              标记结清
            </el-button>
            <el-button v-else link type="primary" @click="unmark(asRow(row))">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 明细 ---------- -->
    <el-drawer
      v-model="detail.visible"
      :title="`${detail.talentNickname} · ${period} 逐笔明细`"
      size="860px"
    >
      <div class="detail-head">
        <span class="sub">
          共 {{ detail.entries.length }} 笔<template v-if="reversedCount">
            ，其中 {{ reversedCount }} 笔已冲销（不计入合计）</template
          >
        </span>
        <el-button
          size="small"
          :loading="detail.exporting"
          :disabled="!detail.entries.length"
          @click="exportDetail"
        >
          导出明细 CSV
        </el-button>
      </div>

      <el-empty v-if="!detail.loading && !detail.entries.length" description="这个月没有分账记录" />
      <el-table v-else v-loading="detail.loading" :data="detail.entries" row-key="id">
        <el-table-column label="来源" width="170">
          <template #default="{ row }">
            <div>{{ sourceLabel(asEntry(row).sourceType) }}</div>
            <div class="mono sub">{{ asEntry(row).sourceRef }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" width="140">
          <template #default="{ row }">
            <span class="sub">{{ when(asEntry(row).occurredAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ formatPrice(asEntry(row).amountCents) }}</template>
        </el-table-column>
        <el-table-column label="分成" width="90">
          <template #default="{ row }">
            <span class="sub">{{ rateText(asEntry(row).commissionRateBp) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="公会 / 主播" width="170">
          <template #default="{ row }">
            {{ formatPrice(asEntry(row).guildCents) }} /
            <strong>{{ formatPrice(asEntry(row).talentCents) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="促销成本" width="110">
          <template #default="{ row }">
            <span v-if="asEntry(row).promoCostCents" class="cost">
              {{ formatPrice(asEntry(row).promoCostCents!) }}
            </span>
            <span v-else class="sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="160">
          <template #default="{ row }">
            <template v-if="asEntry(row).status === 'ACTIVE'">
              <el-tag type="success" size="small">有效</el-tag>
            </template>
            <template v-else>
              <el-tag type="info" size="small">已冲销</el-tag>
              <div class="sub">{{ asEntry(row).reverseReason || '—' }}</div>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 16px;
}
.head h2 {
  margin: 0;
  font-size: 18px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.tile-label {
  font-size: 13px;
  color: #909399;
}
.tile-value {
  font-size: 24px;
  font-weight: 600;
  margin: 6px 0 4px;
  font-variant-numeric: tabular-nums;
}
.tile-value.primary {
  color: #409eff;
}
.tile-unit {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
}
.tile-sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.cost {
  color: #e6a23c;
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
@media (max-width: 1100px) {
  .tiles {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
