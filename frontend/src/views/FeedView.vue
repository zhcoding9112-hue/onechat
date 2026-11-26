<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  Skeleton,
  Loading,
  showToast,
  showDialog,
  showConfirmDialog,
  showImagePreview,
} from 'vant';
import { feedApi, type FeedPostView } from '@/api/feed';
import FanTabBar from '@/components/FanTabBar.vue';
import { ApiError } from '@/api/http';
import { EntrySource, talentPath } from '@/api/analytics';
import Portrait from '@/components/Portrait.vue';
import { talentApi } from '@/api/talent';
import EmptyState from '@/components/EmptyState.vue';
import DemoFeedGallery from '@/components/DemoFeedGallery.vue';
import { previewTalents } from '@/preview/marketplace';
import {
  previewFeedPosts,
  previewFeedFollowing,
  togglePreviewFollow,
  togglePreviewLike,
  getPreviewPost,
  type DisplayFeedPost,
} from '@/preview/feed';

const router = useRouter();

const posts = ref<FeedPostView[]>([]);
const following = ref(false);
const favorites = ref<number[]>([]);
const busyLikes = ref(new Set<number>());
const busyFollows = ref(new Set<number>());
let generation = 0;
const loading = ref(true);
const failed = ref(false);
const page = ref(0);
const hasMore = ref(false);
const loadingMore = ref(false);

const PAGE_SIZE = 10;
const displayPosts = computed<DisplayFeedPost[]>(() => [
  ...(failed.value ? [] : posts.value),
  ...previewFeedPosts.filter(
    (p) => !following.value || previewFeedFollowing.value.includes(p.talentId),
  ),
]);
function isFollowed(id: number) {
  return id < 0
    ? previewFeedFollowing.value.includes(id)
    : favorites.value.includes(id);
}
function openAuthor(post: FeedPostView) {
  if (getPreviewPost(post.id)) {
    const talent = previewTalents.find((t) => t.id === post.talentId);
    void showDialog({
      title: `${post.nickname} · 模拟主播`,
      message: `${talent?.region ?? ''} · ${talent?.age ?? ''} 岁\n${talent?.tags.join(' · ') ?? ''}\n仅供浏览展示，不提供真实服务。`,
    });
    return;
  }
  void router.push(talentPath(post.talentId, EntrySource.FEED));
}

onMounted(() => {
  void reload();
  void talentApi
    .myFavorites()
    .then((items) => (favorites.value = items.map((i) => i.id)))
    .catch(() => {});
});
watch(following, reload);
async function reload() {
  const gen = ++generation;
  loading.value = true;
  failed.value = false;
  page.value = 0;
  loadingMore.value = false;
  try {
    const res = await feedApi.square(0, PAGE_SIZE, following.value);
    if (gen !== generation) return;
    posts.value = res.items;
    hasMore.value = res.hasMore;
  } catch {
    if (gen === generation) failed.value = true;
  } finally {
    if (gen === generation) loading.value = false;
  }
}
async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  const gen = generation;
  loadingMore.value = true;
  try {
    const next = await feedApi.square(
      page.value + 1,
      PAGE_SIZE,
      following.value,
    );
    if (gen !== generation) return;
    page.value++;
    posts.value.push(...next.items);
    hasMore.value = next.hasMore;
  } finally {
    if (gen === generation) loadingMore.value = false;
  }
}
async function follow(id: number) {
  if (id < 0) {
    togglePreviewFollow(id);
    return;
  }
  if (busyFollows.value.has(id)) return;
  busyFollows.value.add(id);
  try {
    if (favorites.value.includes(id)) {
      await talentApi.unfavorite(id);
      favorites.value = favorites.value.filter((x) => x !== id);
      if (following.value) void reload();
    } else {
      await talentApi.favorite(id);
      favorites.value.push(id);
    }
  } finally {
    busyFollows.value.delete(id);
  }
}

/** 计数以服务端返回的为准，前端不自己加减——自己加减迟早和服务端对不上。 */
async function toggleLike(post: FeedPostView) {
  if (getPreviewPost(post.id)) {
    togglePreviewLike(post);
    return;
  }
  if (busyLikes.value.has(post.id)) return;
  busyLikes.value.add(post.id);
  try {
    const count = post.liked
      ? await feedApi.unlike(post.id)
      : await feedApi.like(post.id);
    post.liked = !post.liked;
    post.likeCount = count;
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
  } finally {
    busyLikes.value.delete(post.id);
  }
}

async function report(post: FeedPostView) {
  if (getPreviewPost(post.id)) {
    showToast('这是模拟动态，不会提交真实举报');
    return;
  }
  const { value } = await showConfirmDialog({
    title: '举报这条动态',
    message: '请简要说明问题，运营会尽快处理。',
  })
    .then(() => ({ value: '内容不适' }))
    .catch(() => ({ value: null }));
  if (value === null) return;

  try {
    await feedApi.reportPost(post.id, value);
    showToast('已提交，感谢反馈');
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
  }
}

function preview(images: string[], index: number) {
  showImagePreview({ images, startPosition: index });
}

