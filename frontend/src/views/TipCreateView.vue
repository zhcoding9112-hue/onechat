<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NavBar, Skeleton, showToast } from 'vant';
import { talentApi, type TalentDetail } from '@/api/talent';
import { tipApi, type TipLimits } from '@/api/tip';
import { centsToYuan, formatPrice, yuanToCents } from '@/utils/money';
import { ApiError } from '@/api/http';
import Portrait from '@/components/Portrait.vue';
import GiftArt from '@/components/GiftArt.vue';
import GiftPreview from '@/components/GiftPreview.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();

const talentId = computed(() => Number(route.params.id));
const detail = ref<TalentDetail | null>(null);
const limits = ref<TipLimits | null>(null);
const loading = ref(true);
const submitting = ref(false);

const yuanInput = ref('');
const message = ref('');
const activeTab = ref('gift');
const selectedGift = ref(0);
const preview = ref(false);
const giftKinds = ['heart', 'rose', 'diamond', 'crown', 'castle', 'ring'];
const giftNames = [
  '怦然心动',
  '玫瑰花束',
  '璀璨星光',
  '为你加冕',
  '梦幻城堡',
  '心动约定',
];

/** 快捷金额，取值覆盖常见档位，最终仍受后台上下限约束。 */
const presets = [520, 1000, 2000, 5000, 8800, 18800];

const amountCents = computed(() => yuanToCents(yuanInput.value));

const rangeText = computed(() =>
  limits.value
    ? `${centsToYuan(limits.value.minCents)} ~ ${centsToYuan(limits.value.maxCents)} 元`
    : '',
);

onMounted(async () => {
  try {
    const [d, l] = await Promise.all([
      talentApi.detail(talentId.value),
      tipApi.limits(),
    ]);
    detail.value = d;
    limits.value = l;
    const first = presets.find((p) => p >= l.minCents && p <= l.maxCents);
    if (first !== undefined) {
      pick(first);
      selectedGift.value = presets.indexOf(first);
    }
  } finally {
    loading.value = false;
  }
});

function pick(cents: number) {
  yuanInput.value = centsToYuan(cents);
}

