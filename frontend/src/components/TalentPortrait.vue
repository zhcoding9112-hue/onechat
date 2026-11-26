<script setup lang="ts">
import Portrait from './Portrait.vue';
import LuckyAvatarFrame from './LuckyAvatarFrame.vue';
import type { AvatarFrame } from '@/types/avatar-frame';

defineProps<{
  src?: string | null;
  seed?: number;
  name?: string;
  frame?: AvatarFrame | null;
}>();

// 星光沿外圈散开，避开人物面部。
const sparkles = [
  [8, 28],
  [17, 12],
  [38, 3],
  [65, 5],
  [84, 18],
  [93, 40],
  [90, 65],
  [77, 84],
  [54, 94],
  [29, 88],
  [10, 70],
  [3, 49],
];
</script>

<template>
  <span
    class="talent-portrait"
    :class="{ decorated: !!frame, lucky: frame === 'lucky' }"
  >
    <span class="photo"><Portrait :src="src" :seed="seed" :name="name" /></span>
    <span v-if="frame === 'magic'" class="decoration" aria-hidden="true">
      <span class="frame-aura" />
      <span class="star-orbit"><span class="orbit-trail" /><i>✦</i></span>
      <span class="star-orbit orbit-reverse"
        ><span class="orbit-trail" /><i>✧</i></span
      >
      <span class="frame-sprite frame-body" />
      <span class="frame-sprite frame-charm" />
      <i
        v-for="(point, n) in sparkles"
        :key="n"
        class="glint"
        :style="{
          '--n': n,
          '--drift-x': `${(point[0]! - 50) * 0.16}px`,
          '--drift-y': `${(point[1]! - 50) * 0.2}px`,
          left: `${point[0]}%`,
          top: `${point[1]}%`,
        }"
        >✦</i
      >
    </span>
    <LuckyAvatarFrame v-else-if="frame === 'lucky'" />
  </span>
</template>

<style scoped>
.talent-portrait {
  display: block;
  width: 100%;
  height: 100%;
  position: relative;
  border-radius: inherit;
}
.photo {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  overflow: hidden;
}
.decorated .photo {
  position: absolute;
  inset: 9%;
  width: 82%;
  height: 82%;
  border-radius: 50%;
}
.lucky .photo {
  top: 12%;
  left: 10%;
  width: 80%;
  height: 80%;
}
.decoration {
  position: absolute;
  inset: -5%;
  pointer-events: none;
}
.frame-aura {
  position: absolute;
  inset: 6%;
  border-radius: 50%;
  box-shadow:
    0 0 7px 2px #c79cff70,
    inset 0 0 5px #f5c3ff90;
  animation: aura-breathe 3.2s ease-in-out infinite;
}
.star-orbit {
  position: absolute;
  inset: 4%;
  border-radius: 50%;
  animation: star-orbit 5.6s linear infinite;
}
.orbit-trail {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: conic-gradient(
    from 0deg,
    transparent 55%,
    #dd95ff80 78%,
    #fff2b9 99%,
    transparent
  );
  mask: radial-gradient(
    farthest-side,
    transparent calc(100% - 2px),
    #000 calc(100% - 1px)
  );
}
.star-orbit > i {
  position: absolute;
  top: -7px;
  left: calc(50% - 7px);
  color: #fff5b5;
  font-size: 17px;
  font-style: normal;
  line-height: 1;
  text-shadow:
    0 0 4px #fff,
    0 0 8px #e298ff;
}
.orbit-reverse {
  inset: 1%;
  animation-duration: 8.4s;
  animation-direction: reverse;
  animation-delay: -2.1s;
}
.orbit-reverse > i {
  color: #e8c8ff;
  font-size: 14px;
}
.orbit-reverse .orbit-trail {
  opacity: 0.6;
}
.frame-sprite {
  position: absolute;
  inset: 0;
  background: url('/images/peiyi-magic-frame-color.png') center / contain
    no-repeat;
  mask: url('/images/peiyi-magic-frame-mask.png') center / contain no-repeat;
  mask-mode: luminance;
}
/* 从竞品打印参考中提取的同一静帧分层显示；挂件位移和星光由本项目实现。 */
.frame-body {
  clip-path: polygon(0 0, 100% 0, 100% 56%, 66% 56%, 66% 100%, 0 100%);
  transform-origin: 50% 54%;
  animation: frame-sway 4.2s ease-in-out infinite;
}
.frame-charm {
  clip-path: polygon(66% 56%, 100% 56%, 100% 100%, 66% 100%);
  transform-origin: 80% 74%;
  animation: charm-float 2.8s ease-in-out infinite;
}
.glint {
  position: absolute;
  color: #fff9d9;
  font-size: 11px;
  font-style: normal;
  text-shadow:
    0 0 3px #fff,
    0 0 7px #f5cf78;
  animation: frame-glint 2.8s calc(var(--n) * -0.23s) ease-out infinite;
}
.glint:nth-of-type(3n) {
  color: #f9bcff;
}
@keyframes star-orbit {
  to {
    transform: rotate(360deg);
  }
}
@keyframes aura-breathe {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.96);
  }
  50% {
    opacity: 1;
    transform: scale(1.08);
  }
}
@keyframes frame-sway {
  0%,
  100% {
    transform: rotate(-3.5deg) scale(1);
  }
  50% {
    transform: rotate(3.5deg) scale(1.035);
  }
}
@keyframes charm-float {
  0%,
  100% {
    transform: translate(-1%, 3%) rotate(-10deg) scale(0.98);
  }
  50% {
    transform: translate(1%, -6%) rotate(10deg) scale(1.08);
  }
}
@keyframes frame-glint {
  0%,
  100% {
    opacity: 0;
    transform: translate(0, 0) scale(0.3) rotate(-25deg);
  }
  30% {
    opacity: 1;
    transform: translate(0, -2px) scale(1.4) rotate(20deg);
  }
  85% {
    opacity: 0;
    transform: translate(var(--drift-x), var(--drift-y)) scale(0.4)
      rotate(70deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .frame-body,
  .frame-aura,
  .star-orbit,
  .frame-charm,
  .glint {
    animation: none;
  }
  .glint {
    opacity: 0.5;
  }
  .star-orbit {
    display: none;
  }
}
</style>
