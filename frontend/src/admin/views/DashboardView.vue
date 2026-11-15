<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  analyticsApi,
  bpText,
  durationText,
  type Breakdown,
  type Overview,
} from '@/admin/api/analytics'

const overview = ref<Overview | null>(null)
const byTalent = ref<Breakdown[]>([])
const byService = ref<Breakdown[]>([])
const loading = ref(false)
const tab = ref('talent')

/** 默认最近 7 天。 */
const dateRange = ref<[string, string]>(defaultRange())

function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  return [fmt(start), fmt(end)]
}

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    const [from, to] = dateRange.value ?? []
    const [o, t, s] = await Promise.all([
      analyticsApi.funnel(from, to),
      analyticsApi.byTalent(from, to),
      analyticsApi.byService(from, to),
    ])
    overview.value = o
    byTalent.value = t
    byService.value = s
  } finally {
    loading.value = false
  }
}

/** 漏斗条的宽度相对首个非零节点。 */
const maxUv = computed(() => {
  const list = overview.value?.funnel ?? []
  return Math.max(1, ...list.map((s) => s.uv))
})

function widthOf(uv: number) {
  return `${Math.max(2, Math.round((uv / maxUv.value) * 100))}%`
}

async function saveThreshold(value: number) {
  await analyticsApi.updateThreshold(Math.round(value * 100))
  ElMessage.success('阈值已更新')
  await reload()
}

const thresholdPercent = ref(80)

function asBreakdown(row: unknown): Breakdown {
  return row as Breakdown
}
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="reload"
      />
      <el-button @click="reload">刷新</el-button>
    </div>

    <template v-if="overview">
      <!-- 接单率偏低时，转化率不可信，必须先说清楚 -->
      <el-alert
        v-if="overview.metrics.acceptRateBelowThreshold"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
        :title="`接单率 ${bpText(overview.metrics.acceptRateBp)}，低于阈值 ${bpText(overview.metrics.acceptRateThresholdBp)}`"
        description="接单率偏低会把「供给没跟上」误记成「产品没转化」，此期间的总转化率参考价值有限。请先排查主播接单情况。"
      />

      <el-row :gutter="16" class="metrics">
        <el-col :span="6">
          <el-card shadow="never">
            <div class="metric-label">访问到付费</div>
            <div class="metric-value" :class="{ muted: !overview.metrics.conversionTrustworthy }">
              {{ bpText(overview.metrics.visitToPayBp) }}
            </div>
            <div v-if="!overview.metrics.conversionTrustworthy" class="metric-note">
              接单率偏低，参考价值受限
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="metric-label">主播接单率</div>
            <div
              class="metric-value"
              :class="{ danger: overview.metrics.acceptRateBelowThreshold }"
            >
              {{ bpText(overview.metrics.acceptRateBp) }}
            </div>
            <div class="metric-note">阈值 {{ bpText(overview.metrics.acceptRateThresholdBp) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="metric-label">平均接单时长</div>
            <div class="metric-value">{{ durationText(overview.metrics.avgAcceptSeconds) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="metric-label">7 日复购率</div>
            <div class="metric-value">{{ bpText(overview.metrics.repurchaseBp) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="block">
        <template #header><span class="card-title">转化漏斗</span></template>
        <div v-for="step in overview.funnel" :key="step.eventKey" class="step">
          <span class="step-label">{{ step.label }}</span>
          <div class="step-bar-wrap">
            <div class="step-bar" :style="{ width: widthOf(step.uv) }" />
          </div>
          <span class="step-uv">{{ step.uv }}</span>
          <span class="step-conv">
            <template v-if="step.conversionBp !== null">
              {{ bpText(step.conversionBp) }}
              <span class="step-lost">流失 {{ step.lostUv }}</span>
            </template>
            <template v-else>—</template>
          </span>
        </div>
        <el-empty
          v-if="overview.funnel.every((s) => s.uv === 0)"
          description="所选时间范围内没有数据"
        />
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>
          <div class="card-header">
            <span class="card-title">拆分</span>
            <el-radio-group v-model="tab" size="small">
              <el-radio-button label="talent">按主播</el-radio-button>
              <el-radio-button label="service">按服务项目</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-table v-if="tab === 'talent'" :data="byTalent" size="small" empty-text="暂无数据">
          <el-table-column prop="name" label="主播" min-width="120" />
          <el-table-column label="进详情" width="90">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.VIEW_TALENT_DETAIL ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="提交订单" width="90">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.SUBMIT_ORDER ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="已付款" width="90">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.CONFIRM_PAYMENT ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="已完成" width="90">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.COMPLETE_ORDER ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="接单率" width="110">
            <template #default="scope">
              <span
                :class="{
                  danger:
                    asBreakdown(scope.row).acceptRateBp !== null &&
                    asBreakdown(scope.row).acceptRateBp! <
                      (overview?.metrics.acceptRateThresholdBp ?? 8000),
                }"
              >
                {{ bpText(asBreakdown(scope.row).acceptRateBp) }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <el-table v-else :data="byService" size="small" empty-text="暂无数据">
          <el-table-column prop="name" label="服务项目" min-width="140" />
          <el-table-column label="提交订单" width="100">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.SUBMIT_ORDER ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="已付款" width="100">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.CONFIRM_PAYMENT ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="已完成" width="100">
            <template #default="scope">
              {{ asBreakdown(scope.row).uvByKey?.COMPLETE_ORDER ?? 0 }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header><span class="card-title">告警阈值</span></template>
        <el-form inline>
          <el-form-item label="接单率低于">
            <el-input-number v-model="thresholdPercent" :min="0" :max="100" :precision="2" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveThreshold(thresholdPercent)">保存</el-button>
          </el-form-item>
        </el-form>
        <div class="hint">
          低于该比例时看板会标注异常，并提示总转化率参考价值受限。
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}

.metrics {
  margin-bottom: 16px;
}

.metric-label {
  font-size: 13px;
  color: #909399;
}

.metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.metric-value.danger {
  color: #f56c6c;
}

.metric-value.muted {
  color: #909399;
}

.metric-note {
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
}

.block {
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}

.step-label {
  width: 110px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}

.step-bar-wrap {
  flex: 1;
  background: #f5f7fa;
  border-radius: 4px;
  height: 18px;
}

.step-bar {
  height: 18px;
  border-radius: 4px;
  background: linear-gradient(90deg, #409eff, #79bbff);
}

.step-uv {
  width: 50px;
  text-align: right;
  font-weight: 600;
}

.step-conv {
  width: 150px;
  text-align: right;
  font-size: 12px;
  color: #909399;
}

.step-lost {
  margin-left: 6px;
  color: #c0c4cc;
}

.danger {
  color: #f56c6c;
  font-weight: 600;
}

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
