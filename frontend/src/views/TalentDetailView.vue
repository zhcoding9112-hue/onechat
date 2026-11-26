<script setup lang="ts">
import { computed, onMounted, ref, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Skeleton, NoticeBar, Popup, showToast, showImagePreview } from 'vant';
import Portrait from '@/components/Portrait.vue';
import GiftArt from '@/components/GiftArt.vue';
import GiftPreview from '@/components/GiftPreview.vue';
import { feedApi, type FeedPostView } from '@/api/feed';
import AudioPreview from '@/components/AudioPreview.vue';
import { talentApi, type TalentDetail } from '@/api/talent';
import { chatApi } from '@/api/chat';
import { ApiError } from '@/api/http';
import { formatPrice } from '@/utils/money';
import {
  ClientEvent,
  ENTRY_QUERY,
  entryFromRoute,
  track,
} from '@/api/analytics';
import { savePendingIntent, takePendingIntent } from '@/utils/pendingIntent';
import EmptyState from '@/components/EmptyState.vue';

const OrderCreate = defineAsyncComponent(() => import('./OrderCreateView.vue'));
const route = useRoute();
const router = useRouter();
const detail = ref<TalentDetail | null>(null);
const loading = ref(true);
const failed = ref(false);
const selectedSpecId = ref<number | null>(null);
const favoriting = ref(false);
const opening = ref(false);
const tab = ref('price');
const checkout = ref(false);
const posts = ref<FeedPostView[]>([]);
const previewGift = ref(false);
const giftKind = ref('heart');
const gifts = ['heart', 'rose', 'diamond', 'crown', 'castle', 'ring'];
const giftNames = [
  '怦然心动',
  '玫瑰花束',
  '璀璨星光',
  '为你加冕',
  '梦幻城堡',
  '心动约定',
];
const gallery = computed(() =>
  [
    ...new Set([
      ...(detail.value?.photos ?? []),
      ...(detail.value?.coverUrl &&
      import.meta.env.VITE_ILLUSTRATED_AVATARS !== 'true'
        ? [detail.value.coverUrl]
        : []),
      ...posts.value.flatMap((p) => p.images),
    ]),
  ].slice(0, 12),
);

const talentId = computed(() => Number(route.params.id));

/**
 * 粉丝是从哪个入口来的。
 *
 * 在 setup 里取一次定住，不做成 computed：下面的跳转会改路由，
 * 而「这次进详情的来源」是进来那一刻的事实，不该随路由变化。
 * 分享链接进来的没有这个参数，解析出来就是直接访问——11.3 要的正是这个。
 */
const entrySource = entryFromRoute(route.query[ENTRY_QUERY]);

function genderMark(g: TalentDetail['gender']) {
  if (g === 'FEMALE') return '♀';
  if (g === 'MALE') return '♂';
  return null;
}

