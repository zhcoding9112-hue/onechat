<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Skeleton, Stepper, Popup, showToast } from 'vant';
import Portrait from '@/components/Portrait.vue';
import { talentApi, type SpecOption, type TalentDetail } from '@/api/talent';
import { orderApi } from '@/api/order';
import { couponApi, type CouponItem } from '@/api/coupon';
import { ApiError } from '@/api/http';
import { centsToYuan, formatPrice } from '@/utils/money';
import {
  ENTRY_QUERY,
  entryFromRoute,
  type EntrySourceKey,
} from '@/api/analytics';
import EmptyState from '@/components/EmptyState.vue';

const props = defineProps<{
  embedded?: boolean;
  presetSpec?: number | null;
  talentId?: number;
  entrySource?: EntrySourceKey;
}>();
const emit = defineEmits<{ close: [] }>();
const route = useRoute();
const router = useRouter();

const talentId = computed(() => props.talentId ?? Number(route.params.id));

/** 入口来源：详情页用查询参数带过来；直接打开下单页的按直接访问记。 */
const entrySource =
  props.entrySource ?? entryFromRoute(route.query[ENTRY_QUERY]);
const detail = ref<TalentDetail | null>(null);
const loading = ref(true);
const submitting = ref(false);

const selectedSpecId = ref<number | null>(null);
const quantity = ref(1);
const contact = ref('');
const remark = ref('');
const contactType = ref('微信');
const selectedGroup = computed(
  () =>
    detail.value?.services.find((g) =>
      g.specs.some((s) => s.serviceSpecId === selectedSpecId.value),
    ) ?? detail.value?.services[0],
);
function close() {
  if (props.embedded) emit('close');
  else router.back();
}
let couponRequest = 0;

/** 把分组的档位摊平，下单时只需要选一个。 */
const allSpecs = computed<SpecOption[]>(() =>
  (detail.value?.services ?? []).flatMap((group) => group.specs),
);

const selectedSpec = computed(
  () =>
    allSpecs.value.find((s) => s.serviceSpecId === selectedSpecId.value) ??
    null,
);

/** 原价：未用券前的金额。分成以它为基数，所以它也是券门槛的判定基数。 */
const originalCents = computed(() =>
  selectedSpec.value ? selectedSpec.value.unitPriceCents * quantity.value : 0,
);

/** 选中档位所属的服务项目，用于取限定品类的券。 */
const selectedItemId = computed(() => {
  const group = (detail.value?.services ?? []).find((g) =>
    g.specs.some((s) => s.serviceSpecId === selectedSpecId.value),
  );
  return group?.serviceItemId ?? null;
});

const coupons = ref<CouponItem[]>([]);
const chosenCouponId = ref<number | null>(null);
const couponSheetOpen = ref(false);

const chosenCoupon = computed(
  () =>
    coupons.value.find((c) => c.userCouponId === chosenCouponId.value) ?? null,
);

const discountCents = computed(() => chosenCoupon.value?.amountCents ?? 0);
const payableCents = computed(() => originalCents.value - discountCents.value);

/**
 * 可用券由后端判定。
 *
 * 门槛、有效期、品类三项规则只写在后端一处——前端再算一遍，两处迟早会漂移，
 * 而漂移的表现是「前端让你选，提交时被拒」。
 */
async function reloadCoupons() {
  const request = ++couponRequest;
  if (!originalCents.value) {
    coupons.value = [];
    chosenCouponId.value = null;
    return;
  }
  try {
    const result = await couponApi.usable(
      originalCents.value,
      selectedItemId.value,
    );
    if (request !== couponRequest) return;
    coupons.value = result;
  } catch {
    if (request !== couponRequest) return;
    // 券加载失败不该挡住下单，退化成「这单不用券」
    coupons.value = [];
  }
  // 改了档位或数量后，原先选中的券可能已经不满足门槛
  if (
    chosenCouponId.value &&
    !coupons.value.some((c) => c.userCouponId === chosenCouponId.value)
  ) {
    chosenCouponId.value = null;
  }
}

watch([originalCents, selectedItemId], reloadCoupons);

onMounted(async () => {
  try {
    detail.value = await talentApi.detail(talentId.value);
    const preset = props.presetSpec ?? Number(route.query.specId);
    const candidate = allSpecs.value.find((s) => s.serviceSpecId === preset);
    selectedSpecId.value =
      candidate?.serviceSpecId ?? allSpecs.value[0]?.serviceSpecId ?? null;
    await reloadCoupons();
  } finally {
    loading.value = false;
  }
});

