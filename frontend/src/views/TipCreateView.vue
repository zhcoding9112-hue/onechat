<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Skeleton, Field, CellGroup, Button, showToast } from 'vant'
import { talentApi, type TalentDetail } from '@/api/talent'
import { tipApi, type TipLimits } from '@/api/tip'
import { centsToYuan, formatPrice, yuanToCents } from '@/utils/money'
import { ApiError } from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const talentId = computed(() => Number(route.params.id))
const detail = ref<TalentDetail | null>(null)
const limits = ref<TipLimits | null>(null)
const loading = ref(true)
const submitting = ref(false)

const yuanInput = ref('')
const message = ref('')

/** 快捷金额，取值覆盖常见档位，最终仍受后台上下限约束。 */
const presets = [520, 1000, 2000, 5000, 8800, 18800]

const amountCents = computed(() => yuanToCents(yuanInput.value))

const rangeText = computed(() =>
  limits.value
    ? `${centsToYuan(limits.value.minCents)} ~ ${centsToYuan(limits.value.maxCents)} 元`
    : '',
)

onMounted(async () => {
  try {
    const [d, l] = await Promise.all([talentApi.detail(talentId.value), tipApi.limits()])
    detail.value = d
    limits.value = l
  } finally {
    loading.value = false
  }
})

function pick(cents: number) {
  yuanInput.value = centsToYuan(cents)
}

async function submit() {
  const cents = amountCents.value
  if (cents === null || cents <= 0) {
    showToast('请输入正确的金额，最多两位小数')
    return
  }
  if (limits.value && (cents < limits.value.minCents || cents > limits.value.maxCents)) {
    showToast(`打赏金额需在 ${rangeText.value}之间`)
    return
  }

  submitting.value = true
  try {
    const tip = await tipApi.create(talentId.value, cents, message.value.trim() || undefined)
    showToast('已创建打赏，请完成付款')
    await router.replace(`/tips/${tip.id}`)
  } catch (e) {
    if (!(e instanceof ApiError)) showToast('打赏失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NavBar title="打赏" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" :row="5" style="padding: 16px" />
  <EmptyState v-else-if="!detail" description="主播不存在" />
  <EmptyState
    v-else-if="!detail.available && detail.unavailableReason === '该主播暂不可约'"
    description="该主播暂不可打赏"
  />

  <template v-else>
    <section class="target">
      <p class="label">打赏给</p>
      <p class="name">{{ detail.nickname }}</p>
    </section>

    <div class="presets">
      <button
        v-for="p in presets"
        :key="p"
        type="button"
        class="preset"
        :class="{ active: amountCents === p }"
        @click="pick(p)"
      >
        {{ centsToYuan(p) }}
      </button>
    </div>

    <CellGroup inset title="自定义金额">
      <Field v-model="yuanInput" label="金额" :placeholder="`${rangeText}`" type="number">
        <template #button>元</template>
      </Field>
      <Field
        v-model="message"
        label="留言"
        type="textarea"
        rows="2"
        maxlength="200"
        show-word-limit
        placeholder="想对 TA 说的话（选填）"
      />
    </CellGroup>

    <p class="tips">留言只有 TA 本人和运营看得到，不会公开展示。</p>

    <div class="actions">
      <Button
        type="primary"
        block
        round
        :loading="submitting"
        :disabled="amountCents === null"
        @click="submit"
      >
        {{ amountCents === null ? '请输入金额' : `打赏 ${formatPrice(amountCents)}` }}
      </Button>
    </div>
  </template>
</template>

<style scoped>
.target {
  padding: 20px 16px 12px;
  background: #fff;
  text-align: center;
}

.label {
  margin: 0;
  font-size: 13px;
  color: #969799;
}

.name {
  margin: 6px 0 0;
  font-size: 18px;
  font-weight: 600;
}

.presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 16px;
}

.preset {
  padding: 14px 0;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  background: #fff;
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  cursor: pointer;
}

.preset.active {
  border-color: #3366ff;
  background: #f0f4ff;
  color: #3366ff;
}

.tips {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #969799;
}

.actions {
  padding: 16px;
}
</style>
