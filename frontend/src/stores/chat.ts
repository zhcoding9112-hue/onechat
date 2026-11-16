import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatApi, talentChatApi } from '@/api/chat'

/**
 * 未读总数。底部导航的红点读它。
 *
 * 放在 store 而不是各页面自己拉：打开会话后未读归零，
 * 这件事发生在聊天页，而要变的是另一棵组件树里的导航红点。
 */
export const useChatStore = defineStore('chat', () => {
  const unread = ref(0)

  async function refresh() {
    try {
      unread.value = (await chatApi.unread({ toastOnError: false })).total
    } catch {
      // 未读数拿不到就按没有未读展示。为此弹一个错误提示没有意义，
      // 用户也无从处理——真正要紧的接口失败时会各自提示。
      unread.value = 0
    }
  }

  return { unread, refresh }
})

/** 主播侧的未读总数。与粉丝侧同一套语义，只是数据源不同。 */
export const useTalentChatStore = defineStore('talentChat', () => {
  const unread = ref(0)

  async function refresh() {
    try {
      unread.value = (await talentChatApi.unread({ toastOnError: false })).total
    } catch {
      unread.value = 0
    }
  }

  return { unread, refresh }
})