async function submit() {
  if (submitting.value) return;
  if (!selectedSpecId.value) {
    showToast('请选择服务档位');
    return;
  }
  if (!contact.value.trim()) {
    showToast(`请填写${contactType.value}号，主播需要据此联系你`);
    return;
  }

  submitting.value = true;
  try {
    const order = await orderApi.create({
      talentId: talentId.value,
      serviceSpecId: selectedSpecId.value,
      quantity: quantity.value,
      contact:
        contactType.value === 'QQ'
          ? `QQ：${contact.value.trim()}`
          : contact.value.trim(),
      remark: remark.value.trim() || undefined,
      userCouponId: chosenCouponId.value,
      // 来源一路从详情页带到这里，在下单那一刻快照进订单
      entrySource,
    });
    // 下单成功直接进入支付环节，少一次跳转
    await router.replace(`/orders/${order.id}/payment`);
  } catch (e) {
    // 请求层已弹出提示，这里不重复提示
    if (!(e instanceof ApiError)) showToast('下单失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}
</script>
<template>
  <div class="checkout-shell" :class="{ standalone: !embedded }">
    <div class="checkout-panel">
      <button class="sheet-close" aria-label="关闭下单" @click="close">×</button
      ><Skeleton v-if="loading" :row="5" /><EmptyState
        v-else-if="!detail"
        description="主播资料加载失败"
      /><EmptyState
        v-else-if="!detail.available"
        :description="detail.unavailableReason ?? '暂不可约'"
      /><template v-else
        ><header class="checkout-person">
          <div>
            <Portrait
              :src="detail.coverUrl"
              :seed="detail.id"
              :name="detail.nickname"
              round
            />
          </div>
          <h2>{{ detail.nickname }}</h2>
          <p>选择你的专属陪伴</p>
        </header>
        <form @submit.prevent="submit">
          <section class="checkout-section">
            <h3>服务类型</h3>
            <div class="chips-row">
              <button
                v-for="g in detail.services"
                :key="g.serviceItemId"
                type="button"
                class="choice-chip"
                :class="{ selected: selectedItemId === g.serviceItemId }"
                @click="selectedSpecId = g.specs[0]?.serviceSpecId ?? null"
              >
                {{ g.name }}
              </button>
            </div>
          </section>
          <section class="checkout-section">
            <h3>服务时长</h3>
            <div class="chips-row">
              <button
                v-for="s in selectedGroup?.specs"
                :key="s.serviceSpecId"
                type="button"
                class="choice-chip"
                :class="{ selected: selectedSpecId === s.serviceSpecId }"
                @click="selectedSpecId = s.serviceSpecId"
              >
                {{ s.name }}
              </button>
            </div>
          </section>
          <section class="contact-box">
            <div class="contact-types">
              <label
                ><input v-model="contactType" type="radio" value="微信" />
                微信号</label
              ><label
                ><input v-model="contactType" type="radio" value="QQ" /> QQ
                号</label
              >
            </div>
            <input
              v-model="contact"
              :placeholder="`请输入您的${contactType}号`"
              :aria-label="`${contactType}号`"
              maxlength="60"
            /><textarea
              v-model="remark"
              rows="1"
              maxlength="255"
              placeholder="备注：希望的时间、想聊的话题（选填）"
              aria-label="订单备注"
            />
          </section>
          <div class="checkout-row">
            <span>购买数量</span
            ><Stepper v-model="quantity" :min="1" :max="99" integer />
          </div>
          <button
            type="button"
            class="checkout-row coupon"
            @click="couponSheetOpen = true"
          >
            <span>优惠券</span
            ><span :class="{ discount: chosenCoupon }"
              >{{
                chosenCoupon
                  ? '-' + formatPrice(discountCents)
                  : coupons.length
                    ? coupons.length + ' 张可用'
                    : '暂无可用'
              }}
              ›</span
            >
          </button>
          <div class="checkout-row">
            <span>支付方式</span
            ><span class="payment-method">▣ 人工收款 <i>✓</i></span>
          </div>
          <p class="checkout-note">
            提交后按收款说明付款，客服确认到账后生效。
          </p>
          <footer class="checkout-footer">
            <div>
              <span>合计：</span><strong>{{ formatPrice(payableCents) }}</strong
              ><small v-if="discountCents"
                >原价 {{ formatPrice(originalCents) }}</small
              >
            </div>
            <button
              class="primary-btn"
              :disabled="submitting || !selectedSpecId"
              type="submit"
            >
              {{ submitting ? '提交中…' : '确认下单' }}
            </button>
          </footer>
        </form></template
      >
    </div>
    <Popup v-model:show="couponSheetOpen" position="bottom" round closeable
      ><div class="coupon-sheet">
        <h3>选择优惠券</h3>
        <p class="muted">单笔限用一张，按订单原价计算门槛</p>
        <button
          :class="{ on: chosenCouponId === null }"
          @click="
            chosenCouponId = null;
            couponSheetOpen = false;
          "
        >
          不使用优惠券</button
        ><button
          v-for="c in coupons"
          :key="c.userCouponId"
          :class="{ on: chosenCouponId === c.userCouponId }"
          @click="
            chosenCouponId = c.userCouponId;
            couponSheetOpen = false;
          "
        >
          <strong>-¥{{ centsToYuan(c.amountCents) }}</strong
          ><span
            >{{ c.name
            }}<small>满 {{ centsToYuan(c.thresholdCents) }} 元可用</small></span
          >
        </button>
        <p v-if="!coupons.length" class="muted">这笔订单暂无可用优惠券</p>
      </div></Popup
    >
  </div>
</template>
<style scoped>
.checkout-shell {
  background: linear-gradient(180deg, #fffdf1, white 35%);
  border-radius: 22px 22px 0 0;
  position: relative;
}
.checkout-shell.standalone {
  min-height: 100dvh;
  display: flex;
  align-items: flex-end;
  padding-top: 60px;
  background: linear-gradient(#b0bac399, #a6bfc899), var(--g-page);
}
.standalone .checkout-panel {
  width: 100%;
  background: linear-gradient(#fffdf1, white 35%);
  border-radius: 22px 22px 0 0;
  position: relative;
}
.checkout-panel {
  padding: 18px 20px calc(16px + env(safe-area-inset-bottom));
  max-height: 90dvh;
  overflow: auto;
}
.sheet-close {
  position: absolute;
  right: 15px;
  top: 13px;
  background: #eee9;
  border: 0;
  border-radius: 50%;
  font-size: 22px;
  color: #888;
  width: 27px;
  height: 27px;
  padding: 0;
  line-height: 26px;
}
.checkout-person {
  text-align: center;
  margin-bottom: 18px;
}
.checkout-person > div {
  width: 54px;
  height: 54px;
  margin: auto;
  box-shadow: 0 0 0 3px white;
  border-radius: 50%;
}
.checkout-person h2 {
  font-size: 15px;
  margin: 8px 0 0;
  font-weight: 500;
}
.checkout-person p {
  font-size: 10px;
  color: #aaa;
  margin: 5px 0;
}
.checkout-section {
  margin: 17px 0;
}
.checkout-section h3 {
  font-size: 13px;
  font-weight: 500;
  margin: 0 0 10px;
  color: #555;
}
.choice-chip {
  font-size: 12px;
  padding: 7px 12px;
  min-width: 65px;
}
.contact-box {
  border: 1px solid #e9eceb;
  border-radius: 10px;
  padding: 12px;
  margin-top: 20px;
}
.contact-types {
  display: flex;
  gap: 26px;
  font-size: 12px;
  color: #666;
}
.contact-types input {
  accent-color: var(--c-brand);
  vertical-align: middle;
}
.contact-box > input,
.contact-box > textarea {
  background: none;
  border: 0;
  outline: none;
  display: block;
  width: 100%;
  font-size: 12px;
  padding: 12px 0;
  color: #555;
}
.contact-box > textarea {
  border-top: 1px solid #f1f3f3;
  resize: vertical;
  padding-bottom: 0;
  font-size: 11px;
  min-height: 26px;
}
.checkout-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 17px 0;
  border-bottom: 1px solid #f3f4f4;
  font-size: 13px;
  width: 100%;
}
.coupon {
  background: none;
  border-left: 0;
  border-right: 0;
  border-top: 0;
  color: #555;
}
.coupon > span:last-child {
  font-size: 12px;
  color: #aaa;
}
.coupon .discount {
  color: #ff659c !important;
}
.payment-method {
  color: #666;
  font-size: 12px;
}
.payment-method i {
  font-style: normal;
  background: #10c6b6;
  color: white;
  border-radius: 50%;
  margin-left: 10px;
  padding: 1px 3px;
}
.checkout-note {
  font-size: 10px;
  color: #aaa;
  text-align: center;
  margin: 15px 0;
}
.checkout-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-top: 5px;
}
.checkout-footer > div > span {
  font-size: 12px;
  color: #666;
}
.checkout-footer strong {
  font-size: 21px;
  color: #fa7187;
  font-weight: 500;
}
.checkout-footer small {
  display: block;
  font-size: 10px;
  color: #aaa;
  text-decoration: line-through;
  margin-top: 3px;
}
.checkout-footer .primary-btn {
  min-width: 130px;
  white-space: nowrap;
}
.coupon-sheet {
  padding: 25px 20px;
  max-height: 70dvh;
  overflow: auto;
  text-align: center;
}
.coupon-sheet h3 {
  margin: 0;
}
.coupon-sheet > button {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 9px;
  width: 100%;
  padding: 17px;
  margin-top: 12px;
  text-align: left;
}
.coupon-sheet > button.on {
  background: #e7faf6;
  border-color: #0dbfb5;
}
.coupon-sheet strong {
  font-size: 23px;
  color: #f37f9b;
}
.coupon-sheet span {
  font-size: 13px;
}
.coupon-sheet small {
  display: block;
  font-size: 11px;
  color: #aaa;
  margin-top: 5px;
}
</style>
