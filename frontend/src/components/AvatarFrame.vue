<script setup lang="ts">
import { computed } from 'vue'

/**
 * 按等级升级的头像框。
 *
 * 和徽章同一套语言：一级没有框，往上依次是细环、银环、金环、暗金旋转环。
 * 框是 CSS 画的（conic-gradient + 伪元素），没有图片资源。
 */
const props = withDefaults(
  defineProps<{
    src: string
    level?: number | null
    size?: number
  }>(),
  { level: 1, size: 56 },
)

const tier = computed(() => Math.min(Math.max(Math.round(props.level ?? 1), 1), 5))
/** 框占的额外宽度，头像本身保持给定尺寸不被挤小。 */
const ring = computed(() => (tier.value <= 1 ? 0 : tier.value >= 4 ? 4 : 3))
const boxSize = computed(() => props.size + ring.value * 2)
</script>

<template>
  <span
    class="frame"
    :class="`t${tier}`"
    :style="{ width: boxSize + 'px', height: boxSize + 'px', padding: ring + 'px' }"
  >
    <img :src="src" :width="size" :height="size" alt="" />
  </span>
</template>

<style scoped>
.frame {
  position: relative;
  display: inline-flex;
  border-radius: 50%;
  flex: none;
}

.frame img {
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--c-border);
}

/* 一级：不加框，省得每个人一进来就顶着一圈装饰 */
.t1 {
  padding: 0 !important;
}

.t2 {
  background: #a9d2f5;
}

.t3 {
  background: conic-gradient(#e7ecf3, #b9c3d2, #f4f7fa, #c6cfdb, #e7ecf3);
}

.t4 {
  background: conic-gradient(#ffe9b0, #f0b945, #fff3cf, #e0a52c, #ffe9b0);
  box-shadow: 0 0 10px rgba(240, 185, 69, 0.4);
}

/* 最高级：同样的环，但转起来 */
.t5 {
  background: conic-gradient(#e8c37a, #6b5a39, #ffe6b0, #3a3326, #e8c37a);
  box-shadow: 0 0 12px rgba(232, 195, 122, 0.45);
  animation: spin 6s linear infinite;
}

/* 环在转，头像不能跟着转 */
.t5 img {
  animation: spin 6s linear infinite reverse;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .t5,
  .t5 img {
    animation: none;
  }
}
</style>
