<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue';
import { useRouter } from 'vue-router';
import FanTabBar from '@/components/FanTabBar.vue';
import {
  Skeleton,
  Popup,
  showToast,
  Icon,
  Swipe,
  SwipeItem,
  type SwipeInstance,
} from 'vant';
import AudioPreview from '@/components/AudioPreview.vue';
import PopularTalentRail from '@/components/PopularTalentRail.vue';
import TalentPortrait from '@/components/TalentPortrait.vue';
import { avatarFrameNames } from '@/types/avatar-frame';
import {
  previewTalents,
  previewBanners,
  previewTipMarquee,
  type PreviewTalent,
} from '@/preview/marketplace';
import GiftArt from '@/components/GiftArt.vue';
import TalentCardItem from '@/components/TalentCardItem.vue';
import {
  homeApi,
  type CategoryView,
  type EntryView,
  type MarqueeItem,
  type PopularTalentView,
} from '@/api/home';
import { talentApi, type TalentCard } from '@/api/talent';
import {
  ClientEvent,
  EntrySource,
  ENTRY_QUERY,
  track,
  trackEntry,
  talentPath,
  type EntrySourceKey,
} from '@/api/analytics';
import { centsToYuan, formatPrice } from '@/utils/money';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();
const SupportDialog = defineAsyncComponent(
  () => import('@/components/SupportDialog.vue'),
);
const supportOpen = ref(false);

/*
 * 每个区块各自持有 ready 状态，且各自独立取数。
 * 这是「任一区块取数失败不影响其余部分」的实现方式：
 * 任何一个 catch 只把自己的 ready 置为 false，整块不渲染，其余区块照常。
 */
const categories = ref<CategoryView[]>([]);
const popular = ref<PopularTalentView[]>([]);
const marquee = ref<MarqueeItem[]>([]);
const grids = ref<EntryView[]>([]);
const banners = ref<EntryView[]>([]);

const activeCategory = ref<string | null>(null);
const genderFilter = ref<string | null>(null);
const levelFilter = ref<number | null>(null);
const keyword = ref('');
const searchDraft = ref('');
function openSearch() {
  searchDraft.value = keyword.value;
  searchOpen.value = true;
}
function applySearch() {
  keyword.value = searchDraft.value.trim();
  searchOpen.value = false;
  void reloadFeed();
}
const searchOpen = ref(false);
const levelOpen = ref(false);
const arena = ref(false);
const favorites = ref<number[]>([]);
const tickerIndex = ref(0);
let tickerTimer: number | undefined;
let feedRequest = 0;
const visibleCategories = computed(() =>
  categories.value.filter((c) =>
    arena.value
      ? /游戏|电竞|陪玩/.test(c.name)
      : !/游戏|电竞|陪玩/.test(c.name),
  ),
);
function switchArena(game: boolean) {
  arena.value = game;
  activeCategory.value = null;
  if (game) {
    const first = visibleCategories.value[0];
    if (first) activeCategory.value = first.code;
    else showToast('暂未开放电竞服务');
  }
}
function customerService() {
  supportOpen.value = true;
}

const feed = ref<TalentCard[]>([]);
const feedTotal = ref(0);
const feedHasMore = ref(false);
const feedPage = ref(0);
const feedLoading = ref(true);
const feedFailed = ref(false);
const loadingMore = ref(false);

const bannerIndex = ref(0);
const bannerSwipe = ref<SwipeInstance>();
const bannerAutoplay = window.matchMedia('(prefers-reduced-motion: reduce)')
  .matches
  ? 0
  : 4000;
const displayBanners = computed(() => [...banners.value, ...previewBanners]);
const displayPopular = computed(() => [...popular.value, ...previewTalents]);
const filteredPreview = computed(() => {
  const category = categories.value.find(
    (c) => c.code === activeCategory.value,
  )?.name;
  return previewTalents.filter(
    (t) =>
      (!keyword.value || t.nickname.includes(keyword.value)) &&
      (!genderFilter.value || t.gender === genderFilter.value) &&
      (!levelFilter.value || t.level === levelFilter.value) &&
      (!activeCategory.value ||
        (!!category &&
          (category === '情感倾听' || t.tags.includes(category)))) &&
      !arena.value,
  );
});
const displayFeed = computed(() => [...feed.value, ...filteredPreview.value]);
const displayTotal = computed(
  () => feedTotal.value + filteredPreview.value.length,
);
const selectedPreview = ref<PreviewTalent>();
const previewOpen = ref(false);
function openPreview(id: number) {
  const talent = previewTalents.find((t) => t.id === id);
  if (!talent) return false;
  selectedPreview.value = talent;
  previewOpen.value = true;
  return true;
}
function togglePreviewFavorite() {
  if (!selectedPreview.value) return;
  const id = selectedPreview.value.id;
  favorites.value = favorites.value.includes(id)
    ? favorites.value.filter((value) => value !== id)
    : [...favorites.value, id];
}

