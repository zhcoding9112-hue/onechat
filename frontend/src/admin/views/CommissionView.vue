<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pricingApi, type CommissionSettings, type CommissionView } from '@/admin/api/pricing'
import { bpToPercent, percentToBp } from '@/utils/money'

const settings = ref<CommissionSettings | null>(null)
const loading = ref(false)

const defaultForm = reactive({ percent: '' })
const overrideForm = reactive({ visible: false, talentId: 1, percent: '' })

function asRate(row: unknown): CommissionView {
  return row as CommissionView
}

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    settings.value = await pricingApi.getCommission()
    defaultForm.percent = String(bpToPercent(settings.value.defaultConfig.rateBp))
  } finally {
    loading.value = false
  }
}

async function saveDefault() {
  const bp = percentToBp(defaultForm.percent)
  if (bp === null) {
    ElMessage.warning('请输入 0 到 100 之间的百分比，最多两位小数')
    return
  }
  await ElMessageBox.confirm(
    `将全局默认分成改为 ${defaultForm.percent}%。此改动只对之后创建的新订单生效，已创建的订单仍按其创建时的比例结算。`,
    '确认修改',
    { type: 'warning' },
  )
  await pricingApi.setDefaultRate(bp)
  ElMessage.success('已保存')
  await reload()
}

function openOverride(talentId?: number, rateBp?: number) {
  overrideForm.visible = true
  overrideForm.talentId = talentId ?? 1
  overrideForm.percent = rateBp === undefined ? '' : String(bpToPercent(rateBp))
}

async function saveOverride() {
  const bp = percentToBp(overrideForm.percent)
  if (bp === null) {
    ElMessage.warning('请输入 0 到 100 之间的百分比，最多两位小数')
    return
  }
  await pricingApi.setTalentRate(overrideForm.talentId, bp)
  overrideForm.visible = false
  ElMessage.success('已保存')
  await reload()
}

async function removeOverride(talentId: number) {
  await ElMessageBox.confirm(
    `删除后该主播将回落到全局默认比例。已创建的订单不受影响。`,
    '确认删除覆写',
    { type: 'warning' },
  )
  await pricingApi.removeTalentRate(talentId)
  ElMessage.success('已删除')
  await reload()
}
</script>

<template>
  <div v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="分成比例只对新订单生效"
      description="订单在创建瞬间就把当时的比例写进了订单自身。此处的任何改动都不会影响已创建订单的分账结果。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header><span class="card-title">全局默认分成</span></template>
      <el-form inline>
        <el-form-item label="公会分成">
          <el-input v-model="defaultForm.percent" style="width: 140px" placeholder="百分比">
            <template #append>%</template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveDefault">保存</el-button>
        </el-form-item>
      </el-form>
      <div class="hint" v-if="settings">
        当前值：{{ settings.defaultConfig.rateBp }} 基点（{{ settings.defaultConfig.rateText }}）。
        未单独配置的主播都按此比例计算。
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">按主播覆写</span>
          <el-button type="primary" size="small" @click="openOverride()">新增覆写</el-button>
        </div>
      </template>

      <el-table :data="settings?.overrides ?? []" size="small" empty-text="没有任何覆写，所有主播都用全局默认">
        <el-table-column prop="talentId" label="主播 ID" width="110" />
        <el-table-column prop="rateBp" label="基点" width="100" />
        <el-table-column prop="rateText" label="比例" width="100" />
        <el-table-column label="操作">
          <template #default="scope">
            <el-button link type="primary" size="small"
                       @click="openOverride(asRate(scope.row).talentId!, asRate(scope.row).rateBp)">
              修改
            </el-button>
            <el-button link type="danger" size="small" @click="removeOverride(asRate(scope.row).talentId!)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="overrideForm.visible" title="按主播设定分成" width="420px">
      <el-form label-width="90px">
        <el-form-item label="主播 ID">
          <el-input-number v-model="overrideForm.talentId" :min="1" />
        </el-form-item>
        <el-form-item label="公会分成">
          <el-input v-model="overrideForm.percent" placeholder="百分比">
            <template #append>%</template>
          </el-input>
          <div v-if="percentToBp(overrideForm.percent) !== null" class="hint">
            将保存为 {{ percentToBp(overrideForm.percent) }} 基点
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideForm.visible = false">取消</el-button>
        <el-button type="primary" @click="saveOverride">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-title {
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
