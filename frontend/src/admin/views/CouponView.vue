<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { couponAdminApi, type AdminCoupon } from '@/admin/api/coupon'
import { ApiError } from '@/api/http'
import { centsToYuan, yuanToCents } from '@/utils/money'

const coupons = ref<AdminCoupon[]>([])
const loading = ref(false)
const dialog = ref(false)

const form = reactive({
  name: '',
  amount: '',
  threshold: '',
  range: [] as string[],
  /** 注册即发。默认关：开它等于决定每个新注册的人都送一张。 */
  autoGrant: false,
})

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    coupons.value = await couponAdminApi.list()
  } finally {
    loading.value = false
  }
}

function open() {
  form.name = ''
  form.amount = ''
  form.threshold = ''
  form.range = []
  form.autoGrant = false
  dialog.value = true
}

async function save() {
  const amountCents = yuanToCents(form.amount)
  const thresholdCents = yuanToCents(form.threshold)
  if (amountCents === null || thresholdCents === null) {
    ElMessage.warning('面额与门槛请填写金额，最多两位小数')
    return
  }
  if (form.range.length !== 2) {
    ElMessage.warning('请选择有效期')
    return
  }
  // 面额不得超过门槛由后端判定，这里不抢着判断，以免两处规则漂移
  try {
    await couponAdminApi.create({
      name: form.name.trim(),
      amountCents,
      thresholdCents,
      validFrom: form.range[0].replace(' ', 'T'),
      validTo: form.range[1].replace(' ', 'T'),
      autoGrantOnRegister: form.autoGrant,
    })
    dialog.value = false
    ElMessage.success('已创建')
    await reload()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function grantAll(row: AdminCoupon) {
  await ElMessageBox.confirm(
    `将「${row.name}」发放给全体粉丝。已持有该券的人会被跳过，不会收到第二张。`,
    '确认发放',
    { type: 'warning' },
  )
  const res = await couponAdminApi.grant(row.id)
  ElMessage.success(`新发出 ${res.granted} 张`)
}

async function toggleAutoGrant(row: AdminCoupon) {
  const next = !row.autoGrantOnRegister
  if (next) {
    await ElMessageBox.confirm(
      `打开后，此后**每一个**新注册的用户都会自动拿到「${row.name}」。` +
        '这是一笔持续支出，不是一次性发放。已经注册过的人不会被补发——那还得用「发放」。',
      '开启注册即发',
      { type: 'warning', confirmButtonText: '打开', cancelButtonText: '算了' },
    )
  }
  try {
    await couponAdminApi.setAutoGrant(row.id, next)
    ElMessage.success(next ? '已开启：此后注册的人自动拿到' : '已关闭')
    await reload()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

/** el-table 的插槽 row 是 DefaultRow，与 CommissionView 同样的转换。 */
function asCoupon(row: unknown): AdminCoupon {
  return row as AdminCoupon
}

function money(cents: number) {
  return `¥${centsToYuan(cents)}`
}
</script>

<template>
  <div v-loading="loading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>券的抵扣由公会承担</template>
      券只减少粉丝实付，不减少主播分成——分成始终按订单原价计算。抵扣额在结算报表中单列为「促销成本」。
      面额不得超过门槛，否则一张券就能把订单抵成负数。
    </el-alert>

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span>优惠券</span>
          <el-button type="primary" @click="open">新建券</el-button>
        </div>
      </template>

      <el-table :data="coupons" empty-text="还没有券">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="券名" min-width="160" />
        <el-table-column label="面额" width="110">
          <template #default="{ row }">{{ money(asCoupon(row).amountCents) }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="130">
          <template #default="{ row }">满 {{ money(asCoupon(row).thresholdCents) }}</template>
        </el-table-column>
        <el-table-column label="注册即发" width="160">
          <template #default="{ row }">
            <el-switch
              :model-value="asCoupon(row).autoGrantOnRegister"
              @update:model-value="() => toggleAutoGrant(asCoupon(row))"
            />
            <div v-if="asCoupon(row).autoGrantOnRegister" class="hint-inline">新用户自动拿到</div>
          </template>
        </el-table-column>
        <el-table-column prop="validFrom" label="生效" width="190" />
        <el-table-column prop="validTo" label="失效" width="190" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="grantAll(asCoupon(row))">发给全体</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建优惠券" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="券名" required>
          <el-input v-model="form.name" maxlength="64" placeholder="如：满 100 减 20" />
        </el-form-item>
        <el-form-item label="面额" required>
          <el-input v-model="form.amount" placeholder="元，如 20" />
        </el-form-item>
        <el-form-item label="使用门槛" required>
          <el-input v-model="form.threshold" placeholder="元，如 100。面额不得超过门槛" />
        </el-form-item>
        <el-form-item label="注册即发">
          <el-switch v-model="form.autoGrant" />
          <span class="hint-inline" style="margin-left: 8px">
            打开后，此后每个新注册的用户都自动拿到这张券
          </span>
        </el-form-item>
        <el-form-item label="有效期" required>
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint-inline {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