onMounted(async () => {
  // 授权跳转回来后，先把挂起的收藏补做掉，再拉详情——
  // 顺序反了的话详情里的 favorited 还是旧值，按钮会停在未收藏态。
  const pending = takePendingIntent();
  if (pending?.type === 'favorite' && pending.talentId === talentId.value) {
    try {
      await talentApi.favorite(talentId.value);
      showToast('已收藏');
    } catch {
      // 补做失败不该打断页面，粉丝可以自己再点一次
    }
  }

  try {
    detail.value = await talentApi.detail(talentId.value);
    void feedApi
      .byTalent(talentId.value, 20)
      .then((result) => (posts.value = result))
      .catch(() => {});
    const first = detail.value.services[0]?.specs[0];
    selectedSpecId.value = first?.serviceSpecId ?? null;
    track(ClientEvent.VIEW_TALENT_DETAIL, {
      talentId: talentId.value,
      serviceItemId: detail.value.services[0]?.serviceItemId,
      entrySource,
    });
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
});

async function toggleFavorite() {
  if (!detail.value || favoriting.value) return;
  const next = !detail.value.favorited;
  favoriting.value = true;

  // 请求前先把意图存下：未登录时 http 层会整页跳去授权，
  // 代码不会走到 catch，存在跳转之后才来得及。
  if (next) {
    savePendingIntent({ type: 'favorite', talentId: talentId.value });
  }

  try {
    if (next) {
      await talentApi.favorite(talentId.value);
    } else {
      await talentApi.unfavorite(talentId.value);
    }
    detail.value.favorited = next;
    showToast(next ? '已收藏' : '已取消收藏');
    // 这次已经成功，清掉挂起意图，避免下次进页面又补做一遍
    takePendingIntent();
  } finally {
    favoriting.value = false;
  }
}

/** 进聊天。会话由服务端「有就打开、没有才建」，连点两下也只会有一个会话。 */
async function openChat() {
  if (opening.value) return;
  opening.value = true;
  try {
    const { conversationId } = await chatApi.openWithTalent(talentId.value);
    await router.push(`/me/chats/${conversationId}`);
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
  } finally {
    opening.value = false;
  }
}

function order(specId?: number) {
  const target = specId ?? selectedSpecId.value ?? undefined;
  track(ClientEvent.CLICK_ORDER, {
    talentId: talentId.value,
    serviceItemId: detail.value?.services[0]?.serviceItemId,
    entrySource,
  });
  // 来源继续往下传：真正落进订单快照是在下单页提交那一刻
  selectedSpecId.value = target ?? null;
  checkout.value = true;
}
</script>
<template>
  <div class="detail-page">
    <Skeleton v-if="loading" avatar :row="6" /><EmptyState
      v-else-if="failed || !detail"
      description="主播资料加载失败"
    /><template v-else
      ><section class="detail-hero">
        <div class="hero-backdrop">
          <Portrait :src="detail.coverUrl" :seed="detail.id" />
        </div>
        <nav class="detail-nav">
          <button aria-label="返回" @click="router.back()">‹</button
          ><span>店员详情</span
          ><button
            :disabled="favoriting"
            :aria-label="detail.favorited ? '取消收藏' : '收藏主播'"
            @click="toggleFavorite"
          >
            {{ detail.favorited ? '♥' : '♡' }}
          </button>
        </nav>
        <div class="detail-person">
          <div class="detail-avatar">
            <Portrait
              :src="detail.coverUrl"
              :seed="detail.id"
              :name="detail.nickname"
              round
            />
          </div>
          <div class="person-title">
            <h1>
              {{ detail.nickname
              }}<span v-if="genderMark(detail.gender)" class="gender"
                >{{ genderMark(detail.gender) }}{{ detail.age || '' }}</span
              >
            </h1>
            <div class="person-meta">
              <span v-if="detail.level" class="level"
                >♛ LV.{{ detail.level }}</span
              ><span>{{ detail.online ? '● 在线' : '○ 离线' }}</span
              ><span v-if="detail.region">{{ detail.region }}</span>
            </div>
            <AudioPreview
              v-if="detail.audioUrl"
              :url="detail.audioUrl"
              :seconds="detail.audioSeconds"
              class="pink-audio"
            />
          </div>
        </div>
        <p v-if="detail.intro" class="detail-intro">{{ detail.intro }}</p>
        <div class="detail-tags">
          <span
            v-for="tag in [...detail.promoTags, ...detail.tags]"
            :key="tag"
            >{{ tag }}</span
          >
        </div>
        <div class="photo-strip">
          <button
            v-for="(url, i) in gallery"
            :key="url"
            @click="showImagePreview({ images: gallery, startPosition: i })"
          >
            <img :src="url" alt="主播相册" />
          </button>
          <div v-if="!gallery.length" class="illustration-photo">
            <Portrait :seed="detail.id" /><small>插画头像</small>
          </div>
        </div>
      </section>
      <section class="detail-content">
        <div class="gift-wall">
          <div>
            <b>礼物墙</b><span>送一份心意给 TA ›</span
            ><button @click="router.push(`/talents/${talentId}/tip`)">
              去打赏
            </button>
          </div>
          <div class="gift-line">
            <button
              v-for="(g, i) in gifts"
              :key="g"
              @click="
                giftKind = g;
                previewGift = true;
              "
            >
              <GiftArt :kind="g" :size="40" /><span>{{ giftNames[i] }}</span>
            </button>
          </div>
        </div>
        <NoticeBar
          v-if="!detail.available"
          :text="detail.unavailableReason ?? '暂不可约'"
        /><NoticeBar
          v-else-if="!detail.online"
          text="该店员当前离线，接单可能较慢"
          color="#a0a0a0"
          background="#fafafa"
        />
        <nav class="detail-tabs">
          <button :class="{ active: tab === 'price' }" @click="tab = 'price'">
            价格表</button
          ><button :class="{ active: tab === 'feed' }" @click="tab = 'feed'">
            动态</button
          ><button
            :class="{ active: tab === 'reviews' }"
            @click="tab = 'reviews'"
          >
            评价
          </button>
        </nav>
        <div v-if="tab === 'price'" class="price-pane">
          <table v-if="detail.services.length" class="price-table">
            <thead>
              <tr>
                <th>服务项目</th>
                <th>时长 / 档位</th>
                <th>价格</th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="group in detail.services"
                :key="group.serviceItemId"
                ><tr
                  v-for="(spec, i) in group.specs"
                  :key="spec.serviceSpecId"
                  @click="order(spec.serviceSpecId)"
                >
                  <th v-if="i === 0" :rowspan="group.specs.length">
                    {{ group.name }}
                  </th>
                  <td>{{ spec.name }}</td>
                  <td>
                    <button>
                      {{ formatPrice(spec.unitPriceCents)
                      }}<small>下单 ›</small>
                    </button>
                  </td>
                </tr></template
              >
            </tbody>
          </table>
          <EmptyState
            v-else
            :description="detail.unavailableReason ?? '暂无服务'"
          />
          <p class="price-note">点击服务档位即可下单 · 价格以订单确认为准</p>
        </div>
        <div v-else-if="tab === 'feed'" class="detail-posts">
          <EmptyState v-if="!posts.length" description="TA 还没有发布动态" />
          <article
            v-for="post in posts"
            :key="post.id"
            @click="router.push(`/feed/${post.id}`)"
          >
            <time>{{
              new Date(post.createdAt).toLocaleDateString('zh-CN')
            }}</time>
            <p>{{ post.content }}</p>
            <div>
              <img
                v-for="img in post.images.slice(0, 3)"
                :key="img"
                :src="img"
                alt="动态图片"
              />
            </div>
            <small
              >♡ {{ post.likeCount }}　评论 {{ post.commentCount }} ›</small
            >
          </article>
        </div>
        <div v-else class="review-pane">
          <span>☆ ☆ ☆ ☆ ☆</span>
          <h3>每一次陪伴，都认真对待</h3>
          <p>
            服务完成后可在订单中提交满意度。<br />你的评分仅用于服务改进，不公开展示。
          </p>
          <button class="choice-chip" @click="router.push('/orders')">
            查看我的订单
          </button>
        </div>
        <div class="service-guarantee">
          <span>✓ 订单可追踪</span><span>✓ 人工核对收款</span
          ><span>✓ 服务反馈</span>
        </div>
      </section>
      <button class="chat-orb" :disabled="opening" @click="openChat">
        •••<small>聊一聊</small>
      </button>
      <footer class="detail-footer">
        <button class="home" @click="router.push('/')">
          ⌂<small>首页</small></button
        ><button class="tip" @click="router.push(`/talents/${talentId}/tip`)">
          打赏</button
        ><button class="order" :disabled="!detail.available" @click="order()">
          {{ detail.available ? '立即下单' : '暂不可约' }}
        </button>
      </footer>
      <Popup v-model:show="checkout" position="bottom" round close-on-popstate
        ><OrderCreate
          v-if="checkout"
          embedded
          :preset-spec="selectedSpecId"
          @close="checkout = false" /></Popup
      ><GiftPreview
        :show="previewGift"
        :kind="giftKind"
        @close="previewGift = false"
    /></template>
  </div>
</template>
<style scoped>
.detail-page {
  min-height: 100dvh;
  padding-bottom: 76px;
  background: #fff;
}
.detail-hero {
  position: relative;
  padding: 0 18px 22px;
  overflow: hidden;
  color: #fff;
  background: #a7bac6;
}
.hero-backdrop {
  position: absolute;
  inset: -30px;
  filter: blur(20px);
  transform: scale(1.12);
  opacity: 0.67;
}
.hero-backdrop:after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(#293a4f33, #3c485e55);
}
.detail-nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  font-size: 14px;
  color: #fff;
}
.detail-nav button {
  background: none;
  border: 0;
  color: white;
  padding: 5px 0;
  font-size: 28px;
  min-width: 25px;
}
.detail-person {
  position: relative;
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 13px 5px;
}
.detail-avatar {
  display: grid;
  place-items: center;
  width: 84px;
  height: 84px;
  flex: none;
}
.person-title h1 {
  font-size: 21px;
  font-weight: 600;
  margin: 0 0 7px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.gender {
  background: #f6a7c7;
  color: white;
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 9px;
  font-weight: 400;
}
.person-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 10px;
  color: #fffd;
}
.level {
  background: linear-gradient(110deg, #e4e9ff, #d3b9fd);
  color: #8f68bf;
  font-size: 9px;
  padding: 2px 6px;
  border: 1px solid #fff9;
  border-radius: 12px;
}
.pink-audio {
  margin-top: 10px;
  background: #ef94c1 !important;
  color: white !important;
}
.detail-intro {
  position: relative;
  margin: 9px 0 12px;
  font-size: 12px;
  line-height: 1.8;
  color: #fffe;
}
.detail-tags {
  position: relative;
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  margin: 8px 0 16px;
}
.detail-tags span {
  font-size: 10px;
  background: #ffffff27;
  padding: 4px 8px;
  border-radius: 5px;
}
.photo-strip {
  display: flex;
  gap: 10px;
  position: relative;
  overflow: auto;
  scrollbar-width: none;
}
.photo-strip button,
.illustration-photo {
  width: 95px;
  height: 110px;
  flex: none;
  padding: 0;
  border: 0;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
  background: none;
}
.photo-strip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.illustration-photo small {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  color: white;
  background: #0003;
  font-size: 9px;
  text-align: center;
  padding: 3px;
}
.detail-content {
  position: relative;
  border-radius: 20px 20px 0 0;
  margin-top: -10px;
  background: white;
  padding: 15px;
}
.gift-wall {
  background: linear-gradient(120deg, #e0fff2, #e8fafc);
  border-radius: 12px;
  padding: 12px 10px;
  margin-bottom: 15px;
}
.gift-wall > div:first-child {
  display: flex;
  gap: 10px;
  align-items: center;
}
.gift-wall b {
  font-size: 14px;
  font-weight: 500;
  color: #4e867b;
}
.gift-wall > div:first-child > span {
  font-size: 9px;
  color: #85a9a2;
  flex: 1;
}
.gift-wall > div:first-child > button {
  background: #fff;
  border: 0;
  border-radius: 14px;
  padding: 4px 8px;
  color: #0dbeab;
  font-size: 10px;
}
.gift-line {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  margin-top: 14px;
  gap: 3px;
}
.gift-line button {
  background: none;
  border: 0;
  padding: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
}
.gift-line button > span:not(.gift-art) {
  font-size: 8px;
  color: #8eaaa5;
  white-space: nowrap;
}
.detail-tabs {
  display: flex;
  justify-content: space-around;
  border-bottom: 1px solid #f3f4f4;
  margin: 10px 0 18px;
}
.detail-tabs button {
  background: none;
  border: 0;
  color: #999;
  font-size: 16px;
  padding: 12px 12px;
  position: relative;
}
.detail-tabs .active {
  font-weight: 600;
  color: #00b7aa;
}
.detail-tabs .active:after {
  content: '';
  height: 3px;
  width: 25px;
  background: #0dbfb5;
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  border-radius: 5px;
}
.price-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  text-align: center;
  color: #67b9b0;
}
.price-table th,
.price-table td {
  border: 1px solid #b9e7df;
  padding: 10px 4px;
  font-weight: 400;
}
.price-table thead {
  background: #effdf8;
}
.price-table tbody th {
  width: 29%;
  color: #56a99f;
}
.price-table tbody td:nth-last-child(2) {
  color: #7b9e98;
}
.price-table button {
  background: none;
  border: 0;
  color: #1ab5a3;
  font-size: 12px;
  padding: 0;
}
.price-table small {
  display: block;
  font-size: 8px;
  color: #9dcac4;
  margin-top: 4px;
}
.price-note {
  font-size: 9px;
  color: #a9b4b2;
  text-align: center;
  margin: 14px 0;
}
.service-guarantee {
  display: flex;
  justify-content: space-around;
  font-size: 10px;
  color: #9cb8b1;
  background: #f7fcf9;
  padding: 12px 4px;
  border-radius: 7px;
  margin-top: 25px;
}
.chat-orb {
  position: fixed;
  right: max(12px, calc((100vw - var(--app-max-width)) / 2 + 12px));
  bottom: 90px;
  background: linear-gradient(#c9a2ff, #aa71ed);
  border: 2px solid white;
  box-shadow: 0 2px 8px #bfa3db55;
  color: white;
  width: 47px;
  height: 47px;
  border-radius: 50%;
  font-size: 19px;
  font-weight: 700;
  line-height: 15px;
  z-index: 12;
}
.chat-orb small {
  display: block;
  font-size: 9px;
  font-weight: 400;
}
.detail-footer {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, var(--app-max-width));
  background: #0dbfb5;
  color: white;
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 20;
  height: calc(55px + env(safe-area-inset-bottom));
}
.detail-footer button {
  background: none;
  border: 0;
  color: white;
  font-size: 15px;
  padding: 0;
}
.detail-footer .home {
  width: 20%;
  font-size: 23px;
  line-height: 22px;
  border-right: 1px solid #fff3;
}
.detail-footer small {
  display: block;
  font-size: 9px;
  line-height: 15px;
}
.detail-footer .tip {
  width: 30%;
  border-right: 1px solid #fff3;
}
.detail-footer .order {
  flex: 1;
  font-size: 17px;
}
.detail-posts article {
  padding: 15px 3px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}
.detail-posts time,
.detail-posts small {
  font-size: 11px;
  color: #aaa;
}
.detail-posts p {
  font-size: 13px;
  line-height: 1.8;
}
.detail-posts article > div {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.detail-posts img {
  width: 29%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 7px;
}
.review-pane {
  text-align: center;
  padding: 25px 0;
  color: #999;
}
.review-pane > span {
  font-size: 26px;
  color: #f0dba3;
}
.review-pane h3 {
  font-size: 13px;
  font-weight: 400;
  margin-top: 20px;
}
.review-pane p {
  font-size: 11px;
  line-height: 2;
}
.review-pane button {
  font-size: 12px;
  margin-top: 9px;
}
</style>