const marqueeMessages = computed(() =>
  previewTipMarquee.length
    ? previewTipMarquee.map((m) => ({
        text: `【${m.nickname}】收到了老板打赏${centsToYuan(m.amountCents)
          .replace(/\.00$/, '')
          .replace(/(\.\d)0$/, '$1')}元子`,
        demo: true,
      }))
    : marquee.value.map((m) => ({
        text: `【${m.nickname}】收到了打赏 ${formatPrice(m.amountCents)}`,
        demo: false,
      })),
);
const currentMarquee = computed(
  () => marqueeMessages.value[tickerIndex.value % marqueeMessages.value.length],
);

onMounted(() => {
  track(ClientEvent.VIEW_TALENT_LIST);
  // 并行发起，互不阻塞：慢的区块不拖住整页
  void loadBlock(() => homeApi.categories(), categories);
  void loadBlock(() => homeApi.popularTalents(), popular);
  if (!previewTipMarquee.length) {
    void loadBlock(() => homeApi.tipMarquee(), marquee);
  }
  void loadBlock(() => homeApi.entries('GRID'), grids);
  void loadBlock(() => homeApi.entries('BANNER'), banners);
  void talentApi
    .myFavorites()
    .then((items) => (favorites.value = items.map((x) => x.id)))
    .catch(() => {});
  tickerTimer = window.setInterval(() => tickerIndex.value++, 3800);
  void reloadFeed();
});

onBeforeUnmount(() => {
  window.clearInterval(tickerTimer);
});

/** 区块取数：失败时静默留空，由模板的 v-if 整块隐藏，不向粉丝弹错。 */
async function loadBlock<T>(
  fetcher: () => Promise<T[]>,
  target: { value: T[] },
) {
  try {
    target.value = await fetcher();
  } catch {
    target.value = [];
  }
}

watch([activeCategory, genderFilter, levelFilter], () => void reloadFeed());

async function reloadFeed() {
  const request = ++feedRequest;
  loadingMore.value = false;
  feedPage.value = 0;
  feedLoading.value = true;
  feedFailed.value = false;
  try {
    const res = await talentApi.list(
      {
        category: activeCategory.value,
        gender: genderFilter.value,
        level: levelFilter.value,
        keyword: keyword.value,
        page: 0,
      },
      { toastOnError: false },
    );
    if (request !== feedRequest) return;
    feed.value = res.items;
    feedTotal.value = res.total;
    feedHasMore.value = res.hasMore;
  } catch {
    if (request === feedRequest) feedFailed.value = true;
  } finally {
    if (request === feedRequest) feedLoading.value = false;
  }
}

async function loadMore() {
  if (feedLoading.value || loadingMore.value || !feedHasMore.value) return;
  const request = feedRequest;
  loadingMore.value = true;
  try {
    const next = feedPage.value + 1;
    const res = await talentApi.list({
      category: activeCategory.value,
      gender: genderFilter.value,
      level: levelFilter.value,
      keyword: keyword.value,
      page: next,
    });
    if (request !== feedRequest) return;
    feed.value = feed.value.concat(res.items);
    feedTotal.value = res.total;
    feedHasMore.value = res.hasMore;
    feedPage.value = next;
  } finally {
    if (request === feedRequest) loadingMore.value = false;
  }
}

function onScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 240) {
    void loadMore();
  }
}

function pickCategory(code: string | null) {
  activeCategory.value = activeCategory.value === code ? null : code;
  if (activeCategory.value) trackEntry(EntrySource.CATEGORY_TAB);
}