async function submit() {
  if (submitting.value) return;
  const cents = amountCents.value;
  if (cents === null || cents <= 0) {
    showToast('请输入正确的金额，最多两位小数');
    return;
  }
  if (
    limits.value &&
    (cents < limits.value.minCents || cents > limits.value.maxCents)
  ) {
    showToast(`打赏金额需在 ${rangeText.value}之间`);
    return;
  }

  submitting.value = true;
  try {
    const tip = await tipApi.create(
      talentId.value,
      cents,
      message.value.trim() || undefined,
    );
    showToast('已创建打赏，请完成付款');
    await router.replace(`/tips/${tip.id}`);
  } catch (e) {
    if (!(e instanceof ApiError)) showToast('打赏失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}
</script>
<template>
  <div class="gift-page">
    <NavBar title="礼物打赏" left-arrow @click-left="router.back()" /><Skeleton
      v-if="loading"
      :row="5"
    /><EmptyState
      v-else-if="!detail"
      description="店员资料加载失败"
    /><EmptyState
      v-else-if="
        !detail.available && detail.unavailableReason === '该主播暂不可约'
      "
      description="该店员暂不可打赏"
    /><template v-else
      ><header class="gift-recipient">
        <span><Portrait :src="detail.coverUrl" :seed="detail.id" round /></span>
        <div>
          <small>把心意送给</small>
          <h1>{{ detail.nickname }}</h1>
        </div>
        <span class="recipient-heart">♡</span>
      </header>
      <nav class="gift-tabs">
        <button
          :class="{ on: activeTab === 'gift' }"
          @click="activeTab = 'gift'"
        >
          赠礼</button
        ><button
          :class="{ on: activeTab === 'amount' }"
          @click="activeTab = 'amount'"
        >
          打赏
        </button>
      </nav>
      <template v-if="activeTab === 'gift'"
        ><div class="gift-grid">
          <button
            v-for="(p, i) in presets"
            :key="p"
            :class="{ selected: amountCents === p }"
            :disabled="!!limits && (p < limits.minCents || p > limits.maxCents)"
            @click="
              selectedGift = i;
              pick(p);
            "
          >
            <GiftArt :kind="giftKinds[i]" :size="65" /><b>{{ giftNames[i] }}</b
            ><span>¥{{ centsToYuan(p) }}</span
            ><i v-if="amountCents === p">✓</i>
          </button>
        </div>
        <button class="preview-btn" @click="preview = true">
          ✧ 预览礼物效果
        </button>
        <p class="gift-note">
          礼物为打赏效果预览，实际以确认的金额付款。
        </p></template
      >
      <div v-else class="amount-section">
        <h3>自定义打赏金额</h3>
        <div class="amount-input">
          <span>¥</span
          ><input
            v-model="yuanInput"
            type="number"
            :placeholder="rangeText"
            aria-label="打赏金额"
          />
        </div>
        <p>单次 {{ rangeText }}</p>
        <div class="amount-presets">
          <button
            v-for="p in presets"
            :key="p"
            :class="{ on: amountCents === p }"
            @click="pick(p)"
          >
            {{ centsToYuan(p) }} 元
          </button>
        </div>
      </div>
      <div class="gift-message">
        <label for="gift-message">心意留言</label
        ><textarea
          id="gift-message"
          v-model="message"
          maxlength="200"
          rows="3"
          placeholder="想对 TA 说的话（选填）"
        /><small>仅店员本人和运营可见</small>
      </div>
      <footer class="gift-footer">
        <div>
          <small>合计</small
          ><b>{{ amountCents === null ? '—' : formatPrice(amountCents) }}</b>
        </div>
        <button :disabled="submitting || amountCents === null" @click="submit">
          {{ submitting ? '提交中…' : '确认打赏' }}
        </button>
      </footer></template
    ><GiftPreview
      :show="preview"
      :kind="giftKinds[selectedGift]!"
      :name="giftNames[selectedGift]"
      @close="preview = false"
    />
  </div>
</template>
<style scoped>
.gift-page {
  min-height: 100dvh;
  background: radial-gradient(ellipse at 100% 5%, #293d63, transparent 60%),
    #202a43;
  color: #f1f1fa;
  padding: 0 18px 110px;
  --van-nav-bar-title-text-color: #fff;
  --van-nav-bar-icon-color: #fff;
}
.gift-page :deep(.van-nav-bar__arrow) {
  color: white !important;
}
.gift-recipient {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 5px 28px;
}
.gift-recipient > span:first-child {
  width: 56px;
  height: 56px;
  border: 2px solid #e1b5f0;
  border-radius: 50%;
  flex: none;
}
.gift-recipient small {
  font-size: 11px;
  color: #a4aac2;
}
.gift-recipient h1 {
  font-size: 20px;
  margin: 8px 0 0;
  font-weight: 500;
}
.recipient-heart {
  margin-left: auto;
  font-size: 45px;
  color: #e8bbd955;
}
.gift-tabs {
  display: flex;
  gap: 32px;
  margin: 3px 0 25px;
}
.gift-tabs button {
  background: none;
  border: 0;
  color: #a3adc8;
  padding: 8px 0;
  font-size: 18px;
}
.gift-tabs button.on {
  color: #fff;
  border-bottom: 3px solid #d9a1e5;
  font-weight: 600;
}
.gift-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 11px;
}
.gift-grid button {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
  padding: 17px 7px 13px;
  border: 1px solid #ffffff12;
  background: #ffffff06;
  border-radius: 11px;
  color: #ccd0e5;
}
.gift-grid button.selected {
  border-color: #d99fec;
  background: linear-gradient(#9a68c220, #d38fcf28);
  box-shadow: inset 0 0 15px #e6b1ff0a;
}
.gift-grid b {
  font-size: 11px;
  font-weight: 400;
  margin-top: 4px;
}
.gift-grid button > span:not(.gift-art) {
  font-size: 10px;
  color: #aeb3d0;
}
.gift-grid i {
  position: absolute;
  right: 0;
  bottom: 0;
  background: #d6a2e8;
  font-size: 10px;
  border-radius: 5px 0 9px 0;
  color: white;
  width: 19px;
  font-style: normal;
}
.preview-btn {
  display: block;
  border: 1px solid #d3afe44d;
  color: #dcbce8;
  background: none;
  border-radius: 18px;
  padding: 8px 20px;
  margin: 25px auto 12px;
  font-size: 11px;
}
.gift-note {
  text-align: center;
  color: #79839f;
  font-size: 9px;
  margin: 15px 0 30px;
}
.gift-message {
  margin-top: 25px;
  background: #ffffff07;
  padding: 16px;
  border: 1px solid #ffffff09;
  border-radius: 12px;
}
.gift-message label {
  font-size: 12px;
  color: #a8b1cc;
}
.gift-message textarea {
  display: block;
  background: none;
  color: #d6daef;
  border: 0;
  outline: 0;
  width: 100%;
  font-size: 12px;
  line-height: 1.8;
  padding: 15px 0 0;
  resize: none;
}
.gift-message textarea::placeholder {
  color: #69738e;
}
.gift-message small {
  display: block;
  text-align: right;
  font-size: 9px;
  color: #65728e;
}
.gift-footer {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, var(--app-max-width));
  z-index: 20;
  background: #263149;
  padding: 17px 22px calc(17px + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.gift-footer small {
  font-size: 11px;
  color: #a2acc4;
}
.gift-footer b {
  font-size: 23px;
  color: #f0c2df;
  font-weight: 500;
  margin-left: 13px;
}
.gift-footer button {
  border: 0;
  border-radius: 23px;
  background: linear-gradient(110deg, #c7a0f1, #edb2df);
  color: white;
  padding: 12px 30px;
  font-size: 14px;
}
.amount-section h3 {
  font-size: 14px;
  font-weight: 400;
}
.amount-input {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #ffffff20;
  gap: 10px;
  padding: 10px 0;
}
.amount-input > span {
  font-size: 25px;
  color: #e7c8e5;
}
.amount-input input {
  width: 100%;
  background: none;
  border: 0;
  color: white;
  outline: 0;
  font-size: 24px;
  padding: 8px;
}
.amount-input input::placeholder {
  font-size: 15px;
  color: #8793ad;
}
.amount-section > p {
  font-size: 10px;
  color: #8591ad;
}
.amount-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 20px;
}
.amount-presets button {
  border: 1px solid #ffffff20;
  color: #c7cde3;
  background: none;
  padding: 12px 0;
  border-radius: 8px;
  font-size: 12px;
}
.amount-presets .on {
  border-color: #d1a2e6;
  color: #edd2f4;
}
</style>
