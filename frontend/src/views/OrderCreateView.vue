<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Empty,
  Skeleton,
  Form,
  Field,
  CellGroup,
  Stepper,
  RadioGroup,
  Radio,
  SubmitBar,
  showToast,
} from 'vant'
import { talentApi, type SpecOption, type TalentDetail } from '@/api/talent'
import { orderApi } from '@/api/order'
import { ApiError } from '@/api/http'
import { formatPrice } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const talentId = computed(() => Number(route.params.id))
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

const payableCents = computed(() =>
  selectedSpec.value ? selectedSpec.value.unitPriceCents * quantity.value : 0,
)

onMounted(async () => {
  try {
    detail.value = await talentApi.detail(talentId.value)
    const preset = Number(route.query.specId)
    const candidate = allSpecs.value.find((s) => s.serviceSpecId === preset)
    selectedSpecId.value = candidate?.serviceSpecId ?? allSpecs.value[0]?.serviceSpecId ?? null
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
  <Empty v-else-if="!detail" description="主播不存在" />
  <Empty v-else-if="!detail.available" :description="detail.unavailableReason ?? '该主播暂不可约'" />

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
            <label
              v-for="spec in group.specs"
              :key="spec.serviceSpecId"
              class="spec"
              :class="{ active: spec.serviceSpecId === selectedSpecId }"
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

.group {
  padding: 4px 0;
}

.group-name {
  margin: 0;
  padding: 10px 16px 4px;
  font-size: 12px;
  color: #969799;
}

.spec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f2f3f5;
}

.spec.active {
  background: #f7f9ff;
}

.spec-name {
  font-size: 14px;
}

.spec-duration {
  margin-left: 8px;
  font-size: 12px;
  color: #969799;
}

.spec-price {
  color: #ee0a24;
  font-weight: 600;
}

.stepper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 14px;
}

.notice {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #969799;
}
</style>
