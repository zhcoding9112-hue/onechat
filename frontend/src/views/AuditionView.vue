<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Skeleton, showToast, showConfirmDialog } from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { talentApi, type TalentCard } from '@/api/talent'
import {
  auditionApi,
  type AuditionReplyView,
  type AuditionRequestView,
} from '@/api/audition'
import { ApiError } from '@/api/http'
import { EntrySource, ENTRY_QUERY, talentPath } from '@/api/analytics'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const tab = ref<'voices' | 'request'>('voices')

const voices = ref<TalentCard[]>([])
const voicesLoading = ref(true)
const voicesFailed = ref(false)

const myRequest = ref<AuditionRequestView | null>(null)
const replies = ref<AuditionReplyView[]>([])
const requestLoading = ref(true)
const desc = ref('')
const publishing = ref(false)

onMounted(async () => {
  void loadVoices()
  void loadRequest()
})

async function loadVoices() {
  voicesLoading.value = true
  voicesFailed.value = false
  try {
    voices.value = await talentApi.voices()
  } catch {
    voicesFailed.value = true
  } finally {
    voicesLoading.value = false
  }
}

async function loadRequest() {
  requestLoading.value = true
  try {
    myRequest.value = await auditionApi.mine()
    replies.value = myRequest.value ? await auditionApi.replies(myRequest.value.id) : []
  } catch {
    myRequest.value = null
    replies.value = []
  } finally {
    requestLoading.value = false
  }
}

async function publish() {
  if (!desc.value.trim()) {
    showToast('说说你想要什么样的声音')
    return
  }
  publishing.value = true
  try {
    await auditionApi.publish(desc.value.trim())
    desc.value = ''
    showToast('已发布，等主播应征')
    await loadRequest()
  } catch (e) {
    // 「已有进行中的需求」这类业务拒绝，把后端原话透出来
    if (e instanceof ApiError) {
      showToast(e.message)
    } else {
      throw e
    }
  } finally {
    publishing.value = false
  }
}

async function close() {
  if (!myRequest.value) return
  await showConfirmDialog({
    title: '关闭试音需求',
    message: '关闭后主播不能再应征，已有的应征记录仍可查看。',
  })
  await auditionApi.close(myRequest.value.id)
  showToast('已关闭')
  await loadRequest()
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}

function expiresText(iso: string) {
  const left = new Date(iso).getTime() - Date.now()
  if (left <= 0) return '已过期'
  const hours = Math.floor(left / 3600000)
  return hours >= 1 ? `${hours} 小时后关闭` : '不到 1 小时后关闭'
}
</script>

<template>
  <div class="page">
    <NavBar title="试音选人" left-arrow @click-left="$router.back()" />

    <div class="tabs">
      <button :class="{ on: tab === 'voices' }" @click="tab = 'voices'">听声音挑人</button>
      <button :class="{ on: tab === 'request' }" @click="tab = 'request'">
        发布试音需求<i v-if="replies.length" class="badge">{{ replies.length }}</i>
      </button>
    </div>

    <!-- 听声音挑人 -->
    <template v-if="tab === 'voices'">
      <div v-if="voicesLoading" class="list">
        <Skeleton v-for="i in 3" :key="i" avatar :row="1" class="skeleton" />
      </div>
      <EmptyState v-else-if="voicesFailed" image="network" description="加载失败" />
      <EmptyState v-else-if="voices.length === 0" description="还没有主播上传试听片段" />
      <div v-else class="list">
        <article
          v-for="t in voices"
          :key="t.id"
          class="voice"
          @click="router.push(talentPath(t.id, EntrySource.AUDITION))"
        >
          <div class="avatar">
            <img v-if="t.coverUrl" :src="t.coverUrl" alt="" />
            <span v-else class="fallback">{{ initial(t.nickname) }}</span>
          </div>
          <div class="who">
            <span class="name">{{ t.nickname }}</span>
            <span v-if="t.promoTags.length" class="note">{{ t.promoTags[0] }}</span>
          </div>
          <AudioPreview v-if="t.audioUrl" :url="t.audioUrl" :seconds="t.audioSeconds" />
        </article>
      </div>
    </template>

    <!-- 发布试音需求 -->
    <template v-else>
      <Skeleton v-if="requestLoading" :row="3" style="padding: 16px" />

      <template v-else-if="myRequest">
        <section class="my-request">
          <div class="head">
            <span class="tag">进行中</span>
            <span class="expire">{{ expiresText(myRequest.expiresAt) }}</span>
          </div>
          <p class="desc">{{ myRequest.voiceDesc }}</p>
          <button class="close-btn" @click="close">关闭需求</button>
        </section>

        <h3 class="sec-title">应征的主播（{{ replies.length }}）</h3>
        <EmptyState v-if="replies.length === 0" description="还没有主播应征，再等等" />
        <div v-else class="list">
          <article v-for="r in replies" :key="r.replyId" class="reply">
            <div class="avatar">
              <img v-if="r.coverUrl" :src="r.coverUrl" alt="" />
              <span v-else class="fallback">{{ initial(r.nickname) }}</span>
            </div>
            <div class="reply-main">
              <div class="who">
                <span class="name">{{ r.nickname }}</span>
                <span v-if="r.online" class="online">在线</span>
              </div>
              <p v-if="r.message" class="msg">{{ r.message }}</p>
              <AudioPreview v-if="r.audioUrl" :url="r.audioUrl" :seconds="r.audioSeconds" />
            </div>
            <button class="order" @click.stop="router.push(`/talents/${r.talentId}/order?${ENTRY_QUERY}=${EntrySource.AUDITION}`)">
              下单
            </button>
          </article>
        </div>
      </template>

      <section v-else class="publish">
        <p class="hint">描述你想要的声音，主播看到后会带着自己的试听片段来应征。</p>
        <textarea
          v-model="desc"
          maxlength="200"
          rows="4"
          placeholder="例如：声音偏低、语速慢一点，适合睡前听"
        ></textarea>
        <div class="count">{{ desc.length }} / 200</div>
        <button class="big" :disabled="publishing" @click="publish">
          {{ publishing ? '发布中…' : '发布需求' }}
        </button>
        <p class="tip">同时只能有一条进行中的需求，24 小时后自动关闭。</p>
      </section>
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

