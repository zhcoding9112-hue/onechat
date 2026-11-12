<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Empty,
  Skeleton,
  Cell,
  CellGroup,
  Button,
  NoticeBar,
  Uploader,
  Image as VanImage,
  showToast,
  type UploaderFileListItem,
} from 'vant'
import { paymentApi, type ManualPayload, type PaymentPage } from '@/api/payment'
import { formatPrice } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const page = ref<PaymentPage | null>(null)
const loading = ref(true)
const uploading = ref(false)

/** 人工收款分支的数据。其他 kind 走各自分支，不复用这里。 */
const manual = computed<ManualPayload | null>(() => {
  const ins = page.value?.instruction
  if (!ins || ins.kind !== 'DISPLAY_AND_UPLOAD') return null
  return ins.payload as unknown as ManualPayload
})

onMounted(load)

async function load() {
  try {
    page.value = await paymentApi.page(orderId.value)
  } finally {
    loading.value = false
  }
}

async function onUpload(item: UploaderFileListItem | UploaderFileListItem[]) {
  const entry = Array.isArray(item) ? item[0] : item
  const file = entry?.file
  if (!file) return

  uploading.value = true
  try {
    await paymentApi.uploadProof(orderId.value, file)
    showToast('凭证已提交，等待核对')
    await load()
  } catch (e) {
    showToast(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function copy(text: string | null) {
  if (!text) return
  void navigator.clipboard?.writeText(text).then(() => showToast('已复制'))
}
</script>

<template>
  <NavBar title="订单支付" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" :row="6" style="padding: 16px" />
  <Empty v-else-if="!page" description="订单不存在" />

  <template v-else>
    <section class="amount">
      <p class="label">应付金额</p>
      <p class="value">{{ formatPrice(page.payableCents) }}</p>
      <p class="ref">订单号 {{ page.orderRef }}</p>
    </section>

    <NoticeBar
      v-if="page.providers.length === 0"
      text="当前暂无可用的支付方式，请联系客服"
      color="#ed6a0c"
      background="#fffbe8"
      left-icon="warning-o"
    />

    <template v-else-if="manual">
      <NoticeBar
        v-if="!manual.ready"
        text="收款信息尚未配置，请联系客服后再付款"
        color="#ed6a0c"
        background="#fffbe8"
        left-icon="warning-o"
      />

      <template v-else>
        <div v-if="manual.qrUrl" class="qr">
          <VanImage :src="manual.qrUrl" width="180" height="180" fit="contain" />
          <p class="qr-tip">长按识别或保存收款码</p>
        </div>

        <CellGroup inset title="收款信息">
          <Cell v-if="manual.accountName" title="户名" :value="manual.accountName" />
          <Cell v-if="manual.accountNo" title="账号" is-link @click="copy(manual.accountNo)">
            <template #value>
              <span class="copyable">{{ manual.accountNo }}</span>
            </template>
          </Cell>
          <Cell v-if="manual.bankName" title="开户行" :value="manual.bankName" />
          <Cell title="转账备注" is-link @click="copy(page.orderRef)">
            <template #value>
              <span class="copyable">{{ page.orderRef }}</span>
            </template>
          </Cell>
        </CellGroup>

        <p v-if="manual.tips" class="tips">{{ manual.tips }}</p>
        <p class="tips strong">转账时请在备注里填上订单号，否则我们无法把到账和你的订单对上。</p>

        <CellGroup inset title="支付凭证">
          <div class="uploader">
            <Uploader
              :max-count="5"
              :after-read="onUpload"
              :disabled="uploading"
              accept="image/*"
            />
            <p class="uploader-tip">付款后请上传转账截图，运营核对到账后会通知主播接单。</p>
          </div>
          <Cell
            v-for="p in page.proofs"
            :key="p.id"
            title="已上传"
            :value="p.uploadedAt?.replace('T', ' ').slice(0, 16)"
          />
        </CellGroup>
      </template>
    </template>

    <NoticeBar
      v-else
      text="该支付方式的前端尚未接入"
      color="#969799"
      background="#f7f8fa"
      left-icon="info-o"
    />

    <div class="actions">
      <Button block round @click="router.push(`/orders/${orderId}`)">返回订单详情</Button>
    </div>
  </template>
</template>

<style scoped>
.amount {
  padding: 24px 16px 16px;
  background: #fff;
  text-align: center;
}

.label {
  margin: 0;
  font-size: 13px;
  color: #969799;
}

.value {
  margin: 6px 0 0;
  font-size: 28px;
  font-weight: 600;
  color: #ee0a24;
}

.ref {
  margin: 8px 0 0;
  font-size: 12px;
  color: #c8c9cc;
}

.qr {
  padding: 16px;
  background: #fff;
  margin-top: 12px;
  text-align: center;
}

.qr-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #969799;
}

.copyable {
  color: #3366ff;
}

.tips {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #969799;
}

.tips.strong {
  color: #ed6a0c;
}

.uploader {
  padding: 14px 16px;
}

.uploader-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #969799;
}

.actions {
  padding: 16px;
}
</style>
