<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton } from 'vant'
import { talentChatApi, type ChatConversationView } from '@/api/chat'
import { useTalentChatStore } from '@/stores/chat'

const router = useRouter()
const chat = useTalentChatStore()

const items = ref<ChatConversationView[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    items.value = await talentChatApi.conversations()
    void chat.refresh()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

function initial(nickname: string | null) {
  return (nickname ?? '').trim().charAt(0) || '·'
}

function timeText(iso: string) {
  const at = new Date(iso)
  const now = new Date()
  if (at.toDateString() === now.toDateString()) {
    return `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`
  }
  return `${at.getMonth() + 1}/${at.getDate()}`
}
</script>

<template>
  <div class="page">
    <NavBar title="消息" />

    <Skeleton v-if="loading" :row="4" style="padding: 16px" />
    <Empty v-else-if="failed" image="network" description="加载失败" />
    <Empty v-else-if="items.length === 0" description="还没有粉丝来聊" />

    <div v-else class="list">
      <article
        v-for="c in items"
        :key="c.id"
        class="row"
        @click="router.push(`/talent/chats/${c.id}`)"
      >
        <div class="avatar">
          <img v-if="c.coverUrl" :src="c.coverUrl" alt="" />
          <span v-else class="fallback">{{ initial(c.nickname) }}</span>
          <i v-if="c.unread > 0" class="unread">{{ c.unread > 99 ? '99+' : c.unread }}</i>
        </div>
        <div class="main">
          <div class="top">
            <span class="name">{{ c.nickname ?? '粉丝' }}</span>
            <span class="time">{{ timeText(c.lastMessageAt) }}</span>
          </div>
          <p class="preview">{{ c.lastMessage ?? '还没有消息' }}</p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
}

.list {
  margin: var(--sp-3) var(--sp-4) 0;
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  cursor: pointer;
}

.row + .row {
  border-top: 1px solid var(--c-border);
}

.avatar {
  position: relative;
  width: 46px;
  height: 46px;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.fallback {
  font-size: var(--fs-md);
  font-weight: 300;
  color: #fff;
}

.unread {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 9px;
  background: var(--c-price);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 17px;
  text-align: center;
  box-shadow: 0 0 0 2px var(--c-surface);
}

.main {
  flex: 1;
  min-width: 0;
}

.top {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-md);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  flex: none;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.preview {
  margin: 2px 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
