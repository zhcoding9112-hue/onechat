<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Icon } from 'vant'

const props = defineProps<{
  url: string
  seconds?: number | null
}>()

const playing = ref(false)
let audio: HTMLAudioElement | null = null

function toggle(event: Event) {
  // 卡片整体可点进详情，试听不该顺带跳转
  event.stopPropagation()
  event.preventDefault()

  if (!audio) {
    audio = new Audio(props.url)
    audio.addEventListener('ended', () => (playing.value = false))
    audio.addEventListener('error', () => (playing.value = false))
  }
  if (playing.value) {
    audio.pause()
    playing.value = false
  } else {
    void audio
      .play()
      .then(() => (playing.value = true))
      .catch(() => (playing.value = false))
  }
}

onBeforeUnmount(() => {
  audio?.pause()
  audio = null
})
</script>

<template>
  <button class="audio" type="button" @click="toggle">
    <Icon :name="playing ? 'pause-circle-o' : 'play-circle-o'" size="16" />
    <span v-if="seconds">{{ seconds }}"</span>
    <span v-else>试听</span>
  </button>
</template>

<style scoped>
.audio {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: none;
  border-radius: 999px;
  background: #f0f4ff;
  color: #3366ff;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
}
</style>
