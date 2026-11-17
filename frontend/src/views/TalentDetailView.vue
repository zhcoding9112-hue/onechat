<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Skeleton, NoticeBar, showToast } from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { talentApi, type TalentDetail } from '@/api/talent'
import { chatApi } from '@/api/chat'
import { ApiError } from '@/api/http'
import { formatPrice } from '@/utils/money'
import { ClientEvent, ENTRY_QUERY, entryFromRoute, track } from '@/api/analytics'
import { savePendingIntent, takePendingIntent } from '@/utils/pendingIntent'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<TalentDetail | null>(null)
const loading = ref(true)
const failed = ref(false)
const selectedSpecId = ref<number | null>(null)
const favoriting = ref(false)
const opening = ref(false)

const talentId = computed(() => Number(route.params.id))

/**
 * 粉丝是从哪个入口来的。
 *
 * 在 setup 里取一次定住，不做成 computed：下面的跳转会改路由，
 * 而「这次进详情的来源」是进来那一刻的事实，不该随路由变化。
 * 分享链接进来的没有这个参数，解析出来就是直接访问——11.3 要的正是这个。
 */
const entrySource = entryFromRoute(route.query[ENTRY_QUERY])

const selectedSpec = computed(() => {
  if (!detail.value) return null
  for (const group of detail.value.services) {
    const hit = group.specs.find((s) => s.serviceSpecId === selectedSpecId.value)
    if (hit) return hit
  }
  return null
})

function genderMark(g: TalentDetail['gender']) {
  if (g === 'FEMALE') return '♀'
  if (g === 'MALE') return '♂'
  return null
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}

