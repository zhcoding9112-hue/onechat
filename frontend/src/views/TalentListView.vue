<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import FanTabBar from '@/components/FanTabBar.vue'
import { NavBar, Empty, Skeleton } from 'vant'
import TalentCardItem from '@/components/TalentCardItem.vue'
import { homeApi, type CategoryView, type EntryView, type MarqueeItem, type PopularTalentView } from '@/api/home'
import { talentApi, type TalentCard } from '@/api/talent'
import { ClientEvent, track } from '@/api/analytics'
import { formatPrice } from '@/utils/money'

const router = useRouter()

/*
 * 每个区块各自持有 ready 状态，且各自独立取数。
 * 这是「任一区块取数失败不影响其余部分」的实现方式：
 * 任何一个 catch 只把自己的 ready 置为 false，整块不渲染，其余区块照常。
 */
const categories = ref<CategoryView[]>([])
const popular = ref<PopularTalentView[]>([])
const marquee = ref<MarqueeItem[]>([])
const grids = ref<EntryView[]>([])
const banners = ref<EntryView[]>([])

const activeCategory = ref<string | null>(null)
const genderFilter = ref<string | null>(null)

const feed = ref<TalentCard[]>([])
const feedTotal = ref(0)
const feedHasMore = ref(false)
const feedPage = ref(0)
const feedLoading = ref(true)
const feedFailed = ref(false)
const loadingMore = ref(false)

const bannerIndex = ref(0)
let bannerTimer: number | undefined

const marqueeText = computed(() =>
  marquee.value.map((m) => `【${m.nickname}】收到了打赏 ${formatPrice(m.amountCents)}`),
)
const currentBanner = computed(() => banners.value[bannerIndex.value] ?? null)

onMounted(() => {
  track(ClientEvent.VIEW_TALENT_LIST)
  // 并行发起，互不阻塞：慢的区块不拖住整页
  void loadBlock(() => homeApi.categories(), categories)
  void loadBlock(() => homeApi.popularTalents(), popular)
  void loadBlock(() => homeApi.tipMarquee(), marquee)
  void loadBlock(() => homeApi.entries('GRID'), grids)
  void loadBlock(() => homeApi.entries('BANNER'), banners).then(startBannerRotation)
  void reloadFeed()
})

onBeforeUnmount(stopBannerRotation)

/** 区块取数：失败时静默留空，由模板的 v-if 整块隐藏，不向粉丝弹错。 */
async function loadBlock<T>(fetcher: () => Promise<T[]>, target: { value: T[] }) {
  try {
    target.value = await fetcher()
  } catch {
    target.value = []
  }
}

function startBannerRotation() {
  stopBannerRotation()
  if (banners.value.length <= 1) return
  bannerTimer = window.setInterval(() => {
    bannerIndex.value = (bannerIndex.value + 1) % banners.value.length
  }, 4000)
}

function stopBannerRotation() {
  if (bannerTimer !== undefined) {
    window.clearInterval(bannerTimer)
    bannerTimer = undefined
  }
}

watch([activeCategory, genderFilter], () => void reloadFeed())

async function reloadFeed() {
  feedPage.value = 0
  feedLoading.value = true
  feedFailed.value = false
  try {
    const res = await talentApi.list(
      { category: activeCategory.value, gender: genderFilter.value, page: 0 },
      { toastOnError: false },
    )
    feed.value = res.items
    feedTotal.value = res.total
    feedHasMore.value = res.hasMore
  } catch {
    feedFailed.value = true
  } finally {
    feedLoading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !feedHasMore.value) return
  loadingMore.value = true
  try {
    const next = feedPage.value + 1
    const res = await talentApi.list({
      category: activeCategory.value,
      gender: genderFilter.value,
      page: next,
    })
    feed.value = feed.value.concat(res.items)
    feedTotal.value = res.total
    feedHasMore.value = res.hasMore
    feedPage.value = next
  } finally {
    loadingMore.value = false
  }
}

function onScroll(e: Event) {
  const el = e.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 240) {
    void loadMore()
  }
}

function pickCategory(code: string | null) {
  activeCategory.value = activeCategory.value === code ? null : code
}

function open(id: number) {
  router.push(`/talents/${id}`)
}

