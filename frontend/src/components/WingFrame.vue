<script setup lang="ts">
import Portrait from './Portrait.vue';
withDefaults(
  defineProps<{
    src?: string | null;
    seed?: number;
    name?: string;
    size?: number;
    tone?: 'gold' | 'blue' | 'pink';
  }>(),
  { size: 66, tone: 'gold' },
);
</script>
<template>
  <span
    class="wing-frame"
    :class="tone"
    :style="{ '--frame-size': size + 'px' }"
  >
    <svg
      v-for="side in ['left', 'right']"
      :key="side"
      class="wing"
      :class="side"
      viewBox="0 0 72 96"
      aria-hidden="true"
    >
      <path
        d="M67 88C29 82 5 48 5 3C22 26 43 25 59 47C39 36 27 32 17 23C27 49 43 48 62 61C43 54 30 49 23 42C32 66 49 66 65 74C48 71 41 68 35 64C41 80 58 81 67 88Z"
        fill="currentColor"
        stroke="#bddbff"
        stroke-width="1.2"
      />
    </svg>
    <span class="jewel top">◆</span
    ><span class="frame-ring"
      ><Portrait :src="src" :seed="seed" :name="name" round
    /></span>
    <span class="jewel bottom">✦</span
    ><i v-for="n in 5" :key="n" class="spark" :style="{ '--n': n }">✧</i>
  </span>
</template>
<style scoped>
.wing-frame {
  --frame-color: #e7bb59;
  --frame-light: #fff3b6;
  position: relative;
  display: inline-flex;
  width: var(--frame-size);
  height: var(--frame-size);
  isolation: isolate;
  flex: none;
}
.wing-frame.blue {
  --frame-color: #6ba6ee;
  --frame-light: #e1f4ff;
}
.wing-frame.pink {
  --frame-color: #ec89c3;
  --frame-light: #ffe7f5;
}
.frame-ring {
  display: block;
  width: 100%;
  height: 100%;
  padding: 5px;
  background: conic-gradient(
    from 35deg,
    var(--frame-light),
    var(--frame-color),
    #fff,
    var(--frame-color),
    var(--frame-light)
  );
  border-radius: 50%;
  box-shadow:
    0 0 0 1px #fff9,
    0 0 10px var(--frame-color),
    inset 0 0 0 2px #fff9;
}
.wing {
  position: absolute;
  width: 66%;
  height: 110%;
  color: #f8fcff;
  top: -11%;
  z-index: -1;
  filter: drop-shadow(0 2px 2px #80a5d455);
  transform-origin: 90% 80%;
  animation: wingbeat 2.8s ease-in-out infinite;
}
.wing.left {
  right: 76%;
  transform: rotate(-8deg);
}
.wing.right {
  transform-origin: 50% 80%;
  left: 76%;
  transform: scaleX(-1) rotate(-8deg);
  animation-name: wingbeat-right;
}
.jewel {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  color: var(--frame-color);
  font-size: 23px;
  text-shadow: 0 0 3px white;
  z-index: 2;
  line-height: 1;
}
.top {
  top: -15px;
}
.bottom {
  bottom: -10px;
}
.spark {
  position: absolute;
  color: #fff;
  font-style: normal;
  font-size: 15px;
  left: calc(var(--n) * 22% - 20%);
  top: 0;
  text-shadow: 0 0 5px #6ab7ff;
  animation: twinkle 2s calc(var(--n) * 0.31s) ease-in-out infinite;
}
.spark:nth-last-child(2) {
  top: 70%;
}
.spark:nth-last-child(3) {
  top: 22%;
}
.spark:nth-last-child(4) {
  top: 78%;
}
@keyframes wingbeat {
  50% {
    transform: rotate(7deg) scaleX(0.85);
  }
}
@keyframes wingbeat-right {
  50% {
    transform: scaleX(-0.85) rotate(7deg);
  }
}
@keyframes twinkle {
  0%,
  100% {
    opacity: 0.2;
    scale: 0.6;
  }
  50% {
    opacity: 1;
    scale: 1.2;
  }
}
@media (prefers-reduced-motion: reduce) {
  .wing,
  .spark {
    animation: none;
  }
}
</style>