/**
 * 从主列表进详情。选中了品类就归给品类 Tab，否则归给主列表。
 *
 * 主列表单独算一个入口（而不是并进「直接访问」）：直接访问那一行是用来衡量
 * 分享链接效果的，混进首页随手点开的人就没法看了。
 */
function open(id: number) {
  if (openPreview(id)) return;
  const from = activeCategory.value
    ? EntrySource.CATEGORY_TAB
    : EntrySource.TALENT_LIST;
  router.push(talentPath(id, from));
}

/** 首页上通往另一个入口页的大卡：记一次入口点击，再跳过去。 */
function goEntry(path: string, from: EntrySourceKey) {
  trackEntry(from);
  router.push(path);
}

/** 人气店员横滑。 */
function openPopular(id: number) {
  if (openPreview(id)) return;
  trackEntry(EntrySource.POPULAR, id);
  router.push(talentPath(id, EntrySource.POPULAR));
}

/**
 * 运营位跳转。目标地址是运营填的，可能指向任意页面，所以来源要**挂在 URL 上**
 * 带过去，而不是在这里假设它一定是主播详情。
 */
function openEntry(
  entry: EntryView,
  from: typeof EntrySource.GRID | typeof EntrySource.BANNER,
) {
  if (!entry.targetUrl) return;
  trackEntry(from);
  if (/^https?:\/\//.test(entry.targetUrl)) {
    window.location.assign(entry.targetUrl);
    return;
  }
  const sep = entry.targetUrl.includes('?') ? '&' : '?';
  router.push(`${entry.targetUrl}${sep}${ENTRY_QUERY}=${from}`);
}
</script>
<template>
  <div class="home-page" @scroll.passive="onScroll">
    <header class="home-header">
      <button
        class="main-tab"
        :class="{ active: !arena }"
        @click="switchArena(false)"
      >
        树洞
      </button>
      <button
        class="main-tab game-tab"
        :class="{ active: arena }"
        @click="switchArena(true)"
      >
        电竞
      </button>
      <button class="search-pill" @click="openSearch">
        <Icon name="search" /> <span>{{ keyword || '搜索' }}</span>
      </button>
      <button
        class="trophy-button"
        aria-label="打赏排行榜"
        @click="goEntry('/leaderboard', EntrySource.LEADERBOARD)"
      >
        <GiftArt kind="trophy" :size="36" />
      </button>
    </header>
    <nav class="category-strip" aria-label="服务分类">
      <button
        v-for="c in visibleCategories"
        :key="c.code"
        :class="{ active: activeCategory === c.code }"
        @click="pickCategory(c.code)"
      >
        {{ c.name }}
      </button>
    </nav>
    <section class="entry-pair">
      <button
        class="discover entry-tile"
        @click="goEntry('/feed', EntrySource.FEED)"
      >
        <b>发现</b><span>寻找缘分cp</span><GiftArt kind="heart" :size="98" />
      </button>
      <button
        class="blind entry-tile"
        @click="goEntry('/match', EntrySource.RANDOM_MATCH)"
      >
        <b>随机盲盒</b><span>匹配心动的ta</span
        ><GiftArt kind="box" :size="102" />
      </button>
    </section>
    <div
      v-if="currentMarquee"
      class="tip-ticker"
      :aria-label="currentMarquee.demo ? '模拟打赏播报' : '打赏播报'"
    >
      <GiftArt kind="speaker" :size="27" /><Transition
        name="ticker"
        mode="out-in"
        ><span
          :key="tickerIndex"
          class="ticker-message"
          :title="currentMarquee.text"
          >{{ currentMarquee.text }}</span
        ></Transition
      >
      <small v-if="currentMarquee.demo" class="ticker-demo">模拟</small>
    </div>
    <section v-if="displayPopular.length" class="popular-section">
      <h2 class="section-heading">
        <GiftArt kind="finger" :size="29" />人气店员
      </h2>
      <PopularTalentRail :items="displayPopular" @select="openPopular" />
    </section>
    <section v-if="grids.length" class="home-grid">
      <button
        v-for="(g, i) in grids"
        :key="g.id"
        @click="openEntry(g, EntrySource.GRID)"
      >
        <img v-if="g.imageUrl" :src="g.imageUrl" alt="" /><GiftArt
          v-else
          :kind="['heart', 'box', 'headset', 'crown', 'diamond', 'rose'][i % 6]"
          :size="46"
        /><span>{{ g.title }}</span>
      </button>
    </section>
    <section
      v-if="displayBanners.length"
      class="campaign"
      aria-label="活动横幅"
    >
      <Swipe
        ref="bannerSwipe"
        :autoplay="bannerAutoplay"
        :show-indicators="false"
        @change="bannerIndex = $event"
      >
        <SwipeItem v-for="(banner, i) in displayBanners" :key="banner.id">
          <button
            class="banner-slide"
            :class="`banner-theme-${i % 3}`"
            @click="openEntry(banner, EntrySource.BANNER)"
          >
            <img
              v-if="banner.imageUrl"
              :src="banner.imageUrl"
              :alt="banner.title"
            />
            <template v-else>
              <div class="banner-copy">
                <b>{{ banner.title }}</b>
                <p>{{ banner.subtitle }}</p>
              </div>
              <GiftArt :kind="['heart', 'headset', 'box'][i % 3]" :size="78" />
            </template>
            <small v-if="banner.id < 0" class="banner-demo">演示横幅</small>
          </button>
        </SwipeItem>
      </Swipe>
      <div class="banner-dots">
        <button
          v-for="(b, i) in displayBanners"
          :key="b.id"
          :class="{ on: i === bannerIndex }"
          :aria-label="`第 ${i + 1} 张横幅`"
          :aria-current="i === bannerIndex ? 'true' : undefined"
          @click="bannerSwipe?.swipeTo(i)"
        />
      </div>
    </section>
    <button
      class="voice-banner"
      @click="goEntry('/audition', EntrySource.AUDITION)"
    >
      <GiftArt kind="headset" :size="63" /><span
        ><b>听见心动的声音</b><small>先听声音，再选喜欢的 TA</small></span
      ><em>去试音 ›</em>
    </button>
    <section class="talent-feed">
      <div class="feed-heading">
        <h2 class="section-heading">
          <GiftArt kind="heart" :size="25" />店员推荐
        </h2>
        <div class="feed-filters">
          <button
            :class="{ on: genderFilter === 'FEMALE' }"
            @click="genderFilter = genderFilter === 'FEMALE' ? null : 'FEMALE'"
          >
            女生</button
          ><button
            :class="{ on: genderFilter === 'MALE' }"
            @click="genderFilter = genderFilter === 'MALE' ? null : 'MALE'"
          >
            男生</button
          ><button
            :class="{ on: levelFilter !== null }"
            @click="levelOpen = true"
          >
            {{ levelFilter ? 'LV.' + levelFilter : '等级' }}⌄
          </button>
        </div>
      </div>
      <p v-if="keyword" class="search-result">
        “{{ keyword }}” · {{ displayTotal }} 位
        <button
          @click="
            keyword = '';
            reloadFeed();
          "
        >
          清除 ×
        </button>
      </p>
      <Skeleton v-if="feedLoading" avatar :row="4" />
      <div v-else-if="feedFailed" class="retry">
        <EmptyState image="network" description="加载失败" /><button
          class="primary-btn"
          @click="reloadFeed"
        >
          重新加载
        </button>
      </div>
      <EmptyState
        v-else-if="!displayFeed.length || (arena && !visibleCategories.length)"
        description="暂无符合条件的店员"
      />
      <div v-else class="talent-stack">
        <TalentCardItem
          v-for="t in displayFeed"
          :key="t.id"
          :talent="t"
          :favorited="favorites.includes(t.id)"
          @favorite="
            (value) =>
              (favorites = value
                ? [...favorites, t.id]
                : favorites.filter((id) => id !== t.id))
          "
          @click="open(t.id)"
        /><button
          v-if="feedHasMore"
          class="load-more"
          :disabled="loadingMore"
          @click="loadMore"
        >
          {{ loadingMore ? '加载中…' : '查看更多' }}
        </button>
        <p v-else class="end">没有更多了 · 共 {{ displayTotal }} 位</p>
      </div>
    </section>
    <aside class="floating-tools">
      <button class="coupon-float" @click="router.push('/me/coupons')">
        券<small>优惠券</small></button
      ><button class="service-float" @click="customerService">
        在线<br />客服</button
      ><button
        class="quick-float"
        @click="goEntry('/match', EntrySource.RANDOM_MATCH)"
      >
        快速<br />约玩</button
      ><button
        class="blind-float"
        @click="goEntry('/match', EntrySource.RANDOM_MATCH)"
      >
        心动<br />盲盒
      </button>
    </aside>
    <Popup v-model:show="searchOpen" position="top" round
      ><form class="search-form" @submit.prevent="applySearch">
        <input
          v-model="searchDraft"
          maxlength="60"
          placeholder="搜索店员昵称"
          aria-label="搜索店员昵称"
        /><button class="primary-btn">搜索</button
        ><button type="button" class="text-btn" @click="searchOpen = false">
          取消
        </button>
      </form></Popup
    >
    <Popup v-model:show="levelOpen" position="bottom" round
      ><div class="level-sheet">
        <h3>选择店员等级</h3>
        <div class="chips-row">
          <button
            v-for="n in [null, 1, 2, 3, 4, 5, 6]"
            :key="String(n)"
            class="choice-chip"
            :class="{ selected: levelFilter === n }"
            @click="
              levelFilter = n;
              levelOpen = false;
            "
          >
            {{ n ? 'LV.' + n : '全部等级' }}
          </button>
        </div>
        <button class="text-btn" @click="levelOpen = false">取消</button>
      </div></Popup
    >
    <Popup v-model:show="previewOpen" round closeable class="preview-popup">
      <section v-if="previewOpen && selectedPreview" class="preview-profile">
        <div class="preview-avatar">
          <TalentPortrait
            :seed="selectedPreview.id"
            :name="selectedPreview.nickname"
            :frame="selectedPreview.avatarFrame"
          />
        </div>
        <h2>{{ selectedPreview.nickname }} <small>模拟主播</small></h2>
        <p>
          {{ selectedPreview.gender === 'MALE' ? '♂' : '♀' }}
          {{ selectedPreview.age }} · {{ selectedPreview.region }} · LV.{{
            selectedPreview.level
          }}
        </p>
        <p v-if="selectedPreview.avatarFrame" class="preview-frame-name">
          {{ avatarFrameNames[selectedPreview.avatarFrame] }}
        </p>
        <AudioPreview
          v-if="selectedPreview.audioUrl"
          :url="selectedPreview.audioUrl"
          :seconds="selectedPreview.audioSeconds"
        />
        <p class="preview-services">{{ selectedPreview.tags.join(' · ') }}</p>
        <p class="preview-notice">{{ selectedPreview.intro }}</p>
        <button class="primary-btn" @click="togglePreviewFavorite">
          {{
            favorites.includes(selectedPreview.id)
              ? '已收藏 · 点击取消'
              : '收藏这位模拟主播'
          }}
        </button>
        <button class="text-btn" @click="previewOpen = false">返回列表</button>
      </section>
    </Popup>
    <SupportDialog v-if="supportOpen" v-model:show="supportOpen" />
    <FanTabBar />
  </div>