function openEntry(entry: EntryView) {
  if (!entry.targetUrl) return
  if (/^https?:\/\//.test(entry.targetUrl)) {
    window.location.assign(entry.targetUrl)
  } else {
    router.push(entry.targetUrl)
  }
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}
</script>

<template>
  <div class="page" @scroll.passive="onScroll">
    <NavBar title="陪聊服务">
      <template #right>
        <span class="nav-link" @click="router.push('/me')">我的</span>
      </template>
    </NavBar>

    <!-- 品类 Tab -->
    <section v-if="categories.length" class="block categories">
      <button
        v-for="c in categories"
        :key="c.code"
        class="cat"
        :class="{ on: activeCategory === c.code }"
        @click="pickCategory(c.code)"
      >
        {{ c.name }}
      </button>
    </section>

    <!--
      入口大卡。第 2 组实现首页时跳转目标尚不存在，故留到此处补齐。
      目前只有随机匹配一张；心动墙落地后（第 9 组）这里会变成两张并排。
    -->
    <section class="block entries">
      <button class="entry match" @click="router.push('/match')">
        <strong>随机匹配</strong>
        <span>不知道找谁？随机遇见一位</span>
      </button>
      <button class="entry audition" @click="router.push('/audition')">
        <strong>试音选人</strong>
        <span>先听声音，再决定</span>
      </button>
    </section>

    <!-- 打赏跑马灯 -->
    <section v-if="marqueeText.length" class="block marquee">
      <div class="marquee-track">
        <span v-for="(text, i) in marqueeText" :key="i" class="marquee-item">{{ text }}</span>
      </div>
    </section>

    <!-- 人气店员 -->
    <section v-if="popular.length" class="block">
      <h3 class="sec-title">人气店员</h3>
      <div class="pop-scroll">
        <div v-for="p in popular" :key="p.id" class="pop" @click="open(p.id)">
          <div class="pop-cover">
            <img v-if="p.coverUrl" :src="p.coverUrl" alt="" />
            <span v-else class="fallback">{{ initial(p.nickname) }}</span>
            <i v-if="p.online" class="pop-dot"></i>
          </div>
          <span class="pop-name">{{ p.nickname }}</span>
        </div>
      </div>
    </section>

    <!-- 九宫格服务入口 -->
    <section v-if="grids.length" class="block">
      <div class="grid">
        <button v-for="g in grids" :key="g.id" class="grid-item" @click="openEntry(g)">
          <img v-if="g.imageUrl" :src="g.imageUrl" alt="" />
          <span>{{ g.title }}</span>
        </button>
      </div>
    </section>

    <!-- 运营横幅轮播 -->
    <section v-if="currentBanner" class="block">
      <div class="banner" @click="openEntry(currentBanner)">
        <img v-if="currentBanner.imageUrl" :src="currentBanner.imageUrl" alt="" />
        <div class="banner-text">
          <strong>{{ currentBanner.title }}</strong>
          <span v-if="currentBanner.subtitle">{{ currentBanner.subtitle }}</span>
        </div>
      </div>
      <div v-if="banners.length > 1" class="dots">
        <i v-for="(b, i) in banners" :key="b.id" :class="{ on: i === bannerIndex }"></i>
      </div>
    </section>

    <!-- 主播信息流 -->
    <section class="block feed">
      <div class="feed-head">
        <h3 class="sec-title">店员推荐</h3>
        <div class="filters">
          <button :class="{ on: genderFilter === 'FEMALE' }" @click="genderFilter = genderFilter === 'FEMALE' ? null : 'FEMALE'">
            女生
          </button>
          <button :class="{ on: genderFilter === 'MALE' }" @click="genderFilter = genderFilter === 'MALE' ? null : 'MALE'">
            男生
          </button>
        </div>
      </div>

      <div v-if="feedLoading" class="list">
        <Skeleton v-for="i in 3" :key="i" avatar :row="2" class="skeleton" />
      </div>
      <Empty v-else-if="feedFailed" image="network" description="加载失败，请下拉重试" />
      <Empty v-else-if="feed.length === 0" description="该条件下暂时没有主播" />
      <div v-else class="list">
        <TalentCardItem v-for="t in feed" :key="t.id" :talent="t" @click="open(t.id)" />
        <p v-if="!feedHasMore" class="end">没有更多了 · 共 {{ feedTotal }} 位</p>
        <p v-else-if="loadingMore" class="end">加载中…</p>
      </div>
    </section>

    <FanTabBar />
  </div>
