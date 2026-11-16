<script setup lang="ts">
import { useRoute } from 'vue-router'
import { talentChatApi } from '@/api/chat'
import { useTalentChatStore } from '@/stores/chat'
import ChatThread from '@/components/ChatThread.vue'

const route = useRoute()
const chat = useTalentChatStore()

const conversationId = Number(route.params.id)
</script>

<template>
  <!-- 主播端没有「对方主页」可跳：会话那头是粉丝，粉丝没有公开主页 -->
  <ChatThread :conversation-id="conversationId" :api="talentChatApi" @read="chat.refresh()" />
</template>
