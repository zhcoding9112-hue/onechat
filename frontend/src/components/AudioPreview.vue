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
/* 色值一律取自设计令牌：主色一改，试听条跟着变。 */
.audio {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 3px 10px;
  border: none;
  border-radius: var(--r-pill);
  background: var(--c-brand);
  color: #fff;
  font-size: var(--fs-sm);
  line-height: 1.4;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

/* 叠在封面图上的角标用法：加一点投影保证压在浅色图片上仍可读。 */
.audio.on-cover {
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.25);
}
</style>
