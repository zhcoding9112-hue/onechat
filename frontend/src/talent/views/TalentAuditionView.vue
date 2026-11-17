<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Skeleton, showToast, showConfirmDialog } from 'vant'
import { auditionApi, type AuditionRequestView } from '@/api/audition'
import { ApiError } from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'

const requests = ref<AuditionRequestView[]>([])
const loading = ref(true)
const failed = ref(false)
/** 本次会话里已经应征过的，用于立刻把按钮置灰，不必重新拉列表。 */
const replied = ref<number[]>([])

onMounted(reload)

async function reload() {
  loading.value = true
  failed.value = false
  try {
    requests.value = await auditionApi.open()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function apply(req: AuditionRequestView) {
  const { value } = await showConfirmDialog({
    title: '应征这条试音需求',
    message: '你的试听片段会随应征一起展示给对方。可以附一句留言。',
  })
    .then(() => ({ value: '' }))
    .catch(() => ({ value: null }))
  if (value === null) return

  try {
    await auditionApi.reply(req.id)
    replied.value = replied.value.concat(req.id)
    showToast('已应征')
  } catch (e) {
    // 「已应征过」「需求已关闭」「未上传试听片段」都由后端判定，原话透出
    if (e instanceof ApiError) {
      showToast(e.message)
      if (e.message.includes('已关闭')) {
        await reload()
      }
    } else {
      throw e
    }
  }
}

function expiresText(iso: string) {
  const left = new Date(iso).getTime() - Date.now()
  if (left <= 0) return '已过期'
  const hours = Math.floor(left / 3600000)
  return hours >= 1 ? `${hours} 小时内有效` : '不到 1 小时'
}
</script>

<template>
  <div class="wrap">
    <p class="lead">粉丝发布的试音需求。应征时会带上你的试听片段，先把片段传好再来。</p>

    <div v-if="loading" class="list">
      <Skeleton v-for="i in 2" :key="i" :row="2" class="skeleton" />
    </div>
    <EmptyState v-else-if="failed" image="network" description="加载失败" />
    <EmptyState v-else-if="requests.length === 0" description="暂时没有开放中的试音需求" />

    <div v-else class="list">
      <article v-for="r in requests" :key="r.id" class="card">
        <div class="head">
          <span class="expire">{{ expiresText(r.expiresAt) }}</span>
          <span class="count">{{ r.replyCount }} 人已应征</span>
        </div>
        <p class="desc">{{ r.voiceDesc }}</p>
        <button
          class="apply"
          :class="{ done: replied.includes(r.id) }"
          :disabled="replied.includes(r.id)"
          @click="apply(r)"
        >
          {{ replied.includes(r.id) ? '已应征' : '应征' }}
        </button>
      </article>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  padding: var(--sp-4);
}

.lead {
  margin: 0 0 var(--sp-4);
  font-size: var(--fs-md);
  color: var(--c-text-2);
  line-height: 1.6;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.skeleton {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
}

.card {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  box-shadow: var(--sd-card);
}

.head {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.desc {
  margin: var(--sp-3) 0;
  font-size: var(--fs-lg);
  line-height: 1.6;
}

.apply {
  border: none;
  background: var(--c-brand);
  color: #fff;
  border-radius: var(--r-pill);
  padding: 8px 24px;
  font-size: var(--fs-md);
  font-weight: 600;
  cursor: pointer;
}

.apply.done {
  background: var(--c-offline);
}
</style>
