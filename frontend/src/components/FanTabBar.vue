<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'

/**
 * 粉丝端底部导航。
 *
 * 未读标记只做「有/无」的红点，不显示具体条数：导航上的数字会逼着人去清它，
 * 而真正的条数在会话列表里本来就有。
 *
 * 图标是现画的几何路径，不引第三方图标库，也不取竞品素材。
 */
const route = useRoute()
const router = useRouter()
const chat = useChatStore()

type Tab = { path: string; label: string; d: string; badge?: boolean }

const items: Tab[] = [
  { path: '/', label: '首页', d: 'M12 3 3 11h2.5v9h5v-6h3v6h5v-9H21z' },
  { path: '/feed', label: '动态', d: 'M4 4h16v11H9l-4 4v-4H4z' },
  { path: '/me/chats', label: '消息', d: 'M12 3c5 0 9 3.2 9 7.3s-4 7.3-9 7.3c-.9 0-1.8-.1-2.6-.3L5 20l.9-3.4C4.1 15.3 3 13.1 3 10.3 3 6.2 7 3 12 3z', badge: true },
  { path: '/me', label: '我的', d: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4.4 0-8 2.2-8 5v1h16v-1c0-2.8-3.6-5-8-5z' },
]

onMounted(() => void chat.refresh())
// 切换标签时重拉：刚看完一个会话退回来，红点应当随之消失
watch(() => route.path, () => void chat.refresh())

function go(path: string) {
  if (route.path !== path) void router.push(path)
}
</script>

<template>
  <nav class="tabbar">
    <button
      v-for="it in items"
      :key="it.path"
      class="tab"
      :class="{ on: route.path === it.path }"
      @click="go(it.path)"
    >
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path :d="it.d" fill="currentColor" />
      </svg>
      <span class="label">{{ it.label }}</span>
      <i v-if="it.badge && chat.unread > 0" class="badge" data-testid="chat-unread-dot"></i>
    </button>
  </nav>
</template>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
  padding-bottom: env(safe-area-inset-bottom);
}

.tab {
  position: relative;
  flex: 1;
  border: none;
  background: none;
  padding: 7px 0 5px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: var(--c-text-3);
  cursor: pointer;
}

.tab.on {
  color: var(--c-brand-deep);
}

.icon {
  width: 22px;
  height: 22px;
  display: block;
}

.label {
  font-size: 11px;
  line-height: 1;
}

.badge {
  position: absolute;
  top: 5px;
  left: calc(50% + 7px);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-price);
  box-shadow: 0 0 0 2px var(--c-surface);
}
</style>
