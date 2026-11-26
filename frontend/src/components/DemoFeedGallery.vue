<script setup lang="ts">
import { ref } from 'vue';
import { Popup } from 'vant';
defineProps<{ tiles: number[] }>();
const selected = ref(0);
const open = ref(false);
const names = [
  '咖啡时光',
  '公园散步',
  '今日鲜花',
  '草莓甜点',
  '雨天读书',
  '河畔晚霞',
];
const position = (tile: number) =>
  `${(tile % 3) * 50}% ${Math.floor(tile / 3) * 100}%`;
function preview(tile: number) {
  selected.value = tile;
  open.value = true;
}
</script>

<template>
  <div
    class="demo-gallery"
    :class="{ single: tiles.length === 1, pair: tiles.length === 2 }"
  >
    <button
      v-for="(tile, i) in tiles"
      :key="i"
      class="scene"
      :style="{ backgroundPosition: position(tile) }"
      :aria-label="`查看模拟配图：${names[tile]}`"
      @click="preview(tile)"
    />
  </div>
  <Popup
    v-model:show="open"
    round
    closeable
    teleport="body"
    class="scene-popup"
  >
    <div class="scene-viewer">
      <p>{{ names[selected] }} · 模拟配图</p>
      <div
        class="scene enlarged"
        role="img"
        :aria-label="names[selected]"
        :style="{ backgroundPosition: position(selected) }"
      />
    </div>
  </Popup>
</template>

<style scoped>
.demo-gallery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
  margin-top: 12px;
}
.demo-gallery.pair {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.demo-gallery.single {
  display: block;
}
.scene {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  border: 0;
  border-radius: 8px;
  background-color: #f1eae5;
  background-image: url('/images/feed-lifestyle-scenes.png');
  background-size: 300% 200%;
  background-repeat: no-repeat;
}
.single .scene {
  width: 76%;
  max-width: 420px;
}
.scene-popup {
  width: min(94vw, 580px);
}
.scene-viewer {
  padding: 36px 12px 12px;
}
.scene-viewer p {
  margin: 0 0 12px;
  text-align: center;
  font-size: 12px;
  color: #9a94a1;
}
.enlarged {
  width: 100%;
}
</style>
