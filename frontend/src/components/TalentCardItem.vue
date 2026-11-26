<script setup lang="ts">
import { ref } from 'vue';
import AudioPreview from './AudioPreview.vue';
import TalentPortrait from './TalentPortrait.vue';
import type { AvatarFrame } from '@/types/avatar-frame';
import { talentApi, type TalentCard } from '@/api/talent';
const props = defineProps<{
  talent: TalentCard & { demo?: boolean; avatarFrame?: AvatarFrame | null };
  favorited?: boolean;
}>();
const emit = defineEmits<{ favorite: [value: boolean] }>();
const busy = ref(false);
async function favorite() {
  if (busy.value) return;
  if (props.talent.demo) {
    emit('favorite', !props.favorited);
    return;
  }
  busy.value = true;
  try {
    if (props.favorited) await talentApi.unfavorite(props.talent.id);
    else await talentApi.favorite(props.talent.id);
    emit('favorite', !props.favorited);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <article
    class="talent-card"
    tabindex="0"
    role="link"
    :aria-label="talent.nickname + '的详情'"
    @keydown.enter.self="($event.currentTarget as HTMLElement).click()"
  >
    <div class="cover">
      <TalentPortrait
        :src="talent.coverUrl"
        :seed="talent.id"
        :name="talent.nickname"
        :frame="talent.avatarFrame"
      /><AudioPreview
        v-if="talent.audioUrl"
        class="on-cover"
        :url="talent.audioUrl"
        :seconds="talent.audioSeconds"
      />
    </div>
    <div class="info">
      <div class="name-row">
        <b>{{ talent.nickname }}</b
        ><span class="status" :class="{ online: talent.online }"
          >● {{ talent.online ? '在线' : '离线' }}</span
        >
      </div>
      <div class="meta">
        <span v-if="talent.demo" class="demo-label">模拟</span>
        <span v-if="talent.level" class="talent-level"
          >♛ LV.{{ talent.level }}</span
        ><span
          v-if="talent.gender"
          class="gender"
          :class="{ male: talent.gender === 'MALE' }"
          >{{ talent.gender === 'MALE' ? '♂' : '♀'
          }}{{ talent.age || '' }}</span
        ><span v-if="talent.region" class="region">{{ talent.region }}</span>
      </div>
      <div class="skills">{{ talent.tags.join(' ｜ ') }}</div>
      <div class="card-bottom">
        <div class="promos">
          <span v-for="p in talent.promoTags" :key="p">{{ p }}</span>
        </div>
        <button
          :disabled="busy"
          class="favorite"
          :class="{ on: favorited }"
          :aria-label="favorited ? '取消收藏' : '收藏主播'"
          @click.stop="favorite"
        >
          {{ favorited ? '♥' : '♡' }}
        </button>
      </div>
    </div>
  </article>
</template>
<style scoped>
.talent-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: white;
  border-radius: 12px;
  cursor: pointer;
  min-width: 0;
}
.cover {
  display: grid;
  place-items: center;
  width: 104px;
  height: 104px;
  border-radius: 10px;
  flex: none;
  position: relative;
  overflow: visible;
}
.on-cover {
  position: absolute;
  bottom: 4px;
  left: 5px;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding-top: 2px;
}
.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.name-row b {
  font-size: 17px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status {
  color: #b7babf;
  font-size: 10px;
  white-space: nowrap;
}
.status.online {
  color: #23bb9d;
}
.meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
  flex-wrap: wrap;
}
.demo-label {
  font-size: 9px;
  color: #9296aa;
  border: 1px solid #e2e4ee;
  padding: 1px 4px;
  border-radius: 4px;
}
.talent-level {
  font-size: 9px;
  color: #818bd0;
  background: linear-gradient(100deg, #e4e8ff, #f7e8fa);
  border-radius: 8px;
  padding: 2px 6px;
}
.gender {
  color: #f18abd;
  background: #ffedf6;
  font-size: 10px;
  border-radius: 7px;
  padding: 2px 5px;
}
.gender.male {
  color: #689eed;
  background: #eaf3ff;
}
.region {
  font-size: 10px;
  color: #a6abb0;
}
.skills {
  color: #9ba2a4;
  font-size: 11px;
  margin-top: 10px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-bottom {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 5px;
}
.promos {
  display: flex;
  gap: 4px;
  flex: 1;
  flex-wrap: wrap;
}
.promos span {
  background: #fff;
  border: 1px solid #eee;
  color: #96999d;
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 4px;
}
.favorite {
  border: 0;
  background: none;
  color: #b9c3c8;
  font-size: 25px;
  padding: 0 2px;
  line-height: 26px;
}
.favorite.on {
  color: #ff6a9d;
}
@media (max-width: 350px) {
  .cover {
    width: 88px;
    height: 88px;
  }
  .talent-card {
    gap: 9px;
  }
  .name-row b {
    font-size: 15px;
  }
}
</style>
