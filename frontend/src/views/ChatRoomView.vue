<script setup lang="ts">
import { useRoute } from 'vue-router'
import { chatApi, type ChatConversationView } from '@/api/chat'
import { useChatStore } from '@/stores/chat'
import ChatThread from '@/components/ChatThread.vue'
import { EntrySource, talentPath } from '@/api/analytics'

const route = useRoute()
const chat = useChatStore()

const conversationId = Number(route.params.id)

function peerLink(c: ChatConversationView) {
  return talentPath(c.talentId, EntrySource.CHAT)
}
</script>

<template>
  <ChatThread
    :conversation-id="conversationId"
    :api="chatApi"
    :peer-link="peerLink"
    @read="chat.refresh()"
  />
</template>
