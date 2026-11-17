<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Skeleton, Loading, showToast, showConfirmDialog, showImagePreview } from 'vant'
import { feedApi, type CrushWallItem, type FeedPostView } from '@/api/feed'
import FanTabBar from '@/components/FanTabBar.vue'
import { ApiError } from '@/api/http'
import { EntrySource, talentPath, trackEntry } from '@/api/analytics'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const posts = ref<FeedPostView[]>([])
const wall = ref<CrushWallItem[]>([])
const loading = ref(true)
const failed = ref(false)
const page = ref(0)
const hasMore = ref(false)
const loadingMore = ref(false)

const PAGE_SIZE = 10

onMounted(async () => {
  // 心动墙单独 catch：它挂了不该把整个广场带下水
  void feedApi.crushWall().then((r) => (wall.value = r)).catch(() => (wall.value = []))
  try {
    const first = await feedApi.square(0, PAGE_SIZE)
    posts.value = first.items
    hasMore.value = first.hasMore
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = await feedApi.square(page.value + 1, PAGE_SIZE)
    page.value += 1
    posts.value = posts.value.concat(next.items)
    hasMore.value = next.hasMore
  } finally {
    loadingMore.value = false
  }
}

/** 计数以服务端返回的为准，前端不自己加减——自己加减迟早和服务端对不上。 */
async function toggleLike(post: FeedPostView) {
  try {
    const count = post.liked
      ? await feedApi.unlike(post.id)
      : await feedApi.like(post.id)
    post.liked = !post.liked
    post.likeCount = count
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function report(post: FeedPostView) {
  const { value } = await showConfirmDialog({
    title: '举报这条动态',
    message: '请简要说明问题，运营会尽快处理。',
  })
    .then(() => ({ value: '内容不适' }))
    .catch(() => ({ value: null }))
  if (value === null) return

  try {
    await feedApi.reportPost(post.id, value)
    showToast('已提交，感谢反馈')
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

/** 心动墙与下面的动态流是两个入口，分别归因。 */
function openWall(talentId: number) {
  trackEntry(EntrySource.CRUSH_WALL, talentId)
  router.push(talentPath(talentId, EntrySource.CRUSH_WALL))
}

function preview(images: string[], index: number) {
  showImagePreview({ images, startPosition: index })
}

function initial(nickname: string | null) {
  return (nickname ?? '').trim().charAt(0) || '·'
}

function timeText(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  return day < 30 ? `${day} 天前` : new Date(iso).toLocaleDateString()
}
</script>

<template>
  <div class="page">
    <NavBar title="动态广场" />

    <!-- 心动墙 -->
    <section v-if="wall.length" class="wall">
      <h3 class="wall-title">心动墙</h3>
      <div class="wall-scroll">
        <article
          v-for="w in wall"
          :key="w.talentId"
          class="wall-item"
          @click="openWall(w.talentId)"
        >
          <div class="wall-avatar">
            <img v-if="w.coverUrl" :src="w.coverUrl" alt="" />
            <span v-else class="fallback">{{ initial(w.nickname) }}</span>
            <i v-if="w.online" class="dot"></i>
          </div>
          <span class="wall-name">{{ w.nickname }}</span>
          <span v-if="w.promoTags.length" class="wall-tag">{{ w.promoTags[0] }}</span>
        </article>
      </div>
    </section>

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />
    <EmptyState v-else-if="failed" image="network" description="加载失败" />
    <EmptyState v-else-if="posts.length === 0" description="还没有动态" />

    <div v-else class="list">
      <article v-for="p in posts" :key="p.id" class="post">
        <header class="head" @click="router.push(talentPath(p.talentId, EntrySource.FEED))">
          <div class="avatar">
            <img v-if="p.coverUrl" :src="p.coverUrl" alt="" />
            <span v-else class="fallback">{{ initial(p.nickname) }}</span>
          </div>
          <div class="who">
            <span class="name">{{ p.nickname ?? '主播' }}</span>
            <span class="time">{{ timeText(p.createdAt) }}</span>
          </div>
        </header>

        <p v-if="p.content" class="content">{{ p.content }}</p>

        <div v-if="p.images.length" class="images" :class="`n${Math.min(p.images.length, 3)}`">
          <img
            v-for="(img, i) in p.images"
            :key="img"
            :src="img"
            alt=""
            @click="preview(p.images, i)"
          />
        </div>

        <footer class="actions">
          <button class="act" :class="{ on: p.liked }" @click="toggleLike(p)">
            ♥ {{ p.likeCount || '赞' }}
          </button>
          <button class="act" @click="router.push(`/feed/${p.id}`)">
            评论 {{ p.commentCount || '' }}
          </button>
          <button class="act report" @click="report(p)">举报</button>
        </footer>
      </article>

      <button v-if="hasMore" class="more" :disabled="loadingMore" @click="loadMore">
        <Loading v-if="loadingMore" size="16" />
        <template v-else>加载更多</template>
      </button>
      <p v-else class="end">没有更多了</p>
    </div>

    <FanTabBar />
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: 68px;
}

/* ---------- 心动墙 ---------- */
.wall {
  padding: var(--sp-4) 0 var(--sp-2);
}

.wall-title {
  margin: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

.wall-scroll {
  display: flex;
  gap: var(--sp-3);
  overflow-x: auto;
  padding: 0 var(--sp-4) var(--sp-2);
  scrollbar-width: none;
}

.wall-scroll::-webkit-scrollbar {
  display: none;
}

.wall-item {
  flex: none;
  width: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.wall-avatar {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  overflow: visible;
  background: var(--g-avatar);
  display: flex;
  align-items: center;
  justify-content: center;
}

.wall-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.dot {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-online);
  box-shadow: 0 0 0 2px var(--c-surface);
}

.wall-name,
.wall-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wall-name {
  font-size: var(--fs-sm);
  font-weight: 600;
}

.wall-tag {
  font-size: 11px;
  color: var(--c-text-3);
}

/* ---------- 广场 ---------- */
.list {
  margin: 0 var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.post {
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
  font-size: var(--fs-md);
  font-weight: 300;
  color: #fff;
}

.who {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
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
  gap: 4px;
  margin-top: var(--sp-3);
}

.images.n1 {
  grid-template-columns: minmax(0, 60%);
}

.images.n2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.images.n3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.images img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--r-md);
  cursor: pointer;
}

.images.n1 img {
  aspect-ratio: 4 / 3;
}

.actions {
  display: flex;
  gap: var(--sp-4);
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
}

.act {
  border: none;
  background: none;
  padding: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
}

.act.on {
  color: var(--c-price);
  font-weight: 600;
}

.act.report {
  margin-left: auto;
  color: var(--c-text-3);
}

.more {
  width: 100%;
  border: none;
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3);
  font-size: var(--fs-md);
  color: var(--c-text-2);
  cursor: pointer;
}

.end {
  margin: 0;
  padding: var(--sp-3);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
</style>