</template>
<style scoped>
.preview-popup {
  width: min(340px, 90vw);
}
.preview-profile {
  padding: 32px 24px 20px;
  text-align: center;
}
.preview-avatar {
  width: 160px;
  height: 160px;
  border-radius: 18px;
  margin: 0 auto 18px;
}
.preview-profile h2 {
  font-size: 22px;
}
.preview-profile h2 small {
  font-size: 10px;
  font-weight: 400;
  color: #9693a9;
}
.preview-profile p {
  color: #9992a7;
  font-size: 12px;
  line-height: 1.8;
}
.preview-profile .preview-notice {
  background: #f8f6fc;
  padding: 12px;
  border-radius: 10px;
  margin: 18px 0;
}
.preview-profile > button:not(.audio) {
  display: block;
  width: 100%;
  margin-top: 10px;
}
.home-page {
  height: 100dvh;
  overflow-y: auto;
  background: var(--g-page);
  padding: 20px 15px
    calc(var(--fan-tabbar-height) + 24px + env(safe-area-inset-bottom, 0px));
}
.home-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 1px 15px;
}
.main-tab {
  position: relative;
  background: none;
  border: 0;
  padding: 3px 0 10px;
  font-size: 17px;
  color: #333;
  white-space: nowrap;
}
.main-tab.active {
  font-weight: 750;
  font-size: 24px;
}
.main-tab.active:after {
  content: '';
  position: absolute;
  width: 25px;
  height: 9px;
  border-bottom: 4px solid #23e3cc;
  border-radius: 50%;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
}
.game-tab {
  font-size: 17px;
}
.search-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: #fff8;
  border-radius: 28px;
  color: #a1abad;
  text-align: left;
  padding: 6px 12px;
  flex: 1;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 40px;
}
.search-pill span {
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 20px;
}
.search-pill .van-icon {
  font-size: 20px;
  flex: none;
}
.trophy-button {
  border: 0;
  background: none;
  padding: 0;
}
.category-strip {
  display: flex;
  gap: 21px;
  overflow: auto;
  white-space: nowrap;
  padding: 5px 3px 12px;
  scrollbar-width: none;
}
.category-strip button {
  border: 0;
  background: none;
  font-size: 14px;
  color: #697774;
  padding: 0 0 4px;
}
.category-strip .active {
  color: #00ada6;
  border-bottom: 2px solid;
}
.entry-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 11px;
}
.entry-tile {
  position: relative;
  overflow: hidden;
  text-align: left;
  border: 1.5px solid #fff;
  border-radius: 13px;
  height: 75px;
  padding: 11px;
  color: #183541;
  box-shadow: 0 0 1px #fff;
}
.entry-tile b {
  display: block;
  font-size: 18px;
  font-weight: 800;
  position: relative;
  z-index: 2;
}
.entry-tile > span:not(.gift-art) {
  display: block;
  font-size: 13px;
  color: #916d7c;
  margin-top: 7px;
  position: relative;
  z-index: 2;
}
.entry-tile > .gift-art {
  position: absolute;
  right: -19px;
  bottom: -27px;
  transform: rotate(-8deg);
}
.discover {
  background: linear-gradient(115deg, #ffecd5, #ffbfd7);
}
.blind {
  background: linear-gradient(115deg, #d6fff6, #65d3f7);
}
.blind > span:not(.gift-art) {
  color: #4c9ea7;
}
.tip-ticker {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 42px;
  margin-top: 15px;
  padding: 0 12px;
  background: linear-gradient(100deg, #c2effc, #f5e8fd);
  border-radius: 24px;
  color: #232963;
  font-size: 13px;
  overflow: hidden;
}
.tip-ticker .ticker-message {
  flex: 1;
  min-width: 0;
  font-size: clamp(11px, 3vw, 14px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ticker-demo {
  flex: none;
  font-size: 9px;
  color: #807ba4;
  padding: 2px 4px;
  border: 1px solid #ffffff9c;
  border-radius: 5px;
}
@media (prefers-reduced-motion: reduce) {
  .ticker-enter-active,
  .ticker-leave-active {
    transition: none;
  }
}
.ticker-enter-active,
.ticker-leave-active {
  transition:
    transform 0.3s,
    opacity 0.3s;
}
.ticker-enter-from {
  transform: translateY(20px);
  opacity: 0;
}
.ticker-leave-to {
  transform: translateY(-20px);
  opacity: 0;
}
.home-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 16px;
  padding: 14px 0;
  background: white;
  border-radius: 14px;
  gap: 14px;
}
.home-grid button {
  background: none;
  border: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  font-size: 12px;
  gap: 7px;
}
.home-grid img {
  width: 46px;
  height: 46px;
  object-fit: cover;
}
.campaign {
  position: relative;
  margin: 18px 0 0;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(110deg, #ffeddf, #fbbce1);
  min-height: 95px;
  cursor: pointer;
}
.banner-slide {
  width: 100%;
  height: 108px;
  border: 0;
  display: flex;
  align-items: center;
  padding: 18px;
  text-align: left;
  position: relative;
  overflow: hidden;
  color: #3f3965;
  background: linear-gradient(110deg, #ffeddf, #fbbce1);
}
.banner-theme-1 {
  background: linear-gradient(110deg, #d3e8ff, #e8d0ff);
}
.banner-theme-2 {
  background: linear-gradient(110deg, #d4fff2, #a1def5);
}
.banner-slide > img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.banner-copy {
  position: relative;
  z-index: 1;
  max-width: 82%;
}
.campaign b {
  font-size: 17px;
  line-height: 1.5;
}
.campaign p {
  font-size: 11px;
  margin: 6px 0 0;
  color: #786a8c;
}
.campaign .gift-art {
  position: absolute;
  right: 4px;
  top: 17px;
  opacity: 0.72;
}
.banner-demo {
  position: absolute;
  right: 8px;
  top: 5px;
  font-size: 9px;
  color: #897b9e;
}
.banner-dots {
  position: absolute;
  bottom: 5px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 4px;
}
.banner-dots button {
  width: 20px;
  height: 18px;
  border: 0;
  padding: 6px;
  background: none;
}
.banner-dots button::after {
  content: '';
  display: block;
  height: 6px;
  border-radius: 4px;
  background: #fff9;
}
.banner-dots .on {
  width: 27px;
}
.banner-dots .on::after {
  background: white;
}
.voice-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-top: 17px;
  padding: 8px 10px;
  border: 1px solid white;
  border-radius: 12px;
  background: linear-gradient(105deg, #c0dafc, #edc9fc 70%, #fed5e5);
  text-align: left;
  color: #6458a1;
}
.voice-banner > span:not(.gift-art) {
  flex: 1;
}
.voice-banner b {
  display: block;
  font-size: 16px;
  color: #57519a;
}
.voice-banner small {
  display: block;
  font-size: 10px;
  margin-top: 7px;
}
.voice-banner em {
  font-size: 10px;
  font-style: normal;
  white-space: nowrap;
  color: white;
  background: linear-gradient(110deg, #c079ff, #f593cc);
  border: 1px solid #fff9;
  border-radius: 15px;
  padding: 5px 7px;
}
.feed-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.feed-filters {
  margin-right: 27px;
  display: flex;
  gap: 8px;
  white-space: nowrap;
}
.feed-filters button {
  border: 0;
  background: none;
  font-size: 12px;
  padding: 0;
  color: #888;
}
.feed-filters .on {
  color: #0bb5aa;
}
.talent-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.end,
.load-more {
  text-align: center;
  color: #a0a5ac;
  font-size: 11px;
  padding: 10px;
  border: 0;
  background: none;
}
.floating-tools {
  position: fixed;
  right: max(5px, calc((100vw - var(--app-max-width)) / 2 + 5px));
  bottom: 104px;
  z-index: 15;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.floating-tools button {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid #fffa;
  color: white;
  font-size: 11px;
  font-weight: 800;
  line-height: 12px;
  padding: 0;
  text-shadow: 0 1px 2px #595c98;
  box-shadow:
    inset 2px 2px 3px #fff9,
    0 2px 5px #b6bac055;
}
.floating-tools .coupon-float {
  border-radius: 6px;
  background: linear-gradient(130deg, #ff9a6b, #ff304d);
  height: 46px;
  font-size: 17px;
  margin-bottom: 16px;
}
.coupon-float small {
  display: block;
  font-size: 9px;
  margin-top: 5px;
}
.service-float {
  background: linear-gradient(135deg, #8cacff, #00c4f4);
}
.quick-float {
  background: linear-gradient(135deg, #ffb8e2, #ff3ea2);
}
.blind-float {
  background: linear-gradient(135deg, #efadff, #b900ff);
}
.search-form {
  display: flex;
  gap: 8px;
  padding: 20px;
  max-width: 750px;
  margin: auto;
}
.search-form input {
  border: 0;
  background: #f1f4f6;
  border-radius: 20px;
  padding: 10px 15px;
  width: 0;
  flex: 1;
}
.search-form .primary-btn {
  padding: 9px 15px;
  white-space: nowrap;
}
.text-btn {
  background: none;
  border: 0;
  color: #888;
  padding: 10px;
  white-space: nowrap;
}
.level-sheet {
  padding: 10px 20px 25px;
}
.level-sheet h3 {
  text-align: center;
}
.level-sheet > .text-btn {
  display: block;
  margin: 15px auto 0;
}
.search-result {
  color: #888;
  font-size: 12px;
}
.search-result button {
  background: none;
  border: 0;
  color: #0dbfb5;
}
.retry {
  text-align: center;
}
@media (min-width: 600px) {
  .home-page {
    padding-left: 24px;
    padding-right: 24px;
  }
  .entry-tile {
    height: 130px;
    padding: 18px;
  }
  .entry-tile b {
    font-size: 24px;
  }
  .entry-tile > .gift-art {
    right: 10px;
    bottom: -10px;
  }
  .home-header {
    gap: 28px;
  }
}
</style>
