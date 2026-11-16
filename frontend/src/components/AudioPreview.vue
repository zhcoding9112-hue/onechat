<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Icon } from 'vant'
import { release, takeOver } from '@/utils/audioRegistry'

const props = defineProps<{
  url: string
  seconds?: number | null
}>()

const playing = ref(false)
let audio: HTMLAudioElement | null = null

/*
 * 「同时只播一条」的登记放在 utils/audioRegistry 里，不能写在这里：
 * `<script setup>` 的顶层是 setup 函数体，每个实例都会重跑一遍，
 * 在此声明的变量是实例私有的，起不到全局互斥的作用。
 */

function toggle(event: Event) {
  // 卡片整体可点进详情，试听不该顺带跳转
  event.stopPropagation()
  event.preventDefault()

  if (!audio) {
    audio = new Audio(props.url)
    // 自然播完与出错都走 stop：只把 playing 置假会让「当前播放者」
    // 的登记继续指着自己，后续播放者要多停一次已经停下的音频
    audio.addEventListener('ended', stop)
    audio.addEventListener('error', stop)
  }
  if (playing.value) {
    stop()
    return
  }

  // 先停掉别人，再播自己
  takeOver(stop)
  const el = audio
  void el
    .play()
    .then(() => {
      playing.value = true
    })
    // 播放失败同样要撤销登记，否则下一个播放者会先去停一个根本没响的音频
    .catch(stop)
}

function stop() {
  audio?.pause()
  if (audio) {
    audio.currentTime = 0
  }
  playing.value = false
  release(stop)
}

onBeforeUnmount(() => {
  // 卸载时若正是自己在播，必须把登记一并清掉，
  // 否则下一个播放者会去 stop 一个已经不存在的组件
  stop()
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
