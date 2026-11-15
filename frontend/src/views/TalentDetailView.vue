<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NavBar,
  Empty,
  Skeleton,
  Tag,
  Image as VanImage,
  Button,
  Cell,
  CellGroup,
  NoticeBar,
} from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { talentApi, type TalentDetail } from '@/api/talent'
import { formatPrice } from '@/utils/money'
import { ClientEvent, track } from '@/api/analytics'

const route = useRoute()
const router = useRouter()
const detail = ref<TalentDetail | null>(null)
const loading = ref(true)
const failed = ref(false)

const talentId = computed(() => Number(route.params.id))

function order(specId?: number) {
  track(ClientEvent.CLICK_ORDER, {
    talentId: talentId.value,
    serviceItemId: detail.value?.services[0]?.serviceItemId,
  })
  const query = specId === undefined ? undefined : { specId: String(specId) }
  router.push({ path: `/talents/${talentId.value}/order`, query })
}

onMounted(async () => {
  try {
    detail.value = await talentApi.detail(talentId.value)
    track(ClientEvent.VIEW_TALENT_DETAIL, {
      talentId: talentId.value,
      serviceItemId: detail.value.services[0]?.serviceItemId,
    })
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <NavBar title="主播详情" left-arrow @click-left="$router.back()" />

  <Skeleton v-if="loading" avatar :row="4" style="padding: 16px" />
  <Empty v-else-if="failed || !detail" image="network" description="加载失败" />

  <template v-else>
    <section class="profile">
      <VanImage
        :src="detail.coverUrl ?? '/images/avatar-placeholder.svg'"
        width="72"
        height="72"
        radius="10"
        fit="cover"
      />
      <div class="meta">
        <div class="line">
          <span class="name">{{ detail.nickname }}</span>
          <span class="status" :class="{ online: detail.online }">
            {{ detail.online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="tags">
          <Tag v-for="tag in detail.tags" :key="tag" plain type="primary" size="medium">
            {{ tag }}
          </Tag>
        </div>
        <AudioPreview v-if="detail.audioUrl" :url="detail.audioUrl" :seconds="detail.audioSeconds" />
      </div>
    </section>

    <p v-if="detail.intro" class="intro">{{ detail.intro }}</p>

    <NoticeBar
      v-if="!detail.available"
      :text="detail.unavailableReason ?? '该主播暂不可约'"
      color="#ed6a0c"
      background="#fffbe8"
      left-icon="info-o"
    />
    <!-- 没有可购服务但仍在架时，打赏依然可用 -->
    <div v-if="!detail.available && detail.unavailableReason === '该主播暂无可购服务'" class="actions">
      <Button block round @click="router.push(`/talents/${talentId}/tip`)">打赏 TA</Button>
    </div>
    <NoticeBar
      v-else-if="!detail.online"
      text="该主播当前离线，接单可能较慢"
      color="#969799"
      background="#f7f8fa"
      left-icon="clock-o"
    />

    <template v-if="detail.available">
      <CellGroup v-for="group in detail.services" :key="group.serviceItemId" inset :title="group.name">
        <Cell
          v-for="spec in group.specs"
          :key="spec.serviceSpecId"
          :title="spec.name"
          :label="spec.durationMinutes ? `${spec.durationMinutes} 分钟` : undefined"
          is-link
          @click="order(spec.serviceSpecId)"
        >
          <template #value>
            <span class="price">{{ formatPrice(spec.unitPriceCents) }}</span>
          </template>
        </Cell>
      </CellGroup>

      <div class="actions">
        <Button type="primary" block round @click="order()">选择服务下单</Button>
        <Button block round @click="router.push(`/talents/${talentId}/tip`)">打赏 TA</Button>
      </div>
    </template>
  </template>
</template>

<style scoped>
.profile {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fff;
}

.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  font-size: 17px;
  font-weight: 600;
}

.status {
  font-size: 12px;
  color: #969799;
}

.status.online {
  color: #07c160;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.intro {
  margin: 0;
  padding: 12px 16px;
  background: #fff;
  font-size: 13px;
  line-height: 1.7;
  color: #646566;
  white-space: pre-wrap;
}

.price {
  color: #ee0a24;
  font-weight: 600;
}

.actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
