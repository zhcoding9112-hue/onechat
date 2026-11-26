<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import type { PopularTalentView } from '@/api/home';
import Portrait from './Portrait.vue';

defineProps<{ items: PopularTalentView[] }>();
const emit = defineEmits<{ select: [id: number] }>();
const viewport = ref<HTMLElement>();
const firstGroup = ref<HTMLElement>();
const looping = ref(false);
let cycleWidth = 0;
let offset = 0;
let lastTime = 0;
let frame = 0;
let resumeAfter = 0;
const hovering = ref(false);
const touching = ref(false);
const focused = ref(false);
let reducedMotion = false;
let resizeObserver: ResizeObserver | undefined;
let motionPreference: MediaQueryList | undefined;

function measure() {
  if (!viewport.value || !firstGroup.value) return;
  cycleWidth = firstGroup.value.getBoundingClientRect().width + 11;
  // 一屏能完整放下时保持静止，不为滚动重复少量主播。
  looping.value = cycleWidth - 11 > viewport.value.clientWidth - 30;
  offset = viewport.value.scrollLeft;
}

function syncMotionPreference() {
  reducedMotion = motionPreference?.matches ?? false;
}

function pauseAfterInteraction() {
  touching.value = false;
  resumeAfter = performance.now() + 1800;
}

function onFocusOut(event: FocusEvent) {
  focused.value =
    viewport.value?.contains(event.relatedTarget as Node | null) ?? false;
  if (!focused.value) pauseAfterInteraction();
}

function tick(time: number) {
  const elapsed = lastTime ? Math.min(time - lastTime, 64) : 0;
  lastTime = time;
  const el = viewport.value;
  if (el && looping.value && cycleWidth > 0) {
    if (
      reducedMotion ||
      document.hidden ||
      hovering.value ||
      touching.value ||
      focused.value ||
      time < resumeAfter
    ) {
      offset = el.scrollLeft;
    } else {
      // 保留小数累计量，避免低速位移被 scrollLeft 的像素取整吞掉。
      offset = (offset + (elapsed * 16) / 1000) % cycleWidth;
      el.scrollLeft = offset;
    }
  }
  frame = requestAnimationFrame(tick);
}

onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
  syncMotionPreference();
  motionPreference.addEventListener('change', syncMotionPreference);
  resizeObserver = new ResizeObserver(measure);
  if (viewport.value) resizeObserver.observe(viewport.value);
  if (firstGroup.value) resizeObserver.observe(firstGroup.value);
  measure();
  frame = requestAnimationFrame(tick);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(frame);
  resizeObserver?.disconnect();
  motionPreference?.removeEventListener('change', syncMotionPreference);
});
</script>

<template>
  <div
    ref="viewport"
    class="popular-scroll"
    :class="{ 'is-looping': looping }"
    aria-label="人气店员，支持左右滑动"
    @pointerenter="hovering = $event.pointerType === 'mouse'"
    @pointerleave="
      hovering = false;
      pauseAfterInteraction();
    "
    @touchstart.passive="touching = true"
    @touchend.passive="pauseAfterInteraction"
    @touchcancel.passive="pauseAfterInteraction"
    @wheel.passive="pauseAfterInteraction"
    @focusin="focused = !hovering && !touching"
    @keydown="focused = true"
    @focusout="onFocusOut"
  >
    <div class="popular-track">
      <div ref="firstGroup" class="popular-group">
        <button v-for="p in items" :key="p.id" @click="emit('select', p.id)">
          <span class="popular-photo">
            <Portrait :src="p.coverUrl" :seed="p.id" :name="p.nickname" />
            <i v-if="p.online" />
          </span>
          <span
            >{{ p.nickname
            }}<small v-if="p.id < 0" class="popular-demo">模拟</small></span
          >
        </button>
      </div>
      <!-- 第二组衔接第一组尾部，循环归位时画面保持一致。 -->
      <div v-if="looping" class="popular-group" aria-hidden="true">
        <button
          v-for="p in items"
          :key="p.id"
          tabindex="-1"
          @click="emit('select', p.id)"
        >
          <span class="popular-photo">
            <Portrait :src="p.coverUrl" :seed="p.id" :name="p.nickname" />
            <i v-if="p.online" />
          </span>
          <span
            >{{ p.nickname
            }}<small v-if="p.id < 0" class="popular-demo">模拟</small></span
          >
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.popular-scroll {
  overflow-x: auto;
  scrollbar-width: none;
  margin: 0 -15px;
  padding: 0 15px;
  scroll-behavior: auto;
  overflow-anchor: none;
}
.popular-scroll::-webkit-scrollbar {
  display: none;
}
.popular-track,
.popular-group {
  display: flex;
  gap: 11px;
  width: max-content;
}
.popular-group {
  flex: none;
}
.popular-group button {
  flex: none;
  width: 72px;
  background: none;
  border: 0;
  padding: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 7px;
  font-size: 12px;
  color: inherit;
}
.popular-photo {
  display: block;
  width: 72px;
  height: 72px;
  border-radius: 13px;
  position: relative;
}
.popular-photo i {
  position: absolute;
  right: 6px;
  bottom: 6px;
  background: #20db9c;
  width: 7px;
  height: 7px;
  border: 1px solid white;
  border-radius: 50%;
}
.popular-demo {
  display: block;
  font-size: 9px;
  color: #9496a2;
  margin-top: 2px;
}
@media (min-width: 600px) {
  .popular-group button,
  .popular-photo {
    width: 100px;
  }
  .popular-photo {
    height: 100px;
  }
}
</style>
