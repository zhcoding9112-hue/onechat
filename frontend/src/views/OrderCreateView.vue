<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Skeleton, Form, Field, CellGroup, Stepper, RadioGroup, Radio, SubmitBar, Popup, showToast } from 'vant'
import { talentApi, type SpecOption, type TalentDetail } from '@/api/talent'
import { orderApi } from '@/api/order'
import { couponApi, type CouponItem } from '@/api/coupon'
import { ApiError } from '@/api/http'
import { centsToYuan, formatPrice } from '@/utils/money'
import { ENTRY_QUERY, entryFromRoute } from '@/api/analytics'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const talentId = computed(() => Number(route.params.id))

/** 入口来源：详情页用查询参数带过来；直接打开下单页的按直接访问记。 */
const entrySource = entryFromRoute(route.query[ENTRY_QUERY])
const detail = ref<TalentDetail | null>(null)
const loading = ref(true)
const submitting = ref(false)

const selectedSpecId = ref<number | null>(null)
const quantity = ref(1)
const contact = ref('')
const remark = ref('')

/** 把分组的档位摊平，下单时只需要选一个。 */
const allSpecs = computed<SpecOption[]>(() =>
  (detail.value?.services ?? []).flatMap((group) => group.specs),
)

const selectedSpec = computed(() =>
  allSpecs.value.find((s) => s.serviceSpecId === selectedSpecId.value) ?? null,
)

/** 原价：未用券前的金额。分成以它为基数，所以它也是券门槛的判定基数。 */
const originalCents = computed(() =>
  selectedSpec.value ? selectedSpec.value.unitPriceCents * quantity.value : 0,
)

/** 选中档位所属的服务项目，用于取限定品类的券。 */
const selectedItemId = computed(() => {
  const group = (detail.value?.services ?? []).find((g) =>
    g.specs.some((s) => s.serviceSpecId === selectedSpecId.value),
  )
  return group?.serviceItemId ?? null
})

const coupons = ref<CouponItem[]>([])
const chosenCouponId = ref<number | null>(null)
const couponSheetOpen = ref(false)

const chosenCoupon = computed(
  () => coupons.value.find((c) => c.userCouponId === chosenCouponId.value) ?? null,
)

const discountCents = computed(() => chosenCoupon.value?.amountCents ?? 0)
const payableCents = computed(() => originalCents.value - discountCents.value)

/**
 * 可用券由后端判定。
 *
 * 门槛、有效期、品类三项规则只写在后端一处——前端再算一遍，两处迟早会漂移，
 * 而漂移的表现是「前端让你选，提交时被拒」。
 */
async function reloadCoupons() {
  if (!originalCents.value) {
    coupons.value = []
    chosenCouponId.value = null
    return
  }
  try {
    coupons.value = await couponApi.usable(originalCents.value, selectedItemId.value)
  } catch {
    // 券加载失败不该挡住下单，退化成「这单不用券」
    coupons.value = []
  }
  // 改了档位或数量后，原先选中的券可能已经不满足门槛
  if (chosenCouponId.value && !coupons.value.some((c) => c.userCouponId === chosenCouponId.value)) {
    chosenCouponId.value = null
  }
}

watch([originalCents, selectedItemId], reloadCoupons)

onMounted(async () => {
  try {
    detail.value = await talentApi.detail(talentId.value)
    const preset = Number(route.query.specId)
    const candidate = allSpecs.value.find((s) => s.serviceSpecId === preset)
    selectedSpecId.value = candidate?.serviceSpecId ?? allSpecs.value[0]?.serviceSpecId ?? null
    await reloadCoupons()
  } finally {
    loading.value = false
  }
})

