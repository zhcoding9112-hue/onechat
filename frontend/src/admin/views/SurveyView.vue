<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  surveyAdminApi,
  type SurveyAggregate,
  type SurveyDetailEntry,
} from '@/admin/api/survey'
import { talentAdminApi, type TalentAdminView } from '@/admin/api/talent'
import { ApiError } from '@/api/http'

/** 一行 = 一位主播的评分情况。接口不带昵称，这里和主播列表 join 出来。 */
interface Row {
  talentId: number
  nickname: string
  listed: boolean
  count: number
  average: number | null
  distribution: Record<string, number>
}

const SCORES = [5, 4, 3, 2, 1] as const

/** 均值低于这个数就标红。3 分是「说不上好也说不上差」的中位。 */
const LOW_AVERAGE = 3

const rows = ref<Row[]>([])
const loading = ref(false)
/** 没人评过分的主播默认折起来，否则一屏全是「暂无评分」。 */
const onlyRated = ref(true)

const detail = reactive({
  visible: false,
  talentId: 0,
  nickname: '',
  loading: false,
  aggregate: null as SurveyAggregate | null,
  entries: [] as SurveyDetailEntry[],
})

const shown = computed(() =>
  onlyRated.value ? rows.value.filter((r) => r.count > 0) : rows.value,
)

const rated = computed(() => rows.value.filter((r) => r.count > 0))

/** 全站聚合：把每位主播的分布加起来重算，不是对均值再求平均。 */
const site = computed(() => {
  const dist: Record<number, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }
  let count = 0
  let total = 0
  for (const r of rows.value) {
    for (const s of SCORES) {
      const n = r.distribution[String(s)] ?? 0
      dist[s] += n
      count += n
      total += n * s
    }
  }
  return {
    count,
    average: count ? total / count : null,
    distribution: dist,
    low: dist[1] + dist[2],
  }
})

