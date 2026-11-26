<script setup lang="ts">
import { onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useChatStore } from '@/stores/chat';

/**
 * 粉丝端底部导航。
 *
 * 未读标记只做「有/无」的红点，不显示具体条数：导航上的数字会逼着人去清它，
 * 而真正的条数在会话列表里本来就有。
 *
 * 图标使用自制几何路径，与页面主色共用设计令牌。
 */
const route = useRoute();
const router = useRouter();
const chat = useChatStore();

type Tab = { path: string; label: string; d: string; badge?: boolean };

const items: Tab[] = [
  {
    path: '/wall',
    label: '心动墙',
    d: 'M12 21S3 15 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 7-9 13-9 13M4 11h4l2-4 4 8 2-4h4',
  },
  {
    path: '/match',
    label: '随机盲盒',
    d: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20M8 16l2-6 6-2-2 6z',
  },
  {
    path: '/',
    label: '选人下单',
    d: 'm3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
  },
  {
    path: '/me/chats',
    label: '在线聊天',
    d: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20M7 7h3v3H7zM14 7h3v3h-3zM7 14h3v3H7zM14 14h3v3h-3z',
    badge: true,
  },
  {
    path: '/feed',
    label: '动态广场',
    d: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M2 17c-3-4 17-15 20-10s-18 15-20 10',
  },
  {
    path: '/me',
    label: '个人中心',
    d: 'M12 21S3 15 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 7-9 13-9 13M4 11h4l2-4 4 8 2-4h4',
  },
];

onMounted(() => void chat.refresh());
// 切换标签时重拉：刚看完一个会话退回来，红点应当随之消失
watch(
  () => route.path,
  () => void chat.refresh(),
);

function go(path: string) {
  if (route.path !== path) void router.push(path);
}
</script>

<template>
  <!-- 独立于各页面滚动容器，避免祖先裁切或变换影响固定定位。 -->
  <Teleport to="body">
    <nav class="tabbar" aria-label="主导航">
      <button
        v-for="it in items"
        :key="it.path"
        class="tab"
        :class="{ on: route.path === it.path }"
        :aria-current="route.path === it.path ? 'page' : undefined"
        @click="go(it.path)"
      >
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
          <path
            :d="it.d"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linejoin="round"
            stroke-linecap="round"
          />
        </svg>
        <span class="label">{{ it.label }}</span>
        <i
          v-if="it.badge && chat.unread > 0"
          class="badge"
          data-testid="chat-unread-dot"
        ></i>
      </button>
    </nav>
  </Teleport>
</template>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  width: 100%;
  max-width: var(--app-max-width, 750px);
  margin: 0 auto;
  bottom: 0;
  z-index: 100;
  display: flex;
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
  padding-bottom: env(safe-area-inset-bottom, 0px);
  box-shadow: 0 -2px 10px rgba(31, 35, 40, 0.04);
}

.tab {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: var(--fan-tabbar-height, 60px);
  border: none;
  background: none;
  padding: 7px 0 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: var(--c-text-3);
  cursor: pointer;
}

.tab.on {
  color: var(--c-brand-deep);
}

.icon {
  width: 24px;
  height: 24px;
  display: block;
}

.label {
  font-size: clamp(10px, 2.8vw, 12px);
  line-height: 1.2;
  white-space: nowrap;
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
