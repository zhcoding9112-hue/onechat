<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton, Tag, Image as VanImage } from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { talentApi, type TalentCard } from '@/api/talent'

const router = useRouter()
const talents = ref<TalentCard[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    talents.value = await talentApi.list()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

function open(id: number) {
  router.push(`/talents/${id}`)
}
</script>

<template>
  <NavBar title="陪聊服务">
    <template #right>
      <span class="nav-link" @click="router.push('/me')">我的</span>
    </template>
  </NavBar>

  <div v-if="loading" class="list">
    <Skeleton v-for="i in 3" :key="i" avatar :row="2" class="skeleton" />
  </div>

  <Empty v-else-if="failed" image="network" description="加载失败，请下拉重试" />
  <Empty v-else-if="talents.length === 0" description="暂时还没有主播，稍后再来看看" />

  <div v-else class="list">
    <article v-for="t in talents" :key="t.id" class="card" @click="open(t.id)">
      <VanImage
        :src="t.coverUrl ?? '/images/avatar-placeholder.svg'"
        width="64"
        height="64"
        radius="8"
        fit="cover"
      />
      <div class="info">
        <div class="line">
          <span class="name">{{ t.nickname }}</span>
          <span class="status" :class="{ online: t.online }">
            {{ t.online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="tags">
          <Tag v-for="tag in t.tags" :key="tag" plain type="primary" size="medium">{{ tag }}</Tag>
          <span v-if="t.tags.length === 0" class="no-tag">—</span>
        </div>
        <AudioPreview v-if="t.audioUrl" :url="t.audioUrl" :seconds="t.audioSeconds" />
      </div>
    </article>
  </div>
</template>

<style scoped>
.nav-link {
  font-size: 14px;
  color: #3366ff;
}

.list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton {
  background: #fff;
  border-radius: 10px;
  padding: 14px 12px;
}

.card {
  display: flex;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.no-tag {
  font-size: 12px;
  color: #c8c9cc;
}
</style>
