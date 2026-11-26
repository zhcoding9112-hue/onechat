<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NavBar,
  Skeleton,
  Button,
  Uploader,
  Image as VanImage,
  showToast,
  type UploaderFileListItem,
} from 'vant';
import {
  paymentApi,
  type ManualPayload,
  type PaymentPage,
} from '@/api/payment';
import { formatPrice } from '@/utils/money';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();

const orderId = computed(() => Number(route.params.id));
const page = ref<PaymentPage | null>(null);
const loading = ref(true);
const uploading = ref(false);

/** 人工收款分支的数据。其他 kind 走各自分支，不复用这里。 */
const manual = computed<ManualPayload | null>(() => {
  const ins = page.value?.instruction;
  if (!ins || ins.kind !== 'DISPLAY_AND_UPLOAD') return null;
  return ins.payload as unknown as ManualPayload;
});

onMounted(load);

async function load() {
  try {
    page.value = await paymentApi.page(orderId.value);
  } finally {
    loading.value = false;
  }
}

async function onUpload(item: UploaderFileListItem | UploaderFileListItem[]) {
  const entry = Array.isArray(item) ? item[0] : item;
  const file = entry?.file;
  if (!file) return;

  uploading.value = true;
  try {
    await paymentApi.uploadProof(orderId.value, file);
    showToast('凭证已提交，等待核对');
    await load();
  } catch (e) {
    showToast(e instanceof Error ? e.message : '上传失败');
  } finally {
    uploading.value = false;
  }
}

function copy(text: string | null) {
  if (!text) return;
  void navigator.clipboard?.writeText(text).then(() => showToast('已复制'));
}
</script>

<template>
  <div class="page">
    <NavBar title="订单支付" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" :row="6" style="padding: 16px" />
    <EmptyState v-else-if="!page" description="订单不存在" />

    <template v-else>
      <!-- 金额放在最上面且最大：付多少、付给哪一单，是这页唯一要先说清的事 -->
      <section class="block amount">
        <p class="label">应付金额</p>
        <p class="value num">{{ formatPrice(page.payableCents) }}</p>
        <p class="ref num">订单号 {{ page.orderRef }}</p>
      </section>

      <p v-if="page.providers.length === 0" class="warn">
        当前暂无可用的支付方式，请联系客服
      </p>

      <template v-else-if="manual">
        <p v-if="!manual.ready" class="warn">
          收款信息尚未配置，请联系客服后再付款
        </p>

        <template v-else>
          <section v-if="manual.qrUrl" class="block qr">
            <VanImage
              :src="manual.qrUrl"
              width="180"
              height="180"
              fit="contain"
            />
            <p class="qr-tip">长按识别或保存收款码</p>
          </section>

          <section class="block info">
            <h3>收款信息</h3>
            <div v-if="manual.accountName" class="row">
              <span>户名</span><span>{{ manual.accountName }}</span>
            </div>
            <button
              v-if="manual.accountNo"
              class="row tap"
              @click="copy(manual.accountNo)"
            >
              <span>账号</span>
              <span class="copyable num"
                >{{ manual.accountNo }}<i>复制</i></span
              >
            </button>
            <div v-if="manual.bankName" class="row">
              <span>开户行</span><span>{{ manual.bankName }}</span>
            </div>
            <button class="row tap" @click="copy(page.orderRef)">
              <span>转账备注</span>
              <span class="copyable num">{{ page.orderRef }}<i>复制</i></span>
            </button>
          </section>

          <!--
            这句是整页最要紧的一句话，所以单独成块而不是混在灰色小字里：
            备注里没有订单号，到账就对不上订单，粉丝的钱会卡在那儿等人工排查。
          -->
          <p class="must">
            转账时请在备注里填上订单号，否则我们无法把到账和你的订单对上。
          </p>
          <p v-if="manual.tips" class="tips">{{ manual.tips }}</p>

          <section class="block info">
            <h3>支付凭证</h3>
            <Uploader
              :max-count="5"
              :after-read="onUpload"
              :disabled="uploading"
              accept="image/*"
            />
            <p class="tips">
              付款后请上传转账截图，运营核对到账后会通知主播接单。
            </p>
            <div v-for="p in page.proofs" :key="p.id" class="row">
              <span>已上传</span>
              <span class="num">{{
                p.uploadedAt?.replace('T', ' ').slice(0, 16)
              }}</span>
            </div>
          </section>
        </template>
      </template>

      <p v-else class="tips">该支付方式的前端尚未接入</p>

      <div class="actions">
        <Button block round @click="router.push(`/orders/${orderId}`)"
          >返回订单详情</Button
        >
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: var(--sp-8);
}

.block {
  margin: var(--sp-3) var(--sp-4) 0;
  padding: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
}

.block h3 {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-text-3);
}

.amount {
  text-align: center;
  padding: var(--sp-6) var(--sp-4);
}

.label {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.value {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-2xl);
  font-weight: 700;
  color: var(--c-price);
}

.ref {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.qr {
  text-align: center;
}

.qr-tip {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: 8px 0;
  font-size: var(--fs-md);
  text-align: left;
}

.row > span:first-child {
  flex: none;
  color: var(--c-text-2);
}

.row > span:last-child {
  text-align: right;
  word-break: break-all;
}

/* 整行可点，而不是只有那两个字可点——手机上小目标很难点中 */
.row.tap {
  border: none;
  background: none;
  cursor: pointer;
  font: inherit;
}

.copyable {
  color: var(--c-brand-deep);
}

.copyable i {
  margin-left: var(--sp-2);
  padding: 1px 6px;
  border-radius: var(--r-pill);
  background: var(--c-brand-soft);
  font-size: var(--fs-xs);
  font-style: normal;
}

.must {
  margin: var(--sp-3) var(--sp-4) 0;
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.warn {
  margin: var(--sp-3) var(--sp-4) 0;
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: var(--fs-md);
  line-height: 1.6;
}

/*
 * 页面级的说明文字与卡片内的说明文字共用这个类，但缩进不同：
 * 卡片自带内边距，页面级的得自己留出左右边距，否则会贴着屏幕边缘。
 */
.tips {
  margin: var(--sp-3) var(--sp-4) 0;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--c-text-3);
}

.block .tips {
  margin-left: 0;
  margin-right: 0;
}

.actions {
  padding: var(--sp-4);
}
.page {
  background: linear-gradient(#def9f3, #f5f7f8 300px);
}
.block {
  box-shadow: none;
  border-radius: 14px;
}
.money {
  background: transparent;
  box-shadow: none;
  text-align: center;
}
.amount {
  color: #f17f99;
}
.notice {
  font-size: 12px;
  line-height: 1.8;
}
</style>