onMounted(async () => {
  // 授权跳转回来后，先把挂起的收藏补做掉，再拉详情——
  // 顺序反了的话详情里的 favorited 还是旧值，按钮会停在未收藏态。
  const pending = takePendingIntent()
  if (pending?.type === 'favorite' && pending.talentId === talentId.value) {
    try {
      await talentApi.favorite(talentId.value)
      showToast('已收藏')
    } catch {
      // 补做失败不该打断页面，粉丝可以自己再点一次
    }
  }

  try {
    detail.value = await talentApi.detail(talentId.value)
    const first = detail.value.services[0]?.specs[0]
    selectedSpecId.value = first?.serviceSpecId ?? null
    track(ClientEvent.VIEW_TALENT_DETAIL, {
      talentId: talentId.value,
      serviceItemId: detail.value.services[0]?.serviceItemId,
      entrySource,
    })
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function toggleFavorite() {
  if (!detail.value || favoriting.value) return
  const next = !detail.value.favorited
  favoriting.value = true

  // 请求前先把意图存下：未登录时 http 层会整页跳去授权，
  // 代码不会走到 catch，存在跳转之后才来得及。
  if (next) {
    savePendingIntent({ type: 'favorite', talentId: talentId.value })
  }

  try {
    if (next) {
      await talentApi.favorite(talentId.value)
    } else {
      await talentApi.unfavorite(talentId.value)
    }
    detail.value.favorited = next
    showToast(next ? '已收藏' : '已取消收藏')
    // 这次已经成功，清掉挂起意图，避免下次进页面又补做一遍
    takePendingIntent()
  } finally {
    favoriting.value = false
  }
}

/** 进聊天。会话由服务端「有就打开、没有才建」，连点两下也只会有一个会话。 */
async function openChat() {
  if (opening.value) return
  opening.value = true
  try {
    const { conversationId } = await chatApi.openWithTalent(talentId.value)
    await router.push(`/me/chats/${conversationId}`)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    opening.value = false
  }
}

function order(specId?: number) {
  const target = specId ?? selectedSpecId.value ?? undefined
  track(ClientEvent.CLICK_ORDER, {
    talentId: talentId.value,
    serviceItemId: detail.value?.services[0]?.serviceItemId,
    entrySource,
  })
  // 来源继续往下传：真正落进订单快照是在下单页提交那一刻
  const query: Record<string, string> = { [ENTRY_QUERY]: entrySource }
  if (target !== undefined) query.specId = String(target)
  router.push({ path: `/talents/${talentId.value}/order`, query })
}
</script>

<template>
  <div class="page">
    <NavBar title="主播" left-arrow @click-left="$router.back()" />

    <Skeleton v-if="loading" avatar :row="4" style="padding: 16px" />
    <EmptyState v-else-if="failed || !detail" image="network" description="加载失败" />

    <template v-else>
      <!-- 封面 -->
      <section class="hero">
        <div class="portrait">
          <img v-if="detail.coverUrl" :src="detail.coverUrl" alt="" />
          <span v-else class="fallback">{{ initial(detail.nickname) }}</span>
          <span class="online-badge" :class="{ on: detail.online }">
            <i class="dot"></i>{{ detail.online ? '在线' : '离线' }}
          </span>
        </div>
      </section>

      <!-- 身份 -->
      <section class="ident">
        <div class="name-row">
          <h1 class="name">{{ detail.nickname }}</h1>
          <span v-if="genderMark(detail.gender)" class="gender" :class="detail.gender?.toLowerCase()">
            {{ genderMark(detail.gender) }}<i v-if="detail.age" class="num">{{ detail.age }}</i>
          </span>
          <span v-if="detail.region" class="region">· {{ detail.region }}</span>
        </div>
        <p v-if="detail.intro" class="intro">{{ detail.intro }}</p>
        <div v-if="detail.promoTags.length || detail.tags.length" class="chips">
          <span v-for="p in detail.promoTags" :key="`p-${p}`" class="chip promo">{{ p }}</span>
          <span v-for="t in detail.tags" :key="`t-${t}`" class="chip">{{ t }}</span>
        </div>
      </section>

      <!-- 试听 -->
      <section v-if="detail.audioUrl" class="audio-card">
        <AudioPreview :url="detail.audioUrl" :seconds="detail.audioSeconds" />
        <span class="audio-hint">听听 TA 的声音</span>
      </section>

      <NoticeBar
        v-if="!detail.available"
        :text="detail.unavailableReason ?? '该主播暂不可约'"
        color="#ed6a0c"
        background="#fffbe8"
        left-icon="info-o"
      />
      <NoticeBar
        v-else-if="!detail.online"
        text="该主播当前离线，接单可能较慢"
        color="#969799"
        background="#f7f8fa"
        left-icon="clock-o"
      />

      <!-- 服务档位 -->
      <section v-if="detail.available" class="services">
        <h3 class="sec-title">选择服务</h3>
        <div v-for="group in detail.services" :key="group.serviceItemId" class="group">
          <div
            v-for="spec in group.specs"
            :key="spec.serviceSpecId"
            class="spec"
            :class="{ on: selectedSpecId === spec.serviceSpecId }"
            @click="selectedSpecId = spec.serviceSpecId"
          >
            <span class="radio" :class="{ on: selectedSpecId === spec.serviceSpecId }"></span>
            <div class="spec-main">
              <div class="spec-name">{{ group.name }} · {{ spec.name }}</div>
              <div v-if="spec.durationMinutes" class="spec-sub">{{ spec.durationMinutes }} 分钟</div>
            </div>
            <span class="price num">{{ formatPrice(spec.unitPriceCents) }}</span>
          </div>
        </div>
      </section>

      <div class="bar-space"></div>

      <!-- 底部操作 -->
      <div class="bar">
        <button class="icon-btn" :disabled="favoriting" @click="toggleFavorite">
          <svg width="20" height="18" viewBox="0 0 20 18">
            <path
              d="M10 17S1 11.4 1 5.9A4.9 4.9 0 0110 3.2 4.9 4.9 0 0119 5.9C19 11.4 10 17 10 17z"
              :fill="detail.favorited ? 'var(--c-price)' : 'none'"
              :stroke="detail.favorited ? 'var(--c-price)' : 'var(--c-text-2)'"
              stroke-width="1.4"
            />
          </svg>
          <span :class="{ on: detail.favorited }">{{ detail.favorited ? '已收藏' : '收藏' }}</span>
        </button>
        <button class="icon-btn" @click="router.push(`/talents/${talentId}/tip`)">
          <svg width="20" height="18" viewBox="0 0 20 20">
            <circle cx="10" cy="10" r="8" fill="none" stroke="var(--c-text-2)" stroke-width="1.4" />
            <path d="M10 5v10M7 8h6M7 12h6" stroke="var(--c-text-2)" stroke-width="1.4" />
          </svg>
          <span>打赏</span>
        </button>
        <button class="icon-btn" :disabled="opening" @click="openChat()">
          <svg width="20" height="18" viewBox="0 0 20 20">
            <path
              d="M10 3c4.4 0 8 2.7 8 6s-3.6 6-8 6c-.8 0-1.6-.1-2.3-.3L4 16l.8-2.8C3.1 12.1 2 10.6 2 9c0-3.3 3.6-6 8-6z"
              fill="none"
              stroke="var(--c-text-2)"
              stroke-width="1.4"
            />
          </svg>
          <span>聊天</span>
        </button>
        <button v-if="detail.available" class="cta" @click="order()">
          立即下单
          <em v-if="selectedSpec" class="num">{{ formatPrice(selectedSpec.unitPriceCents) }}</em>
        </button>
        <button v-else class="cta disabled" disabled>暂不可约</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: env(safe-area-inset-bottom);
}

/* ---------- 封面 ---------- */
.hero {
  padding: var(--sp-1) var(--sp-4) 0;
}

.portrait {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 5;
  border-radius: var(--r-lg);
  overflow: hidden;
  background: var(--g-avatar);
  display: flex;
  align-items: center;
  justify-content: center;
}

.portrait img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fallback {
  font-size: 72px;
  font-weight: 300;
  color: #fff;
}

.online-badge {
  position: absolute;
  left: var(--sp-3);
  top: var(--sp-3);
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  background: rgba(255, 255, 255, 0.92);
  border-radius: var(--r-pill);
  padding: 4px 10px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.online-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-offline);
}

.online-badge.on {
  color: var(--c-online);
}

.online-badge.on .dot {
  background: var(--c-online);
}

/* ---------- 身份 ---------- */
.ident {
  padding: var(--sp-4) var(--sp-4) 0;
}

.name-row {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.name {
  margin: 0;
  font-size: var(--fs-2xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.gender {
  font-size: var(--fs-md);
  font-weight: 600;
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
  font-size: var(--fs-md);
  color: var(--c-text-3);
}

.intro {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-md);
  color: var(--c-text-2);
  line-height: 1.6;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.chip {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  border: 1px solid var(--c-border);
  border-radius: var(--r-pill);
  padding: 4px 12px;
  background: var(--c-surface);
}

.chip.promo {
  background: var(--c-brand-soft);
  border-color: transparent;
  color: var(--c-brand-deep);
}

/* ---------- 试听 ---------- */
.audio-card {
  margin: var(--sp-4) var(--sp-4) 0;
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-3) var(--sp-4);
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  box-shadow: var(--sd-card);
}

.audio-hint {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

/* ---------- 服务档位 ---------- */
.services {
  padding: var(--sp-5) var(--sp-4) 0;
}

.sec-title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  letter-spacing: 0.06em;
  font-weight: 500;
}

.group {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  overflow: hidden;
  box-shadow: var(--sd-card);
  margin-bottom: var(--sp-3);
}

.spec {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
}

.spec:last-child {
  border-bottom: none;
}

.spec.on {
  background: var(--c-brand-soft);
}

.radio {
  width: 19px;
  height: 19px;
  border-radius: 50%;
  border: 1.5px solid #d5ccc2;
  flex: none;
}

.radio.on {
  border-color: var(--c-brand);
  border-width: 6px;
}

.spec-main {
  flex: 1;
  min-width: 0;
}

.spec-name {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.spec-sub {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  margin-top: 2px;
}

.price {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--c-price);
  white-space: nowrap;
}

/* ---------- 底部操作 ---------- */
.bar-space {
  height: 96px;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  max-width: var(--app-max-width);
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--c-border);
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.icon-btn {
  border: none;
  background: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: var(--fs-xs);
  color: var(--c-text-2);
  flex: none;
  cursor: pointer;
}

.icon-btn span.on {
  color: var(--c-price);
}

.cta {
  flex: 1;
  height: 46px;
  border: none;
  border-radius: var(--r-md);
  background: var(--c-brand);
  color: #fff;
  font-size: var(--fs-lg);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  cursor: pointer;
}

.cta em {
  font-style: normal;
  font-size: var(--fs-md);
  font-weight: 500;
  opacity: 0.85;
}

.cta.disabled {
  background: var(--c-offline);
}
</style>