</template>

<style scoped>
.page {
  height: 100vh;
  overflow-y: auto;
  background: var(--g-page);
  background-attachment: fixed;
  /* 给底部导航让出位置，否则最后一行卡片会被压在导航下面 */
  padding-bottom: 68px;
}

.nav-link {
  font-size: var(--fs-md);
  color: var(--c-brand-deep);
}

.block {
  padding: 0 var(--sp-4);
  margin-bottom: var(--sp-4);
}

.sec-title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

/* ---------- 品类 Tab ---------- */
.categories {
  display: flex;
  gap: var(--sp-5);
  overflow-x: auto;
  padding-bottom: var(--sp-1);
  scrollbar-width: none;
}

.categories::-webkit-scrollbar {
  display: none;
}

.cat {
  border: none;
  background: none;
  padding: var(--sp-2) 0;
  font-size: var(--fs-lg);
  color: var(--c-text-2);
  white-space: nowrap;
  cursor: pointer;
}

.cat.on {
  color: var(--c-text);
  font-weight: 700;
  font-size: var(--fs-xl);
}

/* ---------- 入口大卡 ---------- */
.entries {
  display: flex;
  gap: var(--sp-3);
}

.entry {
  flex: 1;
  border: none;
  border-radius: var(--r-lg);
  padding: var(--sp-5) var(--sp-4);
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  cursor: pointer;
}

.entry.match {
  background: var(--g-op-cool);
}

.entry.audition {
  background: var(--g-op-violet);
}

.entry strong {
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--c-text);
}

.entry span {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

/* ---------- 跑马灯 ---------- */
.marquee {
  overflow: hidden;
}

.marquee-track {
  display: flex;
  gap: var(--sp-6);
  background: var(--g-op-violet);
  border-radius: var(--r-pill);
  padding: var(--sp-3) var(--sp-4);
  white-space: nowrap;
  animation: marquee 28s linear infinite;
}

.marquee-item {
  font-size: var(--fs-md);
  color: var(--c-text);
}

@keyframes marquee {
  from {
    transform: translateX(8%);
  }
  to {
    transform: translateX(-100%);
  }
}

/* 尊重系统的减少动态偏好：动效不是信息，不该强加 */
@media (prefers-reduced-motion: reduce) {
  .marquee-track {
    animation: none;
  }
}

/* ---------- 人气店员 ---------- */
.pop-scroll {
  display: flex;
  gap: var(--sp-3);
  overflow-x: auto;
  scrollbar-width: none;
}

.pop-scroll::-webkit-scrollbar {
  display: none;
}

.pop {
  flex: none;
  width: 84px;
  cursor: pointer;
}

.pop-cover {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: var(--r-md);
  overflow: hidden;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.pop-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pop-dot {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-online);
  border: 2px solid #fff;
}

.pop-name {
  display: block;
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fallback {
  font-size: var(--fs-xl);
  font-weight: 300;
  color: #fff;
}

/* ---------- 九宫格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-3);
  background: var(--g-op-violet);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
}

.grid-item {
  border: none;
  background: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-md);
  color: var(--c-text);
  cursor: pointer;
}

.grid-item img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

/* ---------- 横幅 ---------- */
.banner {
  position: relative;
  border-radius: var(--r-lg);
  overflow: hidden;
  background: var(--g-op-cool);
  min-height: 84px;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.banner img {
  width: 100%;
  display: block;
}

.banner-text {
  position: absolute;
  left: var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.banner-text strong {
  font-size: var(--fs-lg);
}

.banner-text span {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.dots {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-top: var(--sp-2);
}

.dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--c-offline);
}

.dots i.on {
  background: var(--c-brand);
  width: 14px;
  border-radius: var(--r-pill);
}

/* ---------- 信息流 ---------- */
.feed-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-3);
}

.feed-head .sec-title {
  margin: 0;
}

.filters {
  display: flex;
  gap: var(--sp-2);
}

.filters button {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--r-pill);
  padding: 4px 14px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
}

.filters button.on {
  background: var(--c-brand-soft);
  border-color: var(--c-brand);
  color: var(--c-brand-deep);
  font-weight: 600;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding-bottom: var(--sp-6);
}

.skeleton {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4) var(--sp-3);
}

.end {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
  margin: var(--sp-4) 0 0;
}
</style>
