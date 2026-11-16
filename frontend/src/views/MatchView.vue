<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar, showToast } from 'vant'
import AudioPreview from '@/components/AudioPreview.vue'
import { matchApi, type MatchedTalent } from '@/api/match'
import { homeApi, type CategoryView } from '@/api/home'
import type { Gender } from '@/api/talent'

const router = useRouter()

const categories = ref<CategoryView[]>([])
const category = ref<string | null>(null)
const gender = ref<Gender | null>(null)
const onlineOnly = ref(false)
const excludeOrdered = ref(false)

const result = ref<MatchedTalent | null>(null)
const emptyMessage = ref<string | null>(null)
const matching = ref(false)
/** 已经看过的，用于「换一位」时避开。 */
const seen = ref<number[]>([])

onMounted(async () => {
  try {
    categories.value = await homeApi.categories()
  } catch {
    categories.value = []
  }
})

async function run(fresh: boolean) {
  if (matching.value) return
  matching.value = true
  if (fresh) {
    seen.value = []
  }
  try {
    const res = await matchApi.random({
      category: category.value,
      gender: gender.value,
      onlineOnly: onlineOnly.value,
      excludeOrdered: excludeOrdered.value,
      exclude: seen.value,
    })
    if (res.matched && res.talent) {
      result.value = res.talent
      emptyMessage.value = null
      if (!seen.value.includes(res.talent.id)) {
        seen.value = seen.value.concat(res.talent.id)
      }
    } else {
      result.value = null
      emptyMessage.value = res.message ?? '暂时没有符合条件的主播'
    }
  } finally {
    matching.value = false
  }
}

function toggleGender(g: Gender) {
  gender.value = gender.value === g ? null : g
}

function genderMark(g: Gender | null) {
  if (g === 'FEMALE') return '♀'
  if (g === 'MALE') return '♂'
  return null
}

function initial(nickname: string) {
  return nickname.trim().charAt(0) || '·'
}

function order() {
  if (!result.value) return
  router.push(`/talents/${result.value.id}/order`)
}

function openDetail() {
  if (!result.value) return
  router.push(`/talents/${result.value.id}`)
}

function copyHint() {
  showToast('换一位不收费，随便换')
}
</script>

<template>
  <div class="page">
    <NavBar title="随机匹配" left-arrow @click-left="$router.back()" />

    <section class="intro-card">
      <h2>随机遇见一位</h2>
      <p>按你的条件随机挑一位可接单的主播。<strong>匹配不收费，也不消耗次数。</strong></p>
    </section>

    <section class="filters">
      <div class="row">
        <span class="label">服务</span>
        <div class="chips">
          <button :class="{ on: category === null }" @click="category = null">不限</button>
          <button
            v-for="c in categories.slice(0, 6)"
            :key="c.code"
            :class="{ on: category === c.code }"
            @click="category = category === c.code ? null : c.code"
          >
            {{ c.name }}
          </button>
        </div>
      </div>

      <div class="row">
        <span class="label">性别</span>
        <div class="chips">
          <button :class="{ on: gender === null }" @click="gender = null">不限</button>
          <button :class="{ on: gender === 'FEMALE' }" @click="toggleGender('FEMALE')">女生</button>
          <button :class="{ on: gender === 'MALE' }" @click="toggleGender('MALE')">男生</button>
        </div>
      </div>

      <div class="row switches">
        <label><input v-model="onlineOnly" type="checkbox" />只匹配在线</label>
        <label><input v-model="excludeOrdered" type="checkbox" />排除下过单的</label>
      </div>
    </section>

    <!-- 结果 -->
    <section v-if="result" class="result">
      <div class="card" @click="openDetail">
        <div class="cover">
          <img v-if="result.coverUrl" :src="result.coverUrl" alt="" />
          <span v-else class="fallback">{{ initial(result.nickname) }}</span>
          <span class="badge" :class="{ on: result.online }">
            <i class="dot"></i>{{ result.online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="who">
          <span class="name">{{ result.nickname }}</span>
          <span v-if="genderMark(result.gender)" class="gender" :class="result.gender?.toLowerCase()">
            {{ genderMark(result.gender) }}<i v-if="result.age" class="num">{{ result.age }}</i>
          </span>
          <span v-if="result.region" class="region">· {{ result.region }}</span>
        </div>
        <p v-if="result.intro" class="desc">{{ result.intro }}</p>
        <AudioPreview v-if="result.audioUrl" :url="result.audioUrl" :seconds="result.audioSeconds" />
      </div>

      <div class="ops">
        <button class="again" :disabled="matching" @click="run(false)">换一位</button>
        <button class="go" @click="order">就 TA 了，下单</button>
      </div>
      <p class="free" @click="copyHint">已看过 {{ seen.length }} 位 · 换人不收费</p>
    </section>

    <section v-else-if="emptyMessage" class="empty">
      <p>{{ emptyMessage }}</p>
      <button class="retry" @click="run(true)">重新匹配</button>
    </section>

    <section v-else class="start">
      <button class="big" :disabled="matching" @click="run(true)">
        {{ matching ? '匹配中…' : '开始匹配' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--g-page);
  background-attachment: fixed;
  padding-bottom: var(--sp-8);
}

.intro-card {
  margin: var(--sp-2) var(--sp-4) var(--sp-4);
  background: var(--g-op-cool);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.intro-card h2 {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-xl);
  font-weight: 700;
}

.intro-card p {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--c-text-2);
  line-height: 1.6;
}

.intro-card strong {
  color: var(--c-text);
}

.filters {
  margin: 0 var(--sp-4) var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  box-shadow: var(--sd-card);
}

.row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.row:last-child {
  margin-bottom: 0;
}

.label {
  font-size: var(--fs-md);
  color: var(--c-text-3);
  flex: none;
  padding-top: 5px;
  width: 36px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  flex: 1;
}

.chips button {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--r-pill);
  padding: 5px 14px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
}

