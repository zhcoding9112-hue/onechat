<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton, showToast, showImagePreview } from 'vant'
import { feedApi, type FeedCommentView, type FeedPostView } from '@/api/feed'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()

const postId = Number(route.params.id)
const post = ref<FeedPostView | null>(null)
const comments = ref<FeedCommentView[]>([])
const loading = ref(true)
const draft = ref('')
const sending = ref(false)

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    const page = await feedApi.square(0, 50)
    post.value = page.items.find((p) => p.id === postId) ?? null
    comments.value = await feedApi.comments(postId)
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text) {
    showToast('说点什么吧')
    return
  }
  sending.value = true
  try {
    const created = await feedApi.comment(postId, text)
    comments.value = comments.value.concat(created)
    draft.value = ''
    if (post.value) post.value.commentCount += 1
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    sending.value = false
  }
}

function preview(images: string[], index: number) {
  showImagePreview({ images, startPosition: index })
}

function initial(nickname: string | null) {
  return (nickname ?? '').trim().charAt(0) || '·'
}

function timeText(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}
</script>

<template>
  <div class="page">
    <NavBar title="动态" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />
    <Empty v-else-if="!post" description="动态不存在或已下架" />

    <template v-else>
      <article class="post">
        <header class="head" @click="router.push(`/talents/${post.talentId}`)">
          <div class="avatar">
            <img v-if="post.coverUrl" :src="post.coverUrl" alt="" />
            <span v-else class="fallback">{{ initial(post.nickname) }}</span>
          </div>
          <div class="who">
            <span class="name">{{ post.nickname ?? '主播' }}</span>
            <span class="time">{{ timeText(post.createdAt) }}</span>
          </div>
        </header>

        <p v-if="post.content" class="content">{{ post.content }}</p>

        <div v-if="post.images.length" class="images">
          <img
            v-for="(img, i) in post.images"
            :key="img"
            :src="img"
            alt=""
            @click="preview(post.images, i)"
          />
        </div>
      </article>

      <h3 class="sec-title">评论（{{ comments.length }}）</h3>
      <Empty v-if="comments.length === 0" description="还没有评论" />
      <div v-else class="comments">
        <div v-for="c in comments" :key="c.id" class="comment">
          <p class="c-text">{{ c.content }}</p>
          <span class="c-time">{{ timeText(c.createdAt) }}</span>
        </div>
      </div>

      <div class="composer">
        <input v-model="draft" maxlength="500" placeholder="说点什么…" @keyup.enter="send" />
        <button :disabled="sending" @click="send">{{ sending ? '发送中' : '发送' }}</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: 76px;
}

.post {
  margin: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  padding: var(--sp-4);
}

.head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  cursor: pointer;
}

.avatar {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
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
  color: #fff;
  font-weight: 300;
}

.who {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: var(--fs-md);
  font-weight: 600;
}

.time {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.content {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-md);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.images {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  margin-top: var(--sp-3);
}

.images img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--r-md);
  cursor: pointer;
}

.sec-title {
  margin: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

.comments {
  margin: 0 var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  overflow: hidden;
}

.comment {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.comment:last-child {
  border-bottom: none;
}

.c-text {
  margin: 0;
  font-size: var(--fs-md);
  line-height: 1.6;
  word-break: break-word;
}

.c-time {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.composer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.composer input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--c-border);
  border-radius: var(--r-pill);
  padding: 8px 14px;
  font-size: var(--fs-md);
  color: var(--c-text);
}

.composer button {
  flex: none;
  border: none;
  background: var(--c-brand);
  color: #fff;
  border-radius: var(--r-pill);
  padding: 8px 18px;
  font-size: var(--fs-md);
  font-weight: 600;
  cursor: pointer;
}
</style>