async function submit() {
  if (!selectedSpecId.value) {
    showToast('请选择服务档位')
    return
  }
  if (!contact.value.trim()) {
    showToast('请填写微信号，主播需要据此联系你')
    return
  }

  submitting.value = true
  try {
    const order = await orderApi.create({
      talentId: talentId.value,
      serviceSpecId: selectedSpecId.value,
      quantity: quantity.value,
      contact: contact.value.trim(),
      remark: remark.value.trim() || undefined,
      userCouponId: chosenCouponId.value,
      // 来源一路从详情页带到这里，在下单那一刻快照进订单
      entrySource,
    })
    // 下单成功直接进入支付环节，少一次跳转
    await router.replace(`/orders/${order.id}/payment`)
  } catch (e) {
    // 请求层已弹出提示，这里不重复提示
    if (!(e instanceof ApiError)) showToast('下单失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NavBar title="确认下单" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" :row="5" style="padding: 16px" />
  <EmptyState v-else-if="!detail" description="主播不存在" />
  <EmptyState v-else-if="!detail.available" :description="detail.unavailableReason ?? '该主播暂不可约'" />

  <template v-else>
    <Form class="form">
      <CellGroup inset title="选择服务">
        <RadioGroup v-model="selectedSpecId">
          <div
            v-for="group in detail.services"
            :key="group.serviceItemId"
            class="group"
          >
            <p class="group-name">{{ group.name }}</p>
            <!--
              整行可点：Vant 的 Radio 不渲染原生 input，外层 label 不会把点击转给它，
              结果是价格和两侧留白看着能点其实点不动。实测点 label 无效，点中间那个
              小圆圈才行——在手机上这个命中区域太小了。
            -->
            <label
              v-for="spec in group.specs"
              :key="spec.serviceSpecId"
              class="spec"
              :class="{ active: spec.serviceSpecId === selectedSpecId }"
              @click="selectedSpecId = spec.serviceSpecId"
            >
              <Radio :name="spec.serviceSpecId">
                <span class="spec-name">{{ spec.name }}</span>
                <span v-if="spec.durationMinutes" class="spec-duration">
                  {{ spec.durationMinutes }} 分钟
                </span>
              </Radio>
              <span class="spec-price">{{ formatPrice(spec.unitPriceCents) }}</span>
            </label>
          </div>
        </RadioGroup>
      </CellGroup>

      <CellGroup inset title="数量">
        <div class="stepper-row">
          <span>购买数量</span>
          <Stepper v-model="quantity" :min="1" :max="99" integer />
        </div>
      </CellGroup>

      <CellGroup inset title="优惠券">
        <div class="coupon-row" @click="couponSheetOpen = true">
          <span>优惠券</span>
          <span v-if="chosenCoupon" class="picked">
            -{{ formatPrice(chosenCoupon.amountCents) }}
          </span>
          <span v-else-if="coupons.length" class="hint">{{ coupons.length }} 张可用</span>
          <span v-else class="none">暂无可用</span>
        </div>
      </CellGroup>

      <CellGroup inset title="联系方式">
        <Field
          v-model="contact"
          label="微信号"
          placeholder="请填写你的微信号"
          maxlength="64"
          required
        />
        <Field
          v-model="remark"
          label="备注"
          type="textarea"
          rows="2"
          maxlength="255"
          show-word-limit
          placeholder="想聊的话题、希望的时间段等（选填）"
        />
      </CellGroup>

      <p class="notice">
        请填写正确的微信号。主播接单后会通过它联系你，服务在微信内进行。
      </p>
    </Form>

    <!-- 单笔限一张，所以这里是单选而不是多选 -->
    <Popup v-model:show="couponSheetOpen" position="bottom" round>
      <div class="sheet">
        <h3>选择优惠券</h3>
        <p class="sheet-tip">单笔订单只能使用一张。门槛按订单原价判定。</p>

        <button class="opt" :class="{ on: chosenCouponId === null }" @click="chosenCouponId = null; couponSheetOpen = false">
          <span class="opt-name">不使用优惠券</span>
        </button>

        <button
          v-for="c in coupons"
          :key="c.userCouponId"
          class="opt"
          :class="{ on: chosenCouponId === c.userCouponId }"
          @click="chosenCouponId = c.userCouponId; couponSheetOpen = false"
        >
          <span class="opt-amount">-¥{{ centsToYuan(c.amountCents) }}</span>
          <span class="opt-main">
            <span class="opt-name">{{ c.name }}</span>
            <span class="opt-sub">满 {{ centsToYuan(c.thresholdCents) }} 可用</span>
          </span>
        </button>

        <p v-if="!coupons.length" class="sheet-empty">这笔订单暂时没有可用的券</p>
      </div>
    </Popup>

    <SubmitBar
      :price="payableCents"
      :loading="submitting"
      :disabled="!selectedSpecId"
      button-text="提交订单"
      @submit="submit"
    >
      <template #tip>
        <span v-if="selectedSpec">
          {{ selectedSpec.name }} × {{ quantity }}
          <template v-if="discountCents">
            ｜原价 {{ formatPrice(originalCents) }}，已减 {{ formatPrice(discountCents) }}
          </template>
        </span>
        <span v-else>请先选择服务档位</span>
      </template>
    </SubmitBar>
  </template>
</template>

<style scoped>
.form {
  padding-bottom: 72px;
}

.coupon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: var(--fs-md);
  cursor: pointer;
}

.coupon-row .picked {
  color: var(--c-price);
  font-weight: 600;
}

.coupon-row .hint {
  color: var(--c-price);
}

.coupon-row .none {
  color: var(--c-text-3);
}

.sheet {
  padding: 20px 16px 28px;
}

.sheet h3 {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 700;
  text-align: center;
}

.sheet-tip {
  margin: 8px 0 16px;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  text-align: center;
}

.sheet-empty {
  margin: 16px 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  text-align: center;
}

.opt {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--r-md);
  padding: 12px 14px;
  margin-bottom: 10px;
  text-align: left;
  cursor: pointer;
}

.opt.on {
  border-color: var(--c-brand);
  background: var(--c-brand-soft);
}

.opt-amount {
  flex: none;
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--c-price);
}

.opt-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.opt-name {
  font-size: var(--fs-md);
}

.opt-sub {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.group {
  padding: 4px 0;
}

.group-name {
  margin: 0;
  padding: 10px 16px 4px;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.spec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);
}

.spec.active {
  background: var(--c-brand-soft);
}

.spec-name {
  font-size: var(--fs-md);
}

.spec-duration {
  margin-left: 8px;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.spec-price {
  color: var(--c-price);
  font-weight: 600;
}

.stepper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: var(--fs-md);
}

.notice {
  margin: 12px 16px 0;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--c-text-3);
}
</style>