.chips button.on {
  background: var(--c-brand-soft);
  border-color: var(--c-brand);
  color: var(--c-brand-deep);
  font-weight: 600;
}

.switches {
  gap: var(--sp-5);
  font-size: var(--fs-md);
  color: var(--c-text-2);
}

.switches label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

/* ---------- 结果 ---------- */
.result {
  margin: 0 var(--sp-4);
}

.card {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  box-shadow: var(--sd-card);
  cursor: pointer;
}

.cover {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--r-md);
  overflow: hidden;
  background: linear-gradient(150deg, #e7eef0, #dbe4e8);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sp-3);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fallback {
  font-size: 56px;
  font-weight: 300;
  color: #fff;
}

.badge {
  position: absolute;
  left: var(--sp-3);
  top: var(--sp-3);
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  background: rgba(255, 255, 255, 0.92);
  border-radius: var(--r-pill);
  padding: 3px 10px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-offline);
}

.badge.on {
  color: var(--c-online);
}

.badge.on .dot {
  background: var(--c-online);
}

.who {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.name {
  font-size: var(--fs-xl);
  font-weight: 700;
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

.desc {
  margin: var(--sp-2) 0 var(--sp-3);
  font-size: var(--fs-md);
  color: var(--c-text-2);
  line-height: 1.6;
}

.ops {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
}

.again,
.go {
  flex: 1;
  height: 46px;
  border-radius: var(--r-md);
  font-size: var(--fs-lg);
  font-weight: 600;
  cursor: pointer;
}

.again {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text-2);
}

.go {
  border: none;
  background: var(--c-brand);
  color: #fff;
}

.free {
  margin: var(--sp-3) 0 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

/* ---------- 空与起始 ---------- */
.empty,
.start {
  margin: var(--sp-6) var(--sp-4) 0;
  text-align: center;
}

.empty p {
  font-size: var(--fs-md);
  color: var(--c-text-2);
  margin: 0 0 var(--sp-4);
}

.retry,
.big {
  border: none;
  background: var(--c-brand);
  color: #fff;
  border-radius: var(--r-md);
  height: 48px;
  padding: 0 var(--sp-8);
  font-size: var(--fs-lg);
  font-weight: 600;
  cursor: pointer;
}

.big {
  width: 100%;
}
</style>
