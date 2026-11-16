<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton, showToast } from 'vant'
import { talentApi, type FavoriteItem } from '@/api/talent'
import { EntrySource, ENTRY_QUERY, talentPath } from '@/api/analytics'

const router = useRouter()
const items = ref<FavoriteItem[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(reload)

async function reload() {
  loading.value = true
  failed.value = false
  try {
    items.value = await talentApi.myFavorites()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function open(item: FavoriteItem) {
  // 已下架的仍可进详情看资料，只是详情页不给下单入口
  router.push(talentPath(item.id, EntrySource.FAVORITES))
}

async function remove(item: FavoriteItem, e: Event) {
  e.stopPropagation()
  await talentApi.unfavorite(item.id)
  items.value = items.value.filter((i) => i.id !== item.id)
  showToast('已取消收藏')
}

function genderMark(g: FavoriteItem['gender']) {
  if (g === 'FEMALE') return '♀'
  if (g === 'MALE') return '♂'
  return null
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}
</script>

<template>
  <div class="page">
    <NavBar title="我的收藏" left-arrow @click-left="$router.back()" />

    <div v-if="loading" class="list">
      <Skeleton v-for="i in 3" :key="i" avatar :row="2" class="skeleton" />
    </div>
    <Empty v-else-if="failed" image="network" description="加载失败" />
    <div v-else-if="items.length === 0" class="empty">
      <Empty description="还没有收藏的主播" />
      <button class="go" @click="router.push('/')">去看看</button>
    </div>

    <div v-else class="list">
      <article v-for="item in items" :key="item.id" class="card" @click="open(item)">
        <div class="cover" :class="{ dim: !item.available }">
          <img v-if="item.coverUrl" :src="item.coverUrl" alt="" />
          <span v-else class="fallback">{{ initial(item.nickname) }}</span>
        </div>
        <div class="info">
          <div class="row">
            <span class="name">{{ item.nickname }}</span>
            <span v-if="genderMark(item.gender)" class="gender" :class="item.gender?.toLowerCase()">
              {{ genderMark(item.gender) }}<i v-if="item.age" class="num">{{ item.age }}</i>
            </span>
            <span v-if="item.region" class="region">· {{ item.region }}</span>
          </div>
          <!-- 已下架的保留在列表里并说明原因：直接抹掉会让粉丝以为收藏丢了 -->
          <span v-if="!item.available" class="unavailable">
            {{ item.unavailableReason ?? '该主播暂不可约' }}
          </span>
          <span v-else class="status" :class="{ online: item.online }">
            <i class="dot"></i>{{ item.online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="ops">
          <button class="unfav" @click="remove(item, $event)">取消收藏</button>
          <button v-if="item.available" class="order" @click.stop="router.push(`/talents/${item.id}/order?${ENTRY_QUERY}=${EntrySource.FAVORITES}`)">
            下单
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
}

.list {
  padding: var(--sp-3) var(--sp-4) var(--sp-6);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.skeleton {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4) var(--sp-3);
}

.empty {
  padding-top: var(--sp-8);
  text-align: center;
}

.go {
  border: 1px solid var(--c-brand);
  background: var(--c-surface);
  color: var(--c-brand-deep);
  border-radius: var(--r-pill);
  padding: 8px 24px;
  font-size: var(--fs-md);
  cursor: pointer;
}

.card {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3);
  box-shadow: var(--sd-card);
  cursor: pointer;
}

.cover {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: var(--r-md);
  overflow: hidden;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover.dim {
  filter: grayscale(1);
  opacity: 0.6;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fallback {
  font-size: var(--fs-xl);
  font-weight: 300;
  color: #fff;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.name {
  font-size: var(--fs-lg);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gender {
  font-size: var(--fs-sm);
  font-weight: 600;
  flex: none;
}

.gender.female {
  color: #ff6b9d;
}

.gender.male {
  color: #4b9bff;
}

.gender i {
  font-style: normal;
}

.region {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.unavailable {
  font-size: var(--fs-sm);
  color: #ed6a0c;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.status .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--c-offline);
}

.status.online {
  color: var(--c-online);
}

.status.online .dot {
  background: var(--c-online);
}

.ops {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  flex: none;
}

.unfav,
.order {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--r-pill);
  padding: 5px 12px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
  white-space: nowrap;
}

.order {
  border-color: var(--c-brand);
  background: var(--c-brand);
  color: #fff;
}
</style>
