<script setup lang="ts">
import { computed, ref, watch } from 'vue';
const props = withDefaults(
  defineProps<{
    src?: string | null;
    seed?: number;
    name?: string;
    round?: boolean;
  }>(),
  { seed: 0, name: '', round: false },
);
const broken = ref(false);
watch(
  () => props.src,
  () => (broken.value = false),
);
const tile = computed(() => Math.abs(props.seed) % 6);
const position = computed(
  () => `${(tile.value % 3) * 50}% ${Math.floor(tile.value / 3) * 100}%`,
);
const usePhoto = computed(
  () =>
    import.meta.env.VITE_ILLUSTRATED_AVATARS !== 'true' &&
    props.src &&
    !props.src.includes('avatar-placeholder') &&
    !broken.value,
);
</script>
<template>
  <span
    class="portrait-art"
    :class="{ round }"
    :style="{ backgroundPosition: position }"
    role="img"
    :aria-label="name || '插画头像'"
  >
    <img v-if="usePhoto" :src="src!" :alt="name" @error="broken = true" />
  </span>
</template>
<style scoped>
.portrait-art {
  display: block;
  width: 100%;
  height: 100%;
  flex: none;
  overflow: hidden;
  background-color: #eee3f2;
  background-image: url('/images/portraits.webp');
  background-size: 300% 200%;
  background-repeat: no-repeat;
  border-radius: inherit;
}
.portrait-art.round {
  border-radius: 50%;
}
.portrait-art img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
