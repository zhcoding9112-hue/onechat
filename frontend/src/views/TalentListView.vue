<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, Empty, Skeleton } from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { talentApi, type TalentCard } from '@/api/talent'
import { ClientEvent, track } from '@/api/analytics'

const router = useRouter()
const talents = ref<TalentCard[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  track(ClientEvent.VIEW_TALENT_LIST)
  try {
    talents.value = await talentApi.list()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

function open(id: number) {
  router.push(`/talents/${id}`)
}

/** 性别符号。未填写返回 null，调用处据此整项不渲染，不展示占位。 */
function genderMark(g: TalentCard['gender']) {
  if (g === 'FEMALE') return '♀'
  if (g === 'MALE') return '♂'
  return null
}

/** 封面缺失时用昵称首字兜底，比一个灰色人形图标体面得多。 */
function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}
</script>

<template>
  <div class="page">
    <NavBar title="陪聊服务">
      <template #right>
        <span class="nav-link" @click="router.push('/me')">我的</span>
      </template>
    </NavBar>

    <div v-if="loading" class="list">
      <Skeleton v-for="i in 3" :key="i" avatar :row="2" class="skeleton" />
    </div>

    <Empty v-else-if="failed" image="network" description="加载失败，请下拉重试" />
    <Empty v-else-if="talents.length === 0" description="暂时还没有主播，稍后再来看看" />

    <div v-else class="list">
      <article v-for="t in talents" :key="t.id" class="card" @click="open(t.id)">
        <div class="cover">
          <img v-if="t.coverUrl" :src="t.coverUrl" alt="" />
          <span v-else class="fallback">{{ initial(t.nickname) }}</span>
          <AudioPreview
            v-if="t.audioUrl"
            class="on-cover badge"
            :url="t.audioUrl"
            :seconds="t.audioSeconds"
          />
        </div>

        <div class="info">
          <div class="row-top">
            <span class="name">{{ t.nickname }}</span>
            <span v-if="genderMark(t.gender)" class="gender" :class="t.gender?.toLowerCase()">
              {{ genderMark(t.gender) }}<i v-if="t.age" class="num">{{ t.age }}</i>
            </span>
            <span v-if="t.region" class="region">· {{ t.region }}</span>
            <span class="status" :class="{ online: t.online }">
              <i class="dot"></i>{{ t.online ? '在线' : '离线' }}
            </span>
          </div>

          <div v-if="t.tags.length" class="skills">
            <template v-for="(tag, i) in t.tags" :key="tag">
              <span v-if="i > 0" class="sep">|</span>
              <span>{{ tag }}</span>
            </template>
          </div>

          <div v-if="t.promoTags.length" class="promos">
            <span v-for="p in t.promoTags" :key="p" class="promo">{{ p }}</span>
          </div>
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

.nav-link {
  font-size: var(--fs-md);
  color: var(--c-brand-deep);
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

.card {
  display: flex;
  gap: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3);
  box-shadow: var(--sd-card);
  cursor: pointer;
}

/* ---------- 封面 ---------- */
.cover {
  position: relative;
  width: 92px;
  height: 116px;
  flex: none;
  border-radius: var(--r-md);
  overflow: hidden;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.fallback {
  font-size: var(--fs-2xl);
  font-weight: 300;
  color: #fff;
}

.badge {
  position: absolute;
  left: var(--sp-2);
  bottom: var(--sp-2);
}

/* ---------- 右侧信息 ---------- */
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding-top: var(--sp-1);
}

.row-top {
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
  display: inline-flex;
  align-items: center;
  gap: 2px;
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
  flex: none;
}

.status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-xs);
  color: var(--c-text-3);
  background: var(--c-bg);
  border-radius: var(--r-pill);
  padding: 2px 8px;
  flex: none;
}

.status .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--c-offline);
}

.status.online {
  color: var(--c-online);
  background: rgba(56, 193, 114, 0.1);
}

.status.online .dot {
  background: var(--c-online);
}

.skills {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
}

.skills .sep {
  color: var(--c-border);
}

.promos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.promo {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  padding: 3px 10px;
  background: var(--c-surface);
}
</style>