function timeText(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.floor(hour / 24);
  return day < 30 ? `${day} 天前` : new Date(iso).toLocaleDateString();
}
</script>
<template>
  <div class="square-page">
    <header class="square-header">
      <button :class="{ active: !following }" @click="following = false">
        推荐</button
      ><button :class="{ active: following }" @click="following = true">
        关注</button
      ><span>分享每一个心动瞬间</span>
    </header>
    <Skeleton v-if="loading" :row="5" />
    <div v-else-if="failed" class="empty">
      <EmptyState description="动态加载失败" /><button
        class="primary-btn"
        @click="reload"
      >
        重试
      </button>
    </div>
    <EmptyState
      v-else-if="!displayPosts.length"
      :description="following ? '关注的店员还没有动态' : '还没有动态'"
    />
    <article v-for="p in loading ? [] : displayPosts" :key="p.id" class="post">
      <header>
        <button class="author" @click="openAuthor(p)">
          <span class="avatar"
            ><Portrait :src="p.coverUrl" :seed="p.talentId" round /></span
          ><b>{{ p.nickname || '店员' }}</b
          ><small v-if="p.demo" class="demo-badge">模拟动态</small></button
        ><button
          class="follow"
          :class="{ followed: isFollowed(p.talentId) }"
          :disabled="busyFollows.has(p.talentId)"
          @click="follow(p.talentId)"
        >
          {{ isFollowed(p.talentId) ? '♥ 已关注' : '♡ 关注' }}
        </button>
      </header>
      <p
        v-if="p.content"
        class="post-text"
        @click="router.push(`/feed/${p.id}`)"
      >
        {{ p.content }}
      </p>
      <div
        v-if="p.images.length"
        class="images"
        :class="{ single: p.images.length === 1 }"
      >
        <img
          v-for="(img, i) in p.images"
          :key="img"
          :src="img"
          alt="动态配图"
          @click="preview(p.images, i)"
        />
      </div>
      <DemoFeedGallery v-if="p.demoTiles?.length" :tiles="p.demoTiles" />
      <footer>
        <time>{{ timeText(p.createdAt) }}</time
        ><button class="report" @click="report(p)">···</button
        ><button
          :class="{ liked: p.liked }"
          :disabled="busyLikes.has(p.id)"
          @click="toggleLike(p)"
        >
          {{ p.liked ? '♥' : '♡' }} {{ p.likeCount || '赞' }}</button
        ><button @click="router.push(`/feed/${p.id}`)">
          ♧ {{ p.commentCount || '评论' }}
        </button>
      </footer>
    </article>
    <button
      v-if="hasMore && !loading"
      class="more"
      :disabled="loadingMore"
      @click="loadMore"
    >
      <Loading v-if="loadingMore" size="16" /><span v-else>加载更多</span>
    </button>
    <p v-else-if="displayPosts.length && !loading" class="end">已经到底啦</p>
    <FanTabBar />
  </div>
</template>
<style scoped>
.demo-badge {
  flex: none;
  font-size: 9px;
  color: #a59bad;
  border: 1px solid #eee6f1;
  border-radius: 4px;
  padding: 2px 4px;
}
.square-page {
  min-height: 100dvh;
  background: radial-gradient(circle at 0 0, #fff4c8, transparent 45%),
    radial-gradient(circle at 100% 12%, #d8f6ff, transparent 45%),
    linear-gradient(#fce8f2, #f7f7f8 500px);
  padding: 16px 13px
    calc(var(--fan-tabbar-height) + 20px + env(safe-area-inset-bottom, 0px));
}
.square-header {
  display: flex;
  align-items: center;
  gap: 26px;
  padding: 13px 5px 22px;
}
.square-header button {
  position: relative;
  border: 0;
  background: none;
  padding: 0 0 9px;
  font-size: 20px;
  color: #888;
}
.square-header button.active {
  font-weight: 700;
  font-size: 24px;
  color: #333;
}
.square-header button.active:after {
  content: '';
  width: 23px;
  height: 4px;
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  border-radius: 50%;
  background: #18c8b5;
}
.square-header > span {
  font-size: 9px;
  margin-left: auto;
  color: #b5a9b3;
}
.post {
  padding: 16px;
  background: #fff;
  border-radius: 13px;
  margin-bottom: 12px;
}
.post header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.author {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: none;
  padding: 0;
  min-width: 0;
}
.avatar {
  width: 38px;
  height: 38px;
  flex: none;
}
.author b {
  font-size: 14px;
  font-weight: 500;
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.follow {
  border: 1px solid #e4f7f2;
  background: white;
  color: #41c3b2;
  padding: 5px 10px;
  border-radius: 20px;
  font-size: 10px;
  white-space: nowrap;
}
.follow.followed {
  background: #effaf7;
  color: #9dc7be;
}
.post-text {
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 14px 0;
  color: #666;
  cursor: pointer;
}
.images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  margin-top: 12px;
}
.images img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 7px;
  cursor: pointer;
}
.images.single {
  display: block;
}
.images.single img {
  width: 70%;
  max-height: 360px;
  aspect-ratio: auto;
  border-radius: 7px;
}
.post footer {
  display: flex;
  align-items: center;
  gap: 15px;
  color: #b6b9bf;
  padding-top: 14px;
}
.post time {
  font-size: 10px;
  flex: 1;
}
.post footer button {
  background: none;
  border: 0;
  color: #aaa;
  font-size: 12px;
  padding: 0;
}
.post footer .liked {
  color: #f097b9;
}
.post footer .report {
  font-size: 17px;
}
.more {
  display: block;
  margin: 20px auto;
  border: 0;
  color: #aaa;
  background: none;
  font-size: 12px;
}
.end {
  text-align: center;
  color: #b7b9c0;
  font-size: 10px;
  margin: 22px 0;
}
.empty {
  text-align: center;
}
</style>