async function load() {
  loading.value = true
  try {
    const [overview, talents] = await Promise.all([
      surveyAdminApi.overview(),
      talentAdminApi.list(),
    ])
    rows.value = talents.map((t: TalentAdminView) => {
      const agg = overview[String(t.id)]
      return {
        talentId: t.id,
        nickname: t.nickname,
        listed: t.listed,
        count: agg?.count ?? 0,
        average: agg?.average ?? null,
        distribution: agg?.distribution ?? {},
      }
    })
    // 低分在前：这一页存在的意义是尽早发现服务出问题的主播
    rows.value.sort((a, b) => {
      if (a.count === 0 && b.count === 0) return a.talentId - b.talentId
      if (a.count === 0) return 1
      if (b.count === 0) return -1
      return (a.average ?? 5) - (b.average ?? 5)
    })
  } catch (e) {
    rows.value = []
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openDetail(row: Row) {
  Object.assign(detail, {
    visible: true,
    talentId: row.talentId,
    nickname: row.nickname,
    loading: true,
    aggregate: null,
    entries: [],
  })
  try {
    const result = await surveyAdminApi.talentDetail(row.talentId)
    detail.aggregate = result.aggregate
    detail.entries = [...result.details].sort(
      (a, b) => b.createdAt.localeCompare(a.createdAt) || b.orderId - a.orderId,
    )
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    detail.loading = false
  }
}

/**
 * 复制订单号。
 *
 * navigator.clipboard 在不少环境下直接被拒（权限、非安全上下文、页面没焦点），
 * 实测在本机的内嵌浏览器里就会抛「Read permission denied」。
 * 所以失败后退回 execCommand('copy')——它老，但在这些场景里反而能用。
 * 两条都不行才提示运营自己选中，而不是让按钮看起来像坏的。
 */
async function copyRef(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('订单号已复制，去「订单管理」粘贴查找')
    return
  } catch {
    /* 退回下面的老办法 */
  }

  const ta = document.createElement('textarea')
  ta.value = text
  // 放在视口外，但不能用 display:none——那样选不中
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  ta.style.left = '-9999px'
  document.body.appendChild(ta)
  ta.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  ta.remove()

  if (ok) {
    ElMessage.success('订单号已复制，去「订单管理」粘贴查找')
  } else {
    ElMessage.warning('这个浏览器不让自动复制，请手动选中订单号')
  }
}

// ---------- 展示 ----------

function asRow(row: unknown): Row {
  return row as Row
}

function asEntry(row: unknown): SurveyDetailEntry {
  return row as SurveyDetailEntry
}

function when(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}

function averageText(average: number | null) {
  return average === null ? '—' : average.toFixed(2)
}

function isLow(average: number | null) {
  return average !== null && average < LOW_AVERAGE
}

function barWidth(row: Row, score: number) {
  if (!row.count) return '0%'
  return `${((row.distribution[String(score)] ?? 0) / row.count) * 100}%`
}

function scoreType(score: number) {
  if (score <= 2) return 'danger'
  if (score === 3) return 'warning'
  return 'success'
}
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="head">
      <h2>满意度回访</h2>
      <el-checkbox v-model="onlyRated">只看有评分的</el-checkbox>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>评分不在粉丝端公开</template>
      粉丝在订单完成后给 1 到 5 分，<strong>每单只能评一次，评完不能改</strong>。
      分数<strong>不会展示在主播详情页</strong>，其他粉丝看不到；主播本人能在工作台
      看到自己的评分和均值；运营在这里看全部。
      这一页按均值从低到高排，低分排在最前——它的用处是尽早发现服务出了问题的主播。
    </el-alert>

    <div class="tiles">
      <el-card shadow="never" class="tile">
        <div class="tile-label">全站均值</div>
        <div class="tile-value" :class="{ low: isLow(site.average) }">
          {{ averageText(site.average) }}
        </div>
        <div class="tile-sub">共 {{ site.count }} 条评分</div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">低分（1–2 分）</div>
        <div class="tile-value" :class="{ low: site.low > 0 }">{{ site.low }}</div>
        <div class="tile-sub">
          <template v-if="site.low">逐条看看发生了什么</template>
          <template v-else>暂时没有低分</template>
        </div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">有评分的主播</div>
        <div class="tile-value">
          {{ rated.length }} <span class="tile-unit">/ {{ rows.length }}</span>
        </div>
        <div class="tile-sub">其余还没有人评过</div>
      </el-card>
      <el-card shadow="never" class="tile">
        <div class="tile-label">全站分布</div>
        <div class="dist">
          <div v-for="s in SCORES" :key="s" class="dist-line">
            <span class="dist-score">{{ s }} 分</span>
            <span class="dist-bar">
              <i
                :class="`s${s}`"
                :style="{
                  width: site.count
                    ? `${(site.distribution[s] / site.count) * 100}%`
                    : '0%',
                }"
              />
            </span>
            <span class="dist-num">{{ site.distribution[s] }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-empty v-if="!loading && !shown.length" description="还没有任何评分" />
      <el-table v-else :data="shown" row-key="talentId">
        <el-table-column label="主播" min-width="150">
          <template #default="{ row }">
            <div>
              {{ asRow(row).nickname }}
              <el-tag v-if="!asRow(row).listed" type="info" size="small">未上架</el-tag>
            </div>
            <div class="sub">id {{ asRow(row).talentId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="均值" width="110">
          <template #default="{ row }">
            <strong :class="{ low: isLow(asRow(row).average) }">
              {{ averageText(asRow(row).average) }}
            </strong>
          </template>
        </el-table-column>
        <el-table-column label="评分数" width="90">
          <template #default="{ row }">
            <span :class="{ sub: !asRow(row).count }">{{ asRow(row).count }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分布" min-width="230">
          <template #default="{ row }">
            <div v-if="asRow(row).count" class="dist">
              <div v-for="s in SCORES" :key="s" class="dist-line">
                <span class="dist-score">{{ s }}</span>
                <span class="dist-bar">
                  <i :class="`s${s}`" :style="{ width: barWidth(asRow(row), s) }" />
                </span>
                <span class="dist-num">{{ asRow(row).distribution[String(s)] ?? 0 }}</span>
              </div>
            </div>
            <span v-else class="sub">暂无评分</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!asRow(row).count"
              @click="openDetail(asRow(row))"
            >
              逐条看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 逐条明细 ---------- -->
    <el-drawer
      v-model="detail.visible"
      :title="`${detail.nickname} 的评分`"
      size="680px"
    >
      <el-descriptions v-if="detail.aggregate" :column="2" border size="small"
        style="margin-bottom: 16px">
        <el-descriptions-item label="均值">
          <strong :class="{ low: isLow(detail.aggregate.average) }">
            {{ averageText(detail.aggregate.average) }}
          </strong>
        </el-descriptions-item>
        <el-descriptions-item label="评分数">{{ detail.aggregate.count }}</el-descriptions-item>
      </el-descriptions>

      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        粉丝只给出 id，没有昵称。要查是哪一单出的问题，
        <strong>复制订单号到「订单管理」搜索</strong>，在那边能看到完整轨迹和联系方式。
      </el-alert>

      <el-empty v-if="!detail.loading && !detail.entries.length" description="没有评分记录" />
      <el-table v-else v-loading="detail.loading" :data="detail.entries" row-key="orderId">
        <el-table-column label="评分" width="90">
          <template #default="{ row }">
            <el-tag :type="scoreType(asEntry(row).score)" size="small">
              {{ asEntry(row).score }} 分
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单号" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ asEntry(row).orderRef }}</span>
            <el-button link type="primary" size="small" @click="copyRef(asEntry(row).orderRef)">
              复制
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="粉丝" width="110">
          <template #default="{ row }">
            <span class="sub">id {{ asEntry(row).fanUserId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分时间" min-width="140">
          <template #default="{ row }">
            <span class="sub">{{ when(asEntry(row).createdAt) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head h2 {
  margin: 0;
  font-size: 18px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.tile-label {
  font-size: 13px;
  color: #909399;
}
.tile-value {
  font-size: 24px;
  font-weight: 600;
  margin: 6px 0 4px;
  font-variant-numeric: tabular-nums;
}
.tile-unit {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
}
.tile-sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.low {
  color: #f56c6c;
}
.sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.dist {
  margin-top: 6px;
}
.dist-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.dist-score {
  width: 28px;
  flex: none;
  text-align: right;
}
.dist-bar {
  flex: 1;
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}
.dist-bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
}
/* 高分绿、中间黄、低分红：扫一眼就知道一位主播的口碑形状 */
.dist-bar i.s5,
.dist-bar i.s4 {
  background: #67c23a;
}
.dist-bar i.s3 {
  background: #e6a23c;
}
.dist-bar i.s2,
.dist-bar i.s1 {
  background: #f56c6c;
}
.dist-num {
  width: 24px;
  flex: none;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1100px) {
  .tiles {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
