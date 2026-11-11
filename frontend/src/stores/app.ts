import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全站级轻状态。会话与用户信息在微信授权任务中接入。
 */
export const useAppStore = defineStore('app', () => {
  const booted = ref(false)

  function markBooted() {
    booted.value = true
  }

  return { booted, markBooted }
})
