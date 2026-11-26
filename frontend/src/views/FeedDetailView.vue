<script setup lang="ts">
import { onMounted, ref } from 'vue';
import Portrait from '@/components/Portrait.vue';
import { talentApi } from '@/api/talent';
import LevelBadge from '@/components/LevelBadge.vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NavBar,
  Skeleton,
  showToast,
  showImagePreview,
  showDialog,
} from 'vant';
import { feedApi, type FeedCommentView } from '@/api/feed';
import { ApiError } from '@/api/http';
import { EntrySource, talentPath } from '@/api/analytics';
import EmptyState from '@/components/EmptyState.vue';
import DemoFeedGallery from '@/components/DemoFeedGallery.vue';
import { previewTalents } from '@/preview/marketplace';
import {
  getPreviewPost,
  previewFeedComments,
  previewFeedFollowing,
  togglePreviewFollow,
  addPreviewComment,
  type DisplayFeedPost,
} from '@/preview/feed';

const route = useRoute();
const router = useRouter();

const postId = Number(route.params.id);
const post = ref<DisplayFeedPost | null>(null);
const comments = ref<FeedCommentView[]>([]);
const loading = ref(true);
const draft = ref('');
const sending = ref(false);
const favorited = ref(false);
const favoriting = ref(false);
async function toggleFavorite() {
  if (!post.value || favoriting.value) return;
  if (post.value.demo) {
    togglePreviewFollow(post.value.talentId);
    favorited.value = previewFeedFollowing.value.includes(post.value.talentId);
    return;
  }
  favoriting.value = true;
  try {
    if (favorited.value) await talentApi.unfavorite(post.value.talentId);
    else await talentApi.favorite(post.value.talentId);
    favorited.value = !favorited.value;
  } finally {
    favoriting.value = false;
  }
}

onMounted(reload);

async function reload() {
  loading.value = true;
  if (postId < 0) {
    post.value = getPreviewPost(postId) ?? null;
    comments.value = previewFeedComments[postId] ?? [];
    favorited.value =
      !!post.value && previewFeedFollowing.value.includes(post.value.talentId);
    loading.value = false;
    return;
  }
  try {
    post.value = await feedApi.detail(postId);
    void talentApi
      .myFavorites()
      .then(
        (items) =>
          (favorited.value = items.some((t) => t.id === post.value?.talentId)),
      )
      .catch(() => {});
    comments.value = await feedApi.comments(postId);
  } catch {
    post.value = null;
  } finally {
    loading.value = false;
  }
}

async function send() {
  if (sending.value) return;
  const text = draft.value.trim();
  if (!text) {
    showToast('说点什么吧');
    return;
  }
  if (post.value?.demo) {
    addPreviewComment(postId, text);
    comments.value = previewFeedComments[postId] ?? [];
    draft.value = '';
    return;
  }
  sending.value = true;
  try {
    const created = await feedApi.comment(postId, text);
    comments.value = comments.value.concat(created);
    draft.value = '';
    if (post.value) post.value.commentCount += 1;
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
  } finally {
    sending.value = false;
  }
}

function openAuthor() {
  if (!post.value) return;
  if (post.value.demo) {
    const talent = previewTalents.find((t) => t.id === post.value?.talentId);
    void showDialog({
      title: `${post.value.nickname} · 模拟主播`,
      message: `${talent?.region ?? ''} · ${talent?.age ?? ''} 岁\n${talent?.tags.join(' · ') ?? ''}\n仅供浏览展示，不提供真实服务。`,
    });
    return;
  }
  void router.push(talentPath(post.value.talentId, EntrySource.FEED));
}

function preview(images: string[], index: number) {
  showImagePreview({ images, startPosition: index });
}

function timeText(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
</script>

<template>
  <div class="page">
    <NavBar title="动态" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />
    <EmptyState v-else-if="!post" description="动态不存在或已下架" />

    <template v-else>
      <p v-if="post.demo" class="demo-notice">
        模拟动态 · 点赞、关注和评论仅保存在当前页面会话
      </p>
      <article class="post">
        <header class="head" @click="openAuthor">
          <div class="avatar">
            <Portrait :src="post.coverUrl" :seed="post.talentId" round />
          </div>
          <div class="who">
            <span class="name">{{ post.nickname ?? '主播' }}</span>
            <span class="time">{{ timeText(post.createdAt) }}</span>
          </div>
          <button
            class="detail-follow"
            :class="{ on: favorited }"
            :disabled="favoriting"
            @click.stop="toggleFavorite"
          >
            {{ favorited ? '★' : '☆' }}
          </button>
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
        <DemoFeedGallery
          v-if="post.demoTiles?.length"
          :tiles="post.demoTiles"
        />
      </article>

      <h3 class="sec-title">评论（{{ comments.length }}）</h3>
      <EmptyState v-if="comments.length === 0" description="还没有评论" />
      <div v-else class="comments">
        <div v-for="c in comments" :key="c.id" class="comment">
          <img class="c-avatar" :src="c.avatarUrl" alt="" />
          <div class="c-body">
            <div class="c-head">
              <span class="c-name">{{ c.nickname }}</span>
              <span v-if="c.author" class="c-author">作者</span>
              <LevelBadge
                v-else-if="c.level"
                :level="c.level"
                :name="c.levelName ?? ''"
              />
              <span class="c-time">{{ timeText(c.createdAt) }}</span>
            </div>
            <p class="c-text">{{ c.content }}</p>
          </div>
        </div>
      </div>

      <div class="composer">
        <input
          v-model="draft"
          maxlength="500"
          :placeholder="post.demo ? '写一条模拟评论…' : '说点什么…'"
          @keyup.enter="send"
        />
        <button :disabled="sending" @click="send">
          {{ post.demo ? '模拟发送' : sending ? '发送中' : '发送' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.demo-notice {
  margin: 0;
  padding: 10px 18px;
  font-size: 11px;
  color: #a18ca9;
  background: #faf6fc;
}
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
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.c-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--c-border);
  flex: none;
}

.c-body {
  flex: 1;
  min-width: 0;
}

/* 昵称、身份、时间一行。时间靠右，中间的徽章不被挤掉 */
.c-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: 2px;
}

.c-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 作者标记用主色，和粉丝等级那套金银区分开——它不是等级 */
.c-author {
  flex: none;
  padding: 1px 6px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--c-brand-deep);
  background: var(--c-brand-soft);
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
  margin-left: auto;
  flex: none;
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
.page {
  background: white;
}
.post {
  box-shadow: none;
  margin: 0;
  border-radius: 0;
  padding: 20px;
}
.images img {
  border-radius: 7px;
}
.detail-follow {
  border: 0;
  background: none;
  margin-left: auto;
  font-size: 23px;
  color: #bbb;
}
.detail-follow.on {
  color: #f5c862;
}
.composer {
  left: 50%;
  right: auto;
  transform: translateX(-50%);
  width: min(100%, var(--app-max-width));
}
.composer input {
  border-radius: 22px;
  background: #f5f5f5;
  border: 0;
}
.composer button {
  border-radius: 20px;
  background: #a976df;
}
.sec-title {
  font-size: 14px;
}
</style>
