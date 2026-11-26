<script setup lang="ts">
import { onBeforeUnmount, watch, ref } from 'vue';
import GiftArt from './GiftArt.vue';
const props = defineProps<{ show: boolean; kind: string; name?: string }>();
const emit = defineEmits<{ close: [] }>();
const active = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;
watch(
  () => props.show,
  (v) => {
    active.value = v;
    clearTimeout(timer);
    if (v) timer = setTimeout(() => emit('close'), 3200);
  },
);
onBeforeUnmount(() => clearTimeout(timer));
</script>
<template>
  <Teleport to="body"
    ><div
      v-if="show"
      class="gift-preview"
      role="dialog"
      aria-label="礼物效果预览"
      @click="emit('close')"
    >
      <button class="close" @click.stop="emit('close')">×</button>
      <div class="gift-stage" :class="{ active }">
        <i v-for="n in 16" :key="n" :style="{ '--i': n }">✦</i
        ><GiftArt :kind="kind" :size="170" />
        <h2>{{ name || '心动时刻' }}</h2>
        <p>礼物效果预览</p>
      </div>
    </div></Teleport
  >
</template>
<style scoped>
.gift-preview {
  position: fixed;
  inset: 0;
  z-index: 2500;
  background: radial-gradient(ellipse, #563b6999, #151827e8);
  display: grid;
  place-items: center;
  backdrop-filter: blur(5px);
}
.close {
  position: absolute;
  right: 25px;
  top: 25px;
  color: white;
  border: 1px solid #fff8;
  background: transparent;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 25px;
}
.gift-stage {
  text-align: center;
  color: #fff;
  position: relative;
  animation: gift-appear 0.6s cubic-bezier(0.2, 1.5, 0.5, 1) both;
}
.gift-stage > .gift-art {
  animation: gift-float 1.7s ease-in-out infinite;
  filter: drop-shadow(0 0 35px #f197e6);
}
.gift-stage h2 {
  font-size: 25px;
  font-weight: 500;
  letter-spacing: 5px;
  margin-top: 35px;
}
.gift-stage p {
  font-size: 12px;
  color: #eee9;
}
.gift-stage i {
  position: absolute;
  font-style: normal;
  left: 50%;
  top: 33%;
  color: #fff9;
  animation: burst 2s calc(var(--i) * 0.045s) ease-out infinite;
  --angle: calc(var(--i) * 22.5deg);
}
@keyframes burst {
  from {
    transform: rotate(var(--angle)) translateY(35px) scale(0.3);
    opacity: 1;
  }
  to {
    transform: rotate(var(--angle)) translateY(190px) scale(1.6);
    opacity: 0;
  }
}
@keyframes gift-appear {
  from {
    transform: scale(0.3);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
@keyframes gift-float {
  50% {
    transform: translateY(-12px) rotate(4deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .gift-stage,
  .gift-stage i,
  .gift-stage > .gift-art {
    animation: none;
  }
}
</style>
