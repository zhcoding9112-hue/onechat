<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { creditAdminApi, type AdminCreditAccount } from '@/admin/api/credit'
import { ApiError } from '@/api/http'

const queryUserId = ref<number | null>(null)
const account = ref<AdminCreditAccount | null>(null)
const loading = ref(false)

const form = reactive({ mode: 'grant' as 'grant' | 'adjust', amount: '', reason: '' })

async function load() {
  if (!queryUserId.value) {
    ElMessage.warning('请输入粉丝的用户 id')
    return
  }
  loading.value = true
  try {
    account.value = await creditAdminApi.account(queryUserId.value)
  } catch (e) {
    account.value = null
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

function parseAmount(): number | null {
  const text = form.amount.trim()
  // 元子是整数点数，没有小数概念
  if (!/^-?\d+$/.test(text)) return null
  const n = Number(text)
  return n === 0 ? null : n
}

async function submit() {
  if (!account.value) return
  const amount = parseAmount()
  if (amount === null) {
    ElMessage.warning('请输入非零整数')
    return
  }
  if (form.mode === 'grant' && amount < 0) {
    ElMessage.warning('发放只能为正数，扣减请用差错调整')
    return
  }
  // 原因必填由后端校验，这里不抢着判断，以免两处规则漂移
  const userId = account.value.userId
  try {
    if (form.mode === 'grant') {
      await creditAdminApi.grant(userId, amount, form.reason)
    } else {
      await creditAdminApi.adjust(userId, amount, form.reason)
    }
    ElMessage.success('已记账')
    form.amount = ''
    form.reason = ''
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

function typeLabel(type: string) {
  return { GRANT: '发放', ADJUST: '调整', DEDUCT: '消耗' }[type] ?? type
}

function signed(delta: number) {
  return delta > 0 ? `+${delta}` : String(delta)
}
</script>

<template>
  <div v-loading="loading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>元子只记账</template>
      元子目前只由运营手工发放或调整，粉丝端没有充值、兑换与提现入口。每笔变动都必须填写原因，
      流水只增不改——记错了请再记一笔反向调整，不要去改历史记录。
    </el-alert>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline @submit.prevent="load">
        <el-form-item label="粉丝用户 id">
          <el-input-number v-model="queryUserId" :min="1" :controls="false" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <template v-if="account">
      <el-card shadow="never" style="margin-bottom: 16px">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="用户 id">{{ account.userId }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ account.nickname }}</el-descriptions-item>
          <el-descriptions-item label="当前余额">
            <strong>{{ account.balance }}</strong>
          </el-descriptions-item>
        </el-descriptions>

        <el-form :model="form" label-width="90px" style="margin-top: 20px; max-width: 560px">
          <el-form-item label="操作">
            <el-radio-group v-model="form.mode">
              <el-radio value="grant">发放</el-radio>
              <el-radio value="adjust">差错调整</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="数量" required>
            <el-input
              v-model="form.amount"
              :placeholder="form.mode === 'grant' ? '正整数，如 50' : '可为负，如 -20'"
            />
          </el-form-item>
          <el-form-item label="原因" required>
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="例如：11 月活动奖励 / 订单 xxx 重复收款补偿"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submit">提交</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>流水（最近 50 条，倒序）</template>
        <el-table :data="account.transactions" empty-text="还没有流水">
          <el-table-column prop="createdAt" label="时间" width="200" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="变动" width="100">
            <template #default="{ row }">{{ signed(row.delta) }}</template>
          </el-table-column>
          <el-table-column prop="balanceAfter" label="变动后余额" width="120" />
          <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
          <el-table-column prop="operatorUserId" label="操作者" width="100" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>
