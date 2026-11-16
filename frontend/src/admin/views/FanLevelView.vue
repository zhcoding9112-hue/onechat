<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fanLevelAdminApi, type FanLevelTier } from '@/admin/api/fanLevel'
import { ApiError } from '@/api/http'
import { centsToYuan, yuanToCents } from '@/utils/money'

const tiers = ref<FanLevelTier[]>([])
const loading = ref(false)
const edits = ref<Record<number, string>>({})

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    tiers.value = await fanLevelAdminApi.list()
    edits.value = Object.fromEntries(
      tiers.value.map((t) => [t.level, centsToYuan(t.thresholdCents)]),
    )
  } finally {
    loading.value = false
  }
}

function asTier(row: unknown): FanLevelTier {
  return row as FanLevelTier
}

async function save(tier: FanLevelTier) {
  const cents = yuanToCents(edits.value[tier.level] ?? '')
  if (cents === null) {
    ElMessage.warning('请填写金额，最多两位小数')
    return
  }
  // 「门槛必须递增」「最低档必须为 0」都由后端判定，这里不抢着判断
  try {
    await fanLevelAdminApi.update(tier.level, cents)
    ElMessage.success('已保存')
    await reload()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    await reload()
  }
}
</script>

<template>
  <div v-loading="loading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>等级只影响展示</template>
      粉丝等级按累计已确认消费（订单实付 + 已完成打赏）实时算出，不落库，所以改完门槛立刻生效，
      不需要重算。等级不参与计价与分成——不同等级的粉丝下同一单，金额与分成完全一致。
      门槛必须随等级递增，最低一档固定为 0。
    </el-alert>

    <el-card shadow="never">
      <template #header>粉丝等级门槛</template>
      <el-table :data="tiers" empty-text="未初始化">
        <el-table-column prop="level" label="等级" width="90" />
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column label="累计消费门槛（元）" min-width="240">
          <template #default="{ row }">
            <el-input
              v-model="edits[asTier(row).level]"
              :disabled="asTier(row).level === 1"
              style="max-width: 200px"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="asTier(row).level === 1"
              @click="save(asTier(row))"
            >
              保存
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
