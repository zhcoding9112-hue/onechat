<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tabbar, TabbarItem } from 'vant'
import { useTalentChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const chat = useTalentChatStore()

const active = computed({
  get: () => route.path,
  set: (path: string) => {
    void router.push(path)
  },
})

onMounted(() => void chat.refresh())
// 看完一个会话退回来，红点应当随之消失
watch(() => route.path, () => void chat.refresh())
</script>

<template>
  <div class="wrap">
    <router-view />
  </div>
  <Tabbar v-model="active" route>
    <TabbarItem name="/talent" to="/talent" icon="orders-o">接单</TabbarItem>
    <TabbarItem name="/talent/auditions" to="/talent/auditions" icon="volume-o">试音</TabbarItem>
    <TabbarItem name="/talent/feed" to="/talent/feed" icon="photo-o">动态</TabbarItem>
    <TabbarItem
      name="/talent/chats"
      to="/talent/chats"
      icon="chat-o"
      :dot="chat.unread > 0"
    >
      消息
    </TabbarItem>
    <TabbarItem name="/talent/income" to="/talent/income" icon="balance-o">收入</TabbarItem>
    <TabbarItem name="/talent/me" to="/talent/me" icon="user-o">我的</TabbarItem>
  </Tabbar>
</template>

<style scoped>
.wrap {
  padding-bottom: 60px;
}
</style>