.tabs {
  display: flex;
  gap: var(--sp-5);
  padding: var(--sp-2) var(--sp-4) var(--sp-4);
}

.tabs button {
  position: relative;
  border: none;
  background: none;
  padding: var(--sp-2) 0;
  font-size: var(--fs-lg);
  color: var(--c-text-2);
  cursor: pointer;
}

.tabs button.on {
  color: var(--c-text);
  font-weight: 700;
  font-size: var(--fs-xl);
}

.badge {
  position: absolute;
  top: 0;
  right: -14px;
  min-width: 16px;
  height: 16px;
  border-radius: var(--r-pill);
  background: var(--c-price);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  line-height: 16px;
  text-align: center;
  padding: 0 4px;
}

.list {
  padding: 0 var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.skeleton {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4) var(--sp-3);
}

/* ---------- 听声音挑人 ---------- */
.voice {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3) var(--sp-4);
  box-shadow: var(--sd-card);
  cursor: pointer;
}

.avatar {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  background: var(--g-avatar);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fallback {
  font-size: var(--fs-lg);
  font-weight: 300;
  color: #fff;
}

.who {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.note {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.online {
  font-size: var(--fs-sm);
  color: var(--c-online);
}

/* ---------- 我的需求 ---------- */
.my-request {
  margin: 0 var(--sp-4) var(--sp-5);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  box-shadow: var(--sd-card);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tag {
  font-size: var(--fs-sm);
  color: var(--c-brand-deep);
  background: var(--c-brand-soft);
  border-radius: var(--r-pill);
  padding: 3px 10px;
}

.expire {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.desc {
  margin: var(--sp-3) 0;
  font-size: var(--fs-lg);
  line-height: 1.6;
}

.close-btn {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--r-pill);
  padding: 6px 16px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
}

.sec-title {
  margin: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

.reply {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3) var(--sp-4);
  box-shadow: var(--sd-card);
}

.reply-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.reply-main .who {
  flex-direction: row;
  align-items: center;
  gap: var(--sp-2);
}

.msg {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--c-text-2);
}

.order {
  border: none;
  background: var(--c-brand);
  color: #fff;
  border-radius: var(--r-pill);
  padding: 7px 16px;
  font-size: var(--fs-sm);
  font-weight: 600;
  flex: none;
  cursor: pointer;
}

/* ---------- 发布 ---------- */
.publish {
  margin: 0 var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  box-shadow: var(--sd-card);
}

.hint {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-md);
  color: var(--c-text-2);
  line-height: 1.6;
}

textarea {
  width: 100%;
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--sp-3);
  font-size: var(--fs-md);
  font-family: inherit;
  resize: none;
  color: var(--c-text);
}

.count {
  text-align: right;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  margin: var(--sp-1) 0 var(--sp-3);
}

.big {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: var(--r-md);
  background: var(--c-brand);
  color: #fff;
  font-size: var(--fs-lg);
  font-weight: 600;
  cursor: pointer;
}

.tip {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  text-align: center;
}
</style>
